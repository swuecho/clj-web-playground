# Repository Guidelines

## Project Structure & Module Organization
- Backend Clojure code lives in `src/main/acme/server`, grouped by concern (`handlers`, `services`, `schemas`, and `middleware`) with `acme.server.core` as the entry point and `src/dev/repl.clj` providing Integrant-driven reload helpers.
- Frontend ClojureScript modules sit under `src/main/acme/web`, mirroring the re-frame data flow (`db.cljs`, `events.cljs`, `subs.cljs`, `views.cljs`, `components.cljs`, `app.cljs`), while shared utility code belongs in `acme.web.util`.
- Development tooling and build hooks reside in `src/dev`, shared styling sources in `src/styles`, and compiled/public assets in `public` (`index.html`, `css/`, `js/manifest.edn`).
- Reference docs and deep dives are stored in `docs/`; add new topic-specific guides here.

## Build, Test, and Development Commands
- `npm run backend` – start the Integrant-backed API server (defaults to port 8081, honors `PORT`).
- `npm run frontend` – launch `shadow-cljs watch web` with the Tailwind/Sass build hook.
- `npm run dev` – run backend and frontend watchers together for a full-stack dev loop.
- `npm run build` – build production-ready Sass, Tailwind output, and the optimized cljs bundle.
- `npm run repl` – open an nREPL with CIDER middleware; evaluate `(repl/start)` to bring up the system.

## Coding Style & Naming Conventions
- Prefer idiomatic Clojure formatting (two-space indentation, aligned maps) and keep namespaces kebab-cased under the `acme.*` hierarchy.
- In cljs files, follow re-frame conventions: event ids and subs as keywords, React components in PascalCase, utility fns in kebab-case.
- Run `sass:build` and `tailwind:build` before committing CSS changes to keep checked-in assets up to date.

## Testing Guidelines
- Place automated tests under `src/test` following the same namespace pattern as production code (`acme.<domain>.test-*`); favor focused integrant/component tests for servers and reagent/re-frame view tests for the UI.
- Until a dedicated test alias is added, exercise tests via the REPL (`npm run repl`) or targeted `clj -X` invocations inside your test namespaces.
- For backend additions, provide Malli schemas and handler-level tests that cover expected validation failures and success paths; mirror API behavior using `docs/todo-api.md` payloads.

## Commit & Pull Request Guidelines
- Base your commit messages on the existing history: short imperative subjects (`Refactor user handling`, `Add user schema…`) with optional descriptive bodies.
- Squash WIP commits before opening a PR and ensure each change stands on its own with passing builds.
- PRs should describe the change, link related issues, call out schema/API updates, and include screenshots or curl snippets for UI/API tweaks.

## Security & Configuration Tips
- Store secrets via environment variables: `DATABASE_URL` for Postgres, `ACME_DISABLE_RELOAD=true` when disabling dev hot-reload, and project-specific ports as needed.
- Never commit `.env` files or database credentials; document required vars in `docs/` if clarification is needed.
