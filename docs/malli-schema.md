# Malli Schema Overview

This backend leans on [Malli](https://github.com/metosin/malli) for declarative request/response validation, OpenAPI shape generation, and consistent error messaging.

## Dependency and Router Integration
- `metosin/malli` is pulled in directly as a top-level dependency so schemas are available to both the HTTP stack and any REPL/test tooling (`deps.edn`).
- The Reitit router is configured with `reitit.coercion.malli/create`, which means every `:parameters` and `:responses` entry in `acme.server.core` is validated automatically before handlers execute (`src/main/acme/server/core.clj`).
- The `ring-coercion` middlewares registered alongside the router glue Malli into the request lifecycle: request payloads are coerced, handler return values are checked, and coercion failures are rendered as structured errors without touching handler code.
- Because the router also wires up `reitit.openapi`, the same Malli definitions are turned into the `/openapi.json` description, keeping docs, validation, and runtime behavior in sync.

## Schema Namespaces
All production schemas live under `acme.server.schemas.*`, grouped by domain. Each namespace exposes Malli data structures that are imported by the router and by other schemas when reuse is needed.

### `acme.server.schemas.auth`
- Provides request bodies for login and registration plus token/refresh/logout response shapes.
- Reuses `acme.server.schemas.user` email, password, and user response definitions to guarantee auth endpoints stay aligned with user data constraints.

### `acme.server.schemas.todo`
- Defines `id-path`, `create-body`, and `update-body` to validate todo CRUD request payloads and path params.
- Supplies a `todo-response` map schema (with optional timestamps) and a sequential collection schema for list endpoints.

### `acme.server.schemas.user`
- Owns reusable primitives such as `email-schema`, `password-schema`, `role-schema`, and `uuid-path`.
- Delegates complex validation logic to `acme.server.schemas.validation.users` helpers (normalization, regex checks, min-length enforcement) by embedding them in `:fn` predicates, so invariants live in one place while Malli handles error messaging.
- Exports create/update request bodies and both single/multi user response schemas for the admin endpoints.

### `acme.server.schemas.refresh-token`
- Centralizes refresh-token UUID validation (`token-id-schema`) for both path parameters and response payloads.
- Models the list shape with `[:sequential refresh-token-response]`, which Reitit can use for automatic coercion and docs.

## Common Patterns
- **Non-blank strings & UUIDs:** Each schema namespace defines its own `non-blank-string` and UUID regex helper for clarity, then composes them with `[:and ... [:fn {...} predicate]]` to attach user-friendly error messages.
- **Path vs. body separation:** Path segments are wrapped in small `[:map ...]` schemas (for example, todo `id-path` and user `uuid-path`) so handlers get already-coerced values when Reitit binds route parameters.
- **Optional fields:** Optional request keys consistently use `{:optional true}` so Malli accepts partial updates while still checking provided values.
- **Closed vs. open maps:** Response schemas specify `{:closed true}` when extra keys should be rejected (e.g., refresh tokens, users) and leave it off when backfill fields may be added incrementally (e.g., todos) to strike a balance between future flexibility and tight contracts for security-sensitive payloads.

## Extending the Schema Layer
When adding a new endpoint or data shape:
1. Create or update the appropriate namespace under `src/main/acme/server/schemas` and define Malli data structures close to the domain logic.
2. Reuse existing primitives (email/password/UUID schemas, `non-blank-string`, etc.) before introducing new predicates; add helpers to `acme.server.schemas.validation.*` if the rule is shared.
3. Reference the new schema in `acme.server.core` within both `:parameters` and `:responses` so Reitit can enforce it at the router boundary and publish accurate OpenAPI docs.
4. If the payload is exposed to clients, remember to keep corresponding examples/descriptions in `docs/todo-api.md` or other reference files aligned with the Malli source of truth.

Following this pattern keeps validation logic declarative, promotes reuse, and ensures API documentation, runtime coercion, and handler implementations never drift.

## Using Malli Validation
1. **Require the shared helpers.** Pull in `acme.server.schemas.validation.common` (and `...validation.users` when email/password helpers are needed) inside your schema namespace: `(:require [acme.server.schemas.validation.common :as v])`.
2. **Compose schemas from primitives.** Build request/response shapes with the shared predicates to guarantee consistent error messaging:
   ```clojure
   (def project-body
     [:map
      [:name v/non-blank-string]
      [:owner_id v/uuid-string]])
   ```
3. **Expose the schema via your routes.** In `acme.server.core`, reference the schema in the appropriate route entry—`{:parameters {:body project.schema/project-body}}` for requests, `{:responses {201 {:body project.schema/project-response}}}` for replies. Reitit will run Malli-based coercion before/after the handler, returning structured 400 errors when validation fails.
4. **Describe path/query params explicitly.** Path pieces and query strings need their own `[:map ...]` definitions so Malli can coerce them prior to handler execution (e.g., `[:parameters {:path project.schema/id-path}]`).
5. **Iterate at the REPL.** Because schemas are just data, you can evaluate them directly and use Malli's `m/explain` / `m/validate` helpers while developing to preview validation errors before wiring the schema into the router.

### Writing Rules Effectively
- **Start from a base type.** Every predicate chains off a concrete type (`:string`, `:int`, `:map`). Use `:and` to layer constraints: `[:and :string [:fn {:error/message "..."} predicate]]`.
- **Attach friendly errors.** Pass `{:error/message ...}` metadata to `:fn` or constraint entries so callers get actionable feedback (see `v/non-blank-string`).
- **Share predicates for reuse.** Common checks (UUIDs, emails, password length) belong in `acme.server.schemas.validation.*` and are pulled into schemas with aliases like `:as v` to avoid copy/paste.
- **Control map openness.** Add `{:closed true}` to map schemas when you want Malli to reject unknown keys (used for user and refresh token responses). Leave it off for payloads that may evolve (todos).
- **Model collections explicitly.** Wrap vectors/lists using `[:sequential child-schema]` or `[:vector child-schema]` so coercion understands nested structures—for example, `todo-list-response`.
- **Parameterize numbers and enums.** Numeric constraints go inside the type metadata (`[:int {:min 0 :max 100}]`), while limited string sets should use `[:enum "draft" "published"]`.
- **Combine optional + maybe.** Use `{:optional true}` for keys that may be absent and `[:maybe ...]` for nullable values when the key is present but can be `nil` (refresh token timestamps).
- **Leverage helper functions.** When validation requires more than a pure predicate, write a helper in `schemas.validation.*` (e.g., `user.validation/valid-email?`) and reference it inside the `:fn` clause to keep side effects out of schemas.
