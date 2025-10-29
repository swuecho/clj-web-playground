(ns acme.server.core
  (:gen-class)
  (:require
   [acme.server.handlers.health :as health]
   [acme.server.handlers.users :as users]
   [acme.server.http :as http]
   [acme.server.middleware.logging :refer [wrap-request-logging]]
   [reitit.ring :as ring]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [ring.adapter.jetty :as jetty]))

(def routes
  [["/api/health" {:get health/health-response}]
   ["/api/users" {:get users/users-response
                   :post users/add-user-response}]
   ["/api/users/:uuid" {:put users/update-user-response
                         :patch users/update-user-response
                         :delete users/delete-user-response}]])

(def router
  (ring/router
   routes
   {:data {:muuntaja http/muuntaja-instance
           :middleware [parameters/parameters-middleware
                        muuntaja/format-negotiate-middleware
                        muuntaja/format-request-middleware
                        muuntaja/format-response-middleware]}}))

(def app
  (wrap-request-logging
   (ring/ring-handler
    router
    (ring/routes
     (ring/redirect-trailing-slash-handler)
     (ring/create-default-handler
      {:not-found http/not-found})))))

(defonce server (atom nil))

(defn stop! []
  (when-let [s @server]
    (.stop s)
    (reset! server nil)))

(defn start!
  ([] (start! {}))
  ([{:keys [port join?]
     :or {port (some-> (System/getenv "PORT") Integer/parseInt)
          join? false}}]
   (let [port (or port 8081)]
     (stop!)
     (let [running (jetty/run-jetty #'app {:port port :join? join?})]
       (reset! server running)
       running))))

(defn -main
  [& [port]]
  (let [port (some-> port Integer/parseInt)
        server (start! {:port port :join? true})]
    (println (format "User API listening on http://localhost:%s" (.getPort server)))))
