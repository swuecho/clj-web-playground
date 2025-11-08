  Visualization Ideas

  - 1️⃣ Re-frame-10x: add the dev dependency and preload per the day8.re-frame-10x docs; running npm run frontend then opening the 10x panel gives
  you both the event timeline (showing who dispatches whom) and the live subscription graph so you can inspect the exact chain from ::todo-
  pagination back to its inputs.
  - 2️⃣ Runtime graph dump: in a dev-only namespace, query re-frame.registrar/kinds for :event and :sub, walk each handler’s interceptor effect
  map to collect :dispatch, :dispatch-n, and :dispatch-later targets, and emit an EDN adjacency list. Feed that into rhizome.dot/graph->dot or
  plain Graphviz (dot -Tpng) for a static diagram you can drop into docs/.
  - 3️⃣ Static analysis: run clj-kondo --analysis over src/main/acme/web/feature and post-process the :var-usages to connect rf/dispatch calls to
  the keyword literals they pass; combine that with a simple map of subscription :<- chains (already explicit in the reg-sub forms) to draw a
  two-layer graph (events vs. subs). This is reproducible in CI and can back a “deps.md” document.
