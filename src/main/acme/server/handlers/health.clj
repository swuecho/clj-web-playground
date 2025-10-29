(ns acme.server.handlers.health
  (:require
   [next.jdbc :as jdbc]
   [acme.server.db :as db]
   [acme.server.http :as http]))

(defn health-response [_]
  (try
    (jdbc/execute-one! (db/ds) ["select 1 as ok"] db/result-opts)
    (http/respond-json {:status "ok"})
    (catch Exception _
      (http/respond-json {:status "error"} 500))))
