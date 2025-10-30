(ns acme.server.core
  (:gen-class)
  (:require
   [clojure.string :as cstr]
   [acme.server.handlers.health :as health]
   [acme.server.handlers.todos :as todos]
   [acme.server.handlers.users :as users]
   [acme.server.schemas.todo :as todo.schema]
   [acme.server.http :as http]
   [acme.server.middleware.logging :refer [wrap-request-logging]]
   [reitit.ring :as ring]
   [reitit.coercion.malli :as malli-coercion]
   [reitit.ring.coercion :as ring-coercion]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.reload :refer [wrap-reload]]))

(defn- truthy-env? [value]
  (contains? #{"1" "true" "yes" "on"}
             (some-> value cstr/lower-case)))

(def reload-enabled?
  (not (truthy-env? (System/getenv "ACME_DISABLE_RELOAD"))))

(def routes
  [["/api/health"
    {:get {:handler #'health/health-response}}]

   ["/api/todo"
    {:get {:handler #'todos/list-response
           :responses {200 {:body todo.schema/todo-list-response}}}
     :post {:handler #'todos/create-response
            :parameters {:body todo.schema/create-body}
            :responses {201 {:body todo.schema/todo-response}}}}]

   ["/api/todo/:id"
    {:parameters {:path todo.schema/id-path}
     :get {:handler #'todos/fetch-response
           :responses {200 {:body todo.schema/todo-response}
                       404 {:body [:map [:error :string]]}}}
     :put {:handler #'todos/update-response
           :parameters {:body todo.schema/update-body}
           :responses {200 {:body todo.schema/todo-response}
                       400 {:body [:map [:error :string]]}
                       404 {:body [:map [:error :string]]}}}
     :patch {:handler #'todos/update-response
             :parameters {:body todo.schema/update-body}
             :responses {200 {:body todo.schema/todo-response}
                         400 {:body [:map [:error :string]]}
                         404 {:body [:map [:error :string]]}}}
     :delete {:handler #'todos/delete-response
              :responses {200 {:body [:map [:status :string]]}
                          404 {:body [:map [:error :string]]}}}}]

   ["/api/users"
    {:get {:handler #'users/users-response}
     :post {:handler #'users/add-user-response}}]

   ["/api/users/:uuid"
    {:put {:handler #'users/update-user-response}
     :patch {:handler #'users/update-user-response}
     :delete {:handler #'users/delete-user-response}}]])

(def router
  (ring/router
   routes
   {:data {:coercion (malli-coercion/create)
           :muuntaja http/muuntaja-instance
           :middleware [parameters/parameters-middleware
                        muuntaja/format-negotiate-middleware
                        muuntaja/format-request-middleware
                        ring-coercion/coerce-request-middleware
                        muuntaja/format-response-middleware
                        ring-coercion/coerce-response-middleware
                        ring-coercion/coerce-exceptions-middleware]}}))

(def handler
  (wrap-request-logging
   (ring/ring-handler
    router
    (ring/routes
     (ring/redirect-trailing-slash-handler)
     (ring/create-default-handler
      {:not-found http/not-found})))))

(def app
  (if reload-enabled?
    (wrap-reload #'handler {:dirs ["src/main" "src/dev"]})
    handler))

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
