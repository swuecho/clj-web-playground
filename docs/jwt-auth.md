# JWT Authentication & User Table

The backend now protects every `/api/todo` and `/api/users` endpoint with bearer tokens. Tokens are short-lived JWTs signed with an HMAC secret and carry the user UUID/email/role in their claims. Use the `/api/auth/login` endpoint to obtain a token, then supply it on subsequent requests with the `Authorization: Bearer <token>` header.

## Database changes

`"UserTable"` now stores login credentials in addition to the demo profile fields:

| Column          | Type           | Notes                                         |
| --------------- | -------------- | --------------------------------------------- |
| `uuid`          | `uuid`         | Primary key (supplied or generated).          |
| `name`          | `varchar(255)` | Required.                                     |
| `age`           | `integer`      | Required, must be ≥ 0.                        |
| `email`         | `varchar(255)` | Required, stored in lowercase, unique.        |
| `password_hash` | `text`         | Required, BCrypt hash created by the API.     |
| `role`          | `text`         | Required, either `user` (default) or `admin`. |
| `created_at`    | `timestamptz`  | Optional convenience timestamp.               |
| `updated_at`    | `timestamptz`  | Optional convenience timestamp.               |

If you already had the table in place, apply a migration similar to:

```sql
alter table "UserTable" add column if not exists email varchar(255);
alter table "UserTable" add column if not exists password_hash text;
alter table "UserTable" add column if not exists role text not null default 'user';
-- Optional but recommended
alter table "UserTable" add column if not exists created_at timestamptz not null default now();
alter table "UserTable" add column if not exists updated_at timestamptz not null default now();

create unique index if not exists usertable_email_key on "UserTable" (lower(email));
```

After adding the columns, backfill every row with a unique email and hashed password, then add `NOT NULL` constraints:

```sql
update "UserTable"
set email = lower(uuid::text) || '@example.local'
where email is null;

update "UserTable"
set role = 'user'
where role is null;
```

Generate password hashes with the same helper the API uses:

```clojure
(require '[acme.server.auth :as auth]
         '[acme.server.db :as db])

(db/query ["update \"UserTable\" set password_hash = ? where uuid = ?"
           (auth/hash-password "temporary-password")
           "<user-uuid>"])
```

When every row has both fields populated, lock them down:

```sql
alter table "UserTable"
  alter column email set not null,
  alter column password_hash set not null,
  alter column role set not null;

-- Promote at least one administrator so someone can manage users/todos.
update "UserTable" set role = 'admin' where email = 'demo@example.com';
```

```sql
alter table todo_items add column user_id uuid;
```

### Refresh Tokens

Access tokens remain short lived (1 hour by default) and are now paired with revocable refresh
tokens. Each refresh token is persisted in a dedicated `"RefreshToken"` table and is delivered to
clients via an HttpOnly cookie named `acme-refresh` (the raw value is never exposed to
application/JavaScript code):

```sql
create table if not exists "RefreshToken" (
  id uuid primary key,
  user_uuid uuid not null references "UserTable"(uuid) on delete cascade,
  token_hash text not null,
  created_at timestamptz not null default now(),
  last_used_at timestamptz,
  expires_at timestamptz not null,
  revoked_at timestamptz
);

create index if not exists refresh_token_user_idx on "RefreshToken" (user_uuid);
```

Tokens are stored as BCrypt hashes; the API only returns the raw token once (inside the cookie). Use
`ACME_REFRESH_TTL_SECONDS` (or `ACME_REFRESH_TTL_DAYS`, defaults to 30 days) to adjust their
lifetime. Cookie behavior can be tuned with `ACME_REFRESH_COOKIE_SECURE` (force the `Secure`
attribute in HTTPS environments) and `ACME_REFRESH_COOKIE_SAMESITE` (no attribute by default; set to
`strict`, `lax`, or `none` as needed).

### Refresh flow

1. **Login and capture the cookie**

   ```bash
   curl -X POST http://localhost:8082/api/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"email":"demo@example.com","password":"correct horse"}' \
     -c cookies.txt
   ```

   The JSON response only contains the short-lived access token. The refresh token is stored in
   `cookies.txt` as `acme-refresh`.

2. **Exchange the cookie for a new access token**

   ```bash
   curl -X POST http://localhost:8082/api/auth/refresh \
     -b cookies.txt -c cookies.txt
   ```

   The backend rotates the refresh token, sets a new cookie, and returns a fresh access token. If
   the cookie is missing, expired, or revoked the client receives a 401 and should send the user to
   the login screen. Calling `POST /api/auth/logout` similarly revokes the cookie and prevents future
   refreshes until the user signs back in.

Administrators can review and revoke refresh tokens from **Users → Sessions** in the UI. Behind the
scenes the following endpoints are available:

- `GET /api/users/:uuid/refresh-tokens` — list tokens for a user (admin only)
- `DELETE /api/users/:uuid/refresh-tokens/:token-id` — revoke a token (admin only)

## Environment variables

- `ACME_JWT_SECRET` — **required in production**. Defaults to a dev-only secret, so set this for any shared environment.
- `ACME_JWT_TTL_SECONDS` / `ACME_JWT_TTL_MINUTES` — optionally override the 1-hour token lifetime.
- `ACME_REFRESH_TTL_SECONDS` / `ACME_REFRESH_TTL_DAYS` — override refresh token lifetime (defaults to 30 days).
- `ACME_REFRESH_COOKIE_SECURE` — set to `1`/`true` when the app is served over HTTPS so the cookie gains the `Secure` attribute.
- `ACME_REFRESH_COOKIE_SAMESITE` — override the SameSite attribute (`strict`, `lax`, or `none`); defaults to `lax`.

## API usage

1. Authenticate (capture cookies if you plan to refresh via curl):

```bash
curl -X POST http://localhost:8082/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"correct horse"}' \
  -c cookies.txt
```

Response:

```json
{
  "access_token": "<jwt>",
  "token_type": "Bearer",
  "expires_at": "2024-06-01T18:32:11Z",
  "expires_in": 3600,
  "user": {
    "uuid": "…",
    "name": "Demo",
    "email": "demo@example.com",
    "age": 42,
    "role": "user"
  }
}
```

2. Call protected endpoints by passing the token:

```bash
curl http://localhost:8082/api/users \
  -H 'Authorization: Bearer <jwt>'
```

When the access token expires the frontend calls `/api/auth/refresh` with `withCredentials=true` so
the HttpOnly cookie is attached. `/api/auth/logout` revokes the refresh cookie on the server and the
client clears any cached access tokens, forcing the next login.
