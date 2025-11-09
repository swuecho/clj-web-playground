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
tokens. Each refresh token is persisted in a dedicated `"RefreshToken"` table:

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

Tokens are stored as BCrypt hashes; the API only returns the raw token once. Use the
`ACME_REFRESH_TTL_SECONDS` (or `ACME_REFRESH_TTL_DAYS`, defaults to 30 days) environment variable to
adjust their lifetime.

### Refresh flow

The login endpoint now returns both tokens:

```json
{
  "access_token": "<jwt>",
  "expires_at": "2024-06-01T18:32:11Z",
  "refresh_token": "<token-id>.<secret>",
  "refresh_expires_at": "2024-07-01T18:32:11Z",
  "user": { ... }
}
```

Exchange a refresh token for a new session via `POST /api/auth/refresh`:

```bash
curl -X POST http://localhost:8081/api/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refresh_token":"<token-id>.<secret>"}'
```

The frontend automatically attempts this exchange whenever the access token expires. If the refresh
token is missing, expired, or revoked the user is signed out.

Administrators can review and revoke refresh tokens from **Users → Sessions** in the UI. Behind the
scenes the following endpoints are available:

- `GET /api/users/:uuid/refresh-tokens` — list tokens for a user (admin only)
- `DELETE /api/users/:uuid/refresh-tokens/:token-id` — revoke a token (admin only)

## Environment variables

- `ACME_JWT_SECRET` — **required in production**. Defaults to a dev-only secret, so set this for any shared environment.
- `ACME_JWT_TTL_SECONDS` / `ACME_JWT_TTL_MINUTES` — optionally override the 1-hour token lifetime.

## API usage

1. Authenticate:

```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"correct horse"}'
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
curl http://localhost:8081/api/users \
  -H 'Authorization: Bearer <jwt>'
```

The frontend login form now talks to `/api/auth/login`; once a token is stored, every todo/user request automatically includes the bearer header. A 401 from the server clears the client session and prompts the user to sign back in.
