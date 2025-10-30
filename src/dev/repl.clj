(ns repl
  (:require
   [acme.server.core :as core]
   [integrant.repl :as ig-repl]
   [integrant.repl.state :as state]
   ))

(defonce ^:private options (atom {}))

(defn configure!
  "Merge `opts` into the Integrant system configuration used by `start`/`go`.
  Useful for overriding the HTTP port or database URL in a dev REPL."
  [opts]
  (swap! options merge opts))

(defn reset-options!
  "Clear any overrides added via `configure!`."
  []
  (reset! options {}))

(ig-repl/set-prep!
  (fn []
    (core/system-config @options)))

(defn start []
  (ig-repl/go))

(defn stop []
  (ig-repl/halt))

(defn go []
  (start))

(defn reset []
  (ig-repl/reset))

(defn system []
  @state/system)
(comment
(require 'repl) 
(repl/start)
(repl/stop)
(repl/reset)
)

(comment
(require 'acme.server.handlers.todos)
(require 'acme.server.services.todo)
(acme.server.services.todo/list-todos)
  )