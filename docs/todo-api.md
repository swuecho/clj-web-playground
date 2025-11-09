# Todo API

The `/api/todo` endpoints exercise Toucan2's ORM-style helpers instead of raw SQL queries. They assume a PostgreSQL table named `todo_items` with these columns. All requests must include a valid bearer token (`Authorization: Bearer <jwt>`) obtained from `/api/auth/login`.

- Every todo records its owner via `user_id`.
- Non-admin accounts only see and mutate their own todos; admins retain a full-system view.

```sql
create table if not exists "todo_items" (
  id serial primary key,
  title text not null,
  completed boolean not null default false,
  user_id uuid not null references "UserTable"(uuid),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
```

```sql
INSERT INTO "todo_items" (title, completed, user_id, created_at, updated_at)
SELECT
    'Todo Item ' || generate_series(1, 100),
    (random() < 0.5),
    '<existing-user-uuid>'::uuid,
    NOW() - (random() * interval '365 days'),
    NOW() - (random() * interval '30 days')
FROM generate_series(1, 100);
```
Toucan2's `define-default-fields` config returns `[:id :title :completed :user_id :created_at :updated_at]`, so those keys appear in JSON responses.

## Endpoints

| Method | Path              | Description                              |
|--------|-------------------|------------------------------------------|
| GET    | `/api/todo`       | List todos ordered by `id` (scoped to the caller unless admin). |
| POST   | `/api/todo`       | Create a todo (`title` required).        |
| GET    | `/api/todo/:id`   | Fetch a single todo by numeric id.       |
| PUT    | `/api/todo/:id`   | Replace fields; accepts `title`, `completed`. |
| PATCH  | `/api/todo/:id`   | Same as `PUT`, allows partial updates.   |
| DELETE | `/api/todo/:id`   | Remove a todo; returns `{"status":"deleted"}` on success. |

### Request/response notes

- `completed` accepts booleans or truthy/falsy strings such as `"true"` / `"false"` in incoming JSON.
- `POST /api/todo` and the update endpoints trim whitespace in `title` and reject blank values.
- `POST` responds with HTTP 201 and the inserted row. Updates return the fresh row. Missing rows yield a 404.
- Requests run through Malli + Reitit coercion, so payloads that fail schema validation yield a descriptive 400 response (for example, missing `title`).
- Every response now includes the owning `user_id` so admins can audit who created which todo.

### Quick smoke check

After adding the table, verify that the server boots with:

```
clojure -M:backend
```

Then issue a test request, e.g.:

```
curl -X POST http://localhost:8082/api/todo \
     -H "Content-Type: application/json" \
     -d '{"title": "Buy milk"}'
```
