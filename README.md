# Development Notes

## Backend (Clojure)

- Start the API server with `clj -M:backend [port]`. If no port is supplied it defaults to `8081`, or you can set the `PORT` env var.
- Endpoints provided:
  - `GET /api/health` – simple readiness probe.
  - `GET /api/users` – list users from Postgres.
  - `POST /api/users` – create a user record; accepts JSON `{uuid?, name, age}`.
  - `PUT/PATCH /api/users/:uuid` – update an existing user.
  - `DELETE /api/users/:uuid` – remove a user.
  - `/api/todo` – Toucan2-backed CRUD endpoints; see `docs/todo-api.md` for payload details.
- Database access uses `next.jdbc` for the user endpoints and Toucan2 ORM for todos. Configure the `DATABASE_URL` environment variable or update `acme.server.db/default-database-url`.

To stop the server in a REPL/dev session call `(acme.server.core/stop!)`.

## Frontend (shadow-cljs)

- Launch the cljs build with `npx shadow-cljs watch web`.
- The dev HTTP server at `http://localhost:8080` serves `public/index.html` and forwards `/api/*` calls to the backend via the proxy predicate in `shadow-cljs.edn`.

With both processes running you can interact with the UI locally without relying on the previously proxied external host.
