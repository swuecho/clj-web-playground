(ns acme.server.core
  (:gen-class)
  (:require
   [clojure.string :as cstr]
   [acme.server.handlers.health :as health]
   [acme.server.handlers.todos :as todos]
   [acme.server.handlers.users :as users]
   [acme.server.schemas.todo :as todo.schema]
   [acme.server.schemas.user :as user.schema]
   [acme.server.http :as http]
   [acme.server.middleware.logging :refer [wrap-request-logging]]
   [reitit.ring :as ring]
   [reitit.coercion.malli :as malli-coercion]
   [reitit.ring.coercion :as ring-coercion]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [reitit.openapi :as openapi]
   [reitit.swagger-ui :as swagger-ui]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.reload :refer [wrap-reload]]))

(defn- truthy-env? [value]
  (contains? #{"1" "true" "yes" "on"}
             (some-> value cstr/lower-case)))

(def reload-enabled?
  (not (truthy-env? (System/getenv "ACME_DISABLE_RELOAD"))))

(def routes
  [["/api/health"
    {:get {:summary "Service health check"
           :tags ["System"]
           :handler #'health/health-response
           :responses {200 {:body [:map [:status [:enum "ok" "error"]]]}}}}]

   ["/api/todo"
    {:get {:summary "List todos"
           :tags ["Todos"]
           :handler #'todos/list-response
           :responses {200 {:body todo.schema/todo-list-response}}}
     :post {:summary "Create todo"
            :tags ["Todos"]
            :handler #'todos/create-response
            :parameters {:body todo.schema/create-body}
            :responses {201 {:body todo.schema/todo-response}}}}]

   ["/api/todo/:id"
    {:parameters {:path todo.schema/id-path}
     :get {:summary "Fetch todo"
           :tags ["Todos"]
           :handler #'todos/fetch-response
           :responses {200 {:body todo.schema/todo-response}
                       404 {:body [:map [:error :string]]}}}
     :put {:summary "Replace todo"
           :tags ["Todos"]
           :handler #'todos/update-response
           :parameters {:body todo.schema/update-body}
           :responses {200 {:body todo.schema/todo-response}
                       400 {:body [:map [:error :string]]}
                       404 {:body [:map [:error :string]]}}}
     :patch {:summary "Update todo"
             :tags ["Todos"]
             :handler #'todos/update-response
             :parameters {:body todo.schema/update-body}
             :responses {200 {:body todo.schema/todo-response}
                         400 {:body [:map [:error :string]]}
                         404 {:body [:map [:error :string]]}}}
     :delete {:summary "Delete todo"
              :tags ["Todos"]
              :handler #'todos/delete-response
              :responses {200 {:body [:map [:status :string]]}
                          404 {:body [:map [:error :string]]}}}}]

   ["/api/users"
    {:get {:summary "List users"
           :tags ["Users"]
           :handler #'users/users-response
           :responses {200 {:body user.schema/user-list-response}}}
     :post {:summary "Create user"
            :tags ["Users"]
            :handler #'users/add-user-response
            :parameters {:body user.schema/create-body}
            :responses {201 {:body user.schema/user-response}
                        400 {:body [:map [:error :string]]}
                        409 {:body [:map [:error :string]]}}}}]

   ["/api/users/:uuid"
    {:parameters {:path user.schema/uuid-path}
     :put {:summary "Replace user"
           :tags ["Users"]
           :handler #'users/update-user-response
           :parameters {:body user.schema/update-body}
           :responses {200 {:body user.schema/user-response}
                       400 {:body [:map [:error :string]]}
                       404 {:body [:map [:error :string]]}}}
     :patch {:summary "Update user"
             :tags ["Users"]
             :handler #'users/update-user-response
             :parameters {:body user.schema/update-body}
             :responses {200 {:body user.schema/user-response}
                         400 {:body [:map [:error :string]]}
                         404 {:body [:map [:error :string]]}}}
     :delete {:summary "Delete user"
              :tags ["Users"]
              :handler #'users/delete-user-response
              :responses {200 {:body user.schema/user-response}
                          400 {:body [:map [:error :string]]}
                          404 {:body [:map [:error :string]]}}}}]

   ["/openapi.json"
    {:get {:no-doc true
           :openapi {:id :acme-api}
           :handler (openapi/create-openapi-handler)}}]])

(def router
  (ring/router
   routes
   {:data {:coercion (malli-coercion/create)
           :muuntaja http/muuntaja-instance
           :openapi {:id :acme-api
                     :info {:title "Acme API"
                            :version "1.0.0"
                            :description "API for the Acme web playground backend."}}
           :middleware [parameters/parameters-middleware
                        muuntaja/format-negotiate-middleware
                        muuntaja/format-response-middleware
                        muuntaja/format-request-middleware
                        ring-coercion/coerce-response-middleware
                        ring-coercion/coerce-request-middleware
                        ring-coercion/coerce-exceptions-middleware]}}))

(def swagger-ui-handler
  (swagger-ui/create-swagger-ui-handler
   {:path "/docs"
    :url "/openapi.json"
    :config {:displayRequestDuration true
             :deepLinking true}}))

(def handler
  (wrap-request-logging
   (ring/ring-handler
   router
    (ring/routes
     swagger-ui-handler
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
