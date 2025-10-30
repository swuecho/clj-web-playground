# Development Notes

## Backend (Clojure)

- Start the API server with `npm run backend -- [port]`. If no port is supplied it defaults to `8081`, or you can set the `PORT` env var. (This wraps `clj -M:backend`, which you can still invoke directly.)
- Use `npm run repl` (alias for `clj -M:repl`) to start an nREPL server with CIDER middleware preloaded.
- Endpoints provided:
  - `GET /api/health` – simple readiness probe.
  - `GET /api/users` – list users from Postgres.
  - `POST /api/users` – create a user record; accepts JSON `{uuid?, name, age}`.
  - `PUT/PATCH /api/users/:uuid` – update an existing user.
  - `DELETE /api/users/:uuid` – remove a user.
  - `/api/todo` – Toucan2-backed CRUD endpoints; see `docs/todo-api.md` for payload details.
- Database access uses `next.jdbc` for the user endpoints and Toucan2 ORM for todos. Configure the `DATABASE_URL` environment variable or update `acme.server.db/default-database-url`.
- File changes under `src/main` or `src/dev` are picked up automatically via Ring's `wrap-reload`; set `ACME_DISABLE_RELOAD=true` before starting the server to turn this off (e.g. in production).
- Request and response payloads for the todo API are validated with Malli and Reitit coercion, so malformed payloads return structured 400/422 JSON errors automatically.

To manage the system from a REPL load `repl` (the dev helper namespace) and call `(repl/start)`, `(repl/stop)`, or `(repl/reset)`. Use `(repl/configure! {:port 9001})` to override the defaults (e.g. run Jetty on another port). When connecting from an editor, point it at the nREPL that `npm run repl` exposes.

## Frontend (shadow-cljs)

- Launch the cljs build with `npm run frontend`. The command runs `shadow-cljs watch web` and triggers the Sass/Tailwind build hook.
- Use `npm run dev` to start the backend and frontend watchers together.
- Build for release with `npm run build`, which compiles Sass, Tailwind, and the production cljs bundle.
- The dev HTTP server at `http://localhost:8080` serves `public/index.html` and forwards `/api/*` calls to the backend via the proxy predicate in `shadow-cljs.edn`.

With both processes running you can interact with the UI locally without relying on the previously proxied external host.
