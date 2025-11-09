# Clojure Concepts in This Project

This guide surveys the language and library concepts that show up across the Acme backend (`src/main/acme/server`) and the re-frame frontend (`src/main/acme/web`). Use it as a quick refresher on how idiomatic Clojure manifests inside this repo.

## Namespaces, Aliases, and Requires
- Every source file begins with an `ns` form that declares its namespace and required dependencies. Example: `acme.server.core` aliases middleware and schema namespaces while exposing Jetty entry points (`src/main/acme/server/core.clj`).
- Aliases (e.g. `[acme.server.http :as http]`) keep call sites short and clarify ownership.
- Backend and frontend namespaces mirror the directory structure, which is why `src/main/acme/web/feature/auth/events.cljs` becomes `acme.web.feature.auth.events`.

## Data-First Design & Immutability
- Route tables, Integrant configs, and UI layouts are plain immutable maps/vectors. For instance, `acme.server.core/routes` is a vector of vectors describing HTTP endpoints, coercion metadata, and handlers.
- ClojureScript components like `acme.web.views/workspace-layout` return hiccup data (vectors/lists) that Reagent turns into React elements.

## State Management Primitives
- **Atoms (`atom`, `defonce`)** hold mutable state such as `system*` in `acme.server.core` and `root*` in `acme.web.app`. State changes go through `swap!`/`reset!` to preserve atomicity.
- **Delay** (`delay`/`@`) lazily initializes expensive resources like the default JDBC datasource (`acme.server.db/default-datasource`).
- **Dynamic vars** (`^:dynamic *current-connection*`) allow temporary rebinding inside macros like `with-transaction`.

## Macros and Higher-Order Helpers
- `acme.server.db/with-transaction` is a macro that wraps bodies in `jdbc/with-transaction` and binds the dynamic connection.
- Reagent’s `with-let` macro (used in `acme.web.views/workspace-shell`) enables Form-2 components that capture local state/subscriptions once and return a render function.

## Multimethods & Protocol Extensions
- The repo customizes behavior via multimethods: `methodical.core`’s `m/defmethod` overrides Toucan2 protocol callbacks such as `t2/table-name` for the `::todo` model (`src/main/acme/server/models/todo.clj`).
- Integrant lifecycle handling uses `defmethod ig/init-key` / `ig/halt-key!` to start Jetty and teardown resources (`acme.server.core`).

## Functional Composition & Threading
- Handler assembly relies on the thread-first macro (`->`) to wrap Ring handlers with middleware (`wrap-cors`, `wrap-cookies`, `wrap-request-logging`).
- Business logic functions remain pure: `acme.server.services.todo/normalize-instance` takes a row and returns a cleaned map without side effects.

## Schema & Validation (Malli)
- Request/response contracts live in `acme.server.schemas.*` namespaces using Malli. Example: `todo.schema/create-body` enforces required keys and optional booleans.
- Schemas plug into Reitit’s coercion middleware so HTTP handlers can assume validated data.

## HTTP Routing & Middleware
- **Reitit Router:** `acme.server.core/router` builds a `ring/router` with nested data describing middleware, coercion, and OpenAPI docs.
- **Ring Middleware:** Both custom (e.g., `acme.server.middleware.auth/wrap-authentication`) and library-provided middleware intercept requests/responses.
- **Muuntaja:** Configured in `acme.server.http/muuntaja-instance` to negotiate JSON formats, referenced by the router.

## Database Access Patterns
- `next.jdbc` handles low-level SQL (`acme.server.db/query`), while Toucan2 provides higher-level CRUD helpers for todo models (`acme.server.models.todo`).
- Transactions use `db/with-transaction` to ensure consistent writes when inserting users or tokens.

## Authentication & Cryptography
- `buddy` libraries supply password hashing (`buddy.hashers`) and JWT signing/verification (`buddy.sign.jwt`), wired up in `acme.server.auth`.
- Refresh-token flows illustrate pure data transformations: token structs are maps with derived timestamps, serialized/deserialized via helper fns.

## Frontend re-frame Architecture
- **App DB (`acme.web.db/default-db`)**: a single immutable map representing UI state.
- **Events:** `re-frame.core/reg-event-fx` and `reg-event-db` handle mutations and side effects (e.g., `::auth-events/login`, `::todos-events/fetch-todos`).
- **Subscriptions:** `reg-sub` exposes derived data to components (e.g., `::auth-subs/is-logged-in?`, `::todo-subs/todo-pagination`).
- **Effects/Co-effects:** Custom fx like `::persist-auth` interact with `localStorage`, while `:http-xhrio` drives AJAX requests.

## Reagent Components & Hooks
- Components are pure functions returning hiccup. Form-2 components (function returning a function) are used when local state or subscriptions are needed (`acme.web.views/main-panel`).
- Utility namespaces (e.g., `acme.web.util/style->js`) demonstrate interop with JS and DOM APIs.

## Testing Patterns
- Backend tests rely on `clojure.test`, using `with-redefs` to stub DB calls (`src/test/acme/server/services/todo_test.clj`, `src/test/acme/server/handlers/users_test.clj`).
- Tests focus on pure functions (validation, normalization) so they run quickly without Postgres.

## Tooling & REPL
- `src/dev/repl.clj` wraps Integrant with helper functions (`start`, `stop`, `reset`) to support REPL-driven development.
- Shadow-cljs drives the CLJS build via `shadow-cljs.edn`, while `deps.edn` declares shared deps/aliases.

Keep this reference handy to onboard new contributors or as a checklist when extending functionality.
