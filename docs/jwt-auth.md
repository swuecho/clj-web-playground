# JWT Authentication & User Table

The backend now protects every `/api/todo` and `/api/users` endpoint with bearer tokens. Tokens are short-lived JWTs signed with an HMAC secret and carry the user UUID/email in their claims. Use the `/api/auth/login` endpoint to obtain a token, then supply it on subsequent requests with the `Authorization: Bearer <token>` header.

## Database changes

`"UserTable"` now stores login credentials in addition to the demo profile fields:

| Column         | Type                | Notes                                  |
|----------------|---------------------|----------------------------------------|
| `uuid`         | `uuid`              | Primary key (supplied or generated).    |
| `name`         | `varchar(255)`      | Required.                              |
| `age`          | `integer`           | Required, must be ≥ 0.                 |
| `email`        | `varchar(255)`      | Required, stored in lowercase, unique. |
| `password_hash`| `text`              | Required, BCrypt hash created by the API. |
| `created_at`   | `timestamptz`       | Optional convenience timestamp.        |
| `updated_at`   | `timestamptz`       | Optional convenience timestamp.        |

If you already had the table in place, apply a migration similar to:

```sql
alter table "UserTable" add column if not exists email varchar(255);
alter table "UserTable" add column if not exists password_hash text;
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
  alter column password_hash set not null;
```

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
  "user": {"uuid": "…", "name": "Demo", "email": "demo@example.com", "age": 42}
}
```

2. Call protected endpoints by passing the token:

```bash
curl http://localhost:8081/api/users \
  -H 'Authorization: Bearer <jwt>'
```

The frontend login form now talks to `/api/auth/login`; once a token is stored, every todo/user request automatically includes the bearer header. A 401 from the server clears the client session and prompts the user to sign back in.
