
# Command Cheatsheet

Toucan2 integration required a few ad-hoc `clj` invocations. They are captured here with a short note so we can replay or adapt them later.

## Discovering the dependency

```
clojure -X:deps find-versions :lib io.github.camsaul/toucan2 :sort :version
```
- Lists published versions from Maven to confirm the coordinates before updating `deps.edn`.

## Inspecting source during investigation

```
clojure -Sdeps '{:deps {io.github.camsaul/toucan2 {:mvn/version "1.0.568"}}}' \
  -e "(println (slurp (clojure.java.io/resource \"toucan2/jdbc/options.clj\")))"
```
- Temporarily pulls Toucan2 onto the classpath and prints the chosen file so we can review implementation details without editing our project deps.

## Exploring Toucan2 APIs

```
clojure -Sdeps '{:deps {io.github.camsaul/toucan2 {:mvn/version "1.0.568"}}}' \
  -e "(require '[toucan2.connection :as conn]) (println (keys (ns-publics 'toucan2.connection)))"
```
- Lists the public var names in the connection namespace to reveal transaction helpers and connection utilities at a glance.

```
clojure -Sdeps '{:deps {io.github.camsaul/toucan2 {:mvn/version "1.0.568"}}}' \
  -e "(require '[clojure.repl :refer [doc]] '[toucan2.core :as t2]) (doc t2/query)"
```
- Opens the docstring for a specific function via `clojure.repl/doc`, handy when double-checking argument order.

```
clojure -Sdeps '{:deps {io.github.camsaul/toucan2 {:mvn/version "1.0.568"}}}' \
  -e "(require '[clojure.pprint :as pprint] '[toucan2.core :as t2]) (pprint/pprint (keys (ns-publics 'toucan2.core)))"
```
- Pretty prints the exported API from `toucan2.core` to see the available CRUD helpers before diving into the source.

## Verifying namespace loading

```
clojure -M -e "(require 'acme.server.db 'acme.server.handlers.users 'acme.server.handlers.health)
               (println :ok)"
```
- Quick smoke check to ensure the refactored namespaces compile after wiring Toucan2.
- Good to run after touching server handlers so we catch missing requires before deploying.


## query db 

clojure -M -e "(require 'acme.server.db 'acme.server.models.todo) (def first-row (first (acme.server.models.todo/all))) (prn (type first-row)) (prn (into {} (seq first-row)))"

## Integrant REPL helpers

```
npm run repl
```
- Starts `clj -M:repl`, launching an nREPL server with CIDER middleware preloaded. Connect your editor client to the port printed in the terminal.

```
(require 'repl)
(repl/start)  ; boots the Integrant system
(repl/reset)  ; reloads modified namespaces and restarts Jetty
(repl/configure! {:port 9090}) ; override Jetty port before the next start/reset
```
