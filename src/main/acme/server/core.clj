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
   [integrant.core :as ig]
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

(def default-port 8082)

(def default-reload-dirs ["src/main" "src/dev"])

(defn- parse-port [value]
  (cond
    (nil? value) nil
    (integer? value) value
    (string? value) (Integer/parseInt value)
    :else (Integer/parseInt (str value))))

(defn- resolve-port [port]
  (or (parse-port port)
      (some-> (System/getenv "PORT") Integer/parseInt)
      default-port))

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

(defonce system* (atom nil))

(defn system-config
  "Build the Integrant system configuration. Accepts optional overrides:
  - `:port` will override the HTTP port (default 8081 or $PORT).
  - `:reload?` enables wrap-reload (default honours `ACME_DISABLE_RELOAD`).
  - `:reload-dirs` overrides the directories that trigger reloads.
  - `:database-url` overrides the JDBC connection string."
  ([] (system-config {}))
  ([{:keys [port reload? reload-dirs database-url] :as _opts}]
   (let [reload? (if (some? reload?) reload? reload-enabled?)
         reload-dirs (or reload-dirs default-reload-dirs)]
     {:acme.server/db {:database-url database-url}
      :acme.server/http-handler {:reload? reload?
                                 :reload-dirs reload-dirs
                                 :db (ig/ref :acme.server/db)}
      :acme.server/http-server {:port (resolve-port port)
                                :handler (ig/ref :acme.server/http-handler)}})))

(defmethod ig/init-key :acme.server/http-handler
  [_ {:keys [reload? reload-dirs]}]
  (let [reload? (if (some? reload?) reload? reload-enabled?)
        dirs (or reload-dirs default-reload-dirs)]
    (if reload?
      (wrap-reload #'handler {:dirs dirs})
      handler)))

(defmethod ig/init-key :acme.server/http-server
  [_ {:keys [handler port]}]
  (jetty/run-jetty handler {:port (resolve-port port)
                            :join? false}))

(defmethod ig/halt-key! :acme.server/http-server
  [_ server]
  (when server
    (.stop server)))

(defn stop! []
  (when-let [system @system*]
    (try
      (ig/halt! system)
      (finally
        (reset! system* nil)))))

(defn start!
  ([] (start! {}))
  ([opts]
   (stop!)
   (let [config (system-config opts)
         system (ig/init config)
         server (:acme.server/http-server system)]
     (reset! system* system)
     server)))

(defn -main
  [& [port]]
  (let [resolved-port (resolve-port port)
        server (start! {:port resolved-port})]
    (println (format "User API listening on http://localhost:%s" resolved-port))
    (.join server)))
