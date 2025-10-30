(ns acme.server.handlers.health
  (:require
   [acme.server.db :as db]
   [acme.server.http :as http]))

(defn health-response [_]
  (try
    (db/query-one ["select 1 as ok"])
    (http/respond-json {:status "ok"})
    (catch Exception _
      (http/respond-json {:status "error"} 500))))
