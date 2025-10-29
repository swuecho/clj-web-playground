# Development Notes

## Backend (Clojure)

- Start the API server with `clj -M:backend [port]`. If no port is supplied it defaults to `8081`, or you can set the `PORT` env var.
- Endpoints provided:
  - `GET /api/health` – simple readiness probe.
  - `GET /api/users` – returns the current in-memory user collection.
  - `POST /api/users` – accepts JSON `{uuid?, name, age}` and appends a new user. A `uuid` is generated when omitted.
- The data lives in-memory for quick iteration; restarting the process resets it to a few sample users.

To stop the server in a REPL/dev session call `(acme.server.core/stop!)`.

## Frontend (shadow-cljs)

- Launch the cljs build with `npx shadow-cljs watch web`.
- The dev HTTP server at `http://localhost:8080` serves `public/index.html` and forwards `/api/*` calls to the backend via the proxy predicate in `shadow-cljs.edn`.

With both processes running you can interact with the UI locally without relying on the previously proxied external host.
