(ns acme.server.core
  (:gen-class)
  (:require
   [clojure.string :as cstr]
   [acme.server.handlers.auth :as auth-handler]
   [acme.server.handlers.health :as health]
   [acme.server.handlers.todos :as todos]
   [acme.server.handlers.users :as users]
   [acme.server.schemas.auth :as auth.schema]
   [acme.server.schemas.todo :as todo.schema]
   [acme.server.schemas.user :as user.schema]
   [acme.server.schemas.refresh-token :as refresh.schema]
   [acme.server.http :as http]
   [acme.server.middleware.auth :as auth-middleware]
   [acme.server.middleware.logging :as logging :refer [wrap-request-logging]]
   [integrant.core :as ig]
   [reitit.ring :as ring]
   [reitit.coercion.malli :as malli-coercion]
   [reitit.ring.coercion :as ring-coercion]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [reitit.openapi :as openapi]
   [reitit.swagger-ui :as swagger-ui]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.cors :refer [wrap-cors]]
   [ring.middleware.cookies :refer [wrap-cookies]]
   [ring.middleware.reload :refer [wrap-reload]]
   [ring.util.response :as response]))

(defn- truthy-env? [value]
  (contains? #{"1" "true" "yes" "on"}
             (some-> value cstr/lower-case)))

(def reload-enabled?
  (not (truthy-env? (System/getenv "ACME_DISABLE_RELOAD"))))

(def default-port 8082)

(def default-reload-dirs ["src/main" "src/dev"])

(def static-asset-root "public")

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

   ["/api/auth/login"
   {:post {:summary "Authenticate and issue a JWT"
            :tags ["Auth"]
            :handler #'auth-handler/login-response
            :parameters {:body auth.schema/login-body}
            :responses {200 {:body auth.schema/login-response}
                        400 {:body [:map [:error :string]]}
                        401 {:body [:map [:error :string]]}}}}]

  ["/api/auth/refresh"
   {:post {:summary "Exchange refresh token for a new session"
           :tags ["Auth"]
           :handler #'auth-handler/refresh-response
           :responses {200 {:body auth.schema/refresh-response}
                       401 {:body [:map [:error :string]]}
                       404 {:body [:map [:error :string]]}}}}]

  ["/api/auth/register"
   {:post {:summary "Register a new user"
           :tags ["Auth"]
           :handler #'auth-handler/register-response
           :parameters {:body auth.schema/register-body}
           :responses {201 {:body user.schema/user-response}
                       400 {:body [:map [:error :string]]}
                       409 {:body [:map [:error :string]]}}}}]

  ["/api/auth/logout"
   {:post {:summary "Revoke refresh token cookie"
           :tags ["Auth"]
           :handler #'auth-handler/logout-response
           :responses {200 {:body auth.schema/logout-response}}}}]

   ["/api/todo"
    {:middleware [auth-middleware/wrap-require-identity]
     :get {:summary "List todos"
           :tags ["Todos"]
           :handler #'todos/list-response
           :responses {200 {:body todo.schema/todo-list-response}}}
     :post {:summary "Create todo"
            :tags ["Todos"]
            :handler #'todos/create-response
            :parameters {:body todo.schema/create-body}
            :responses {201 {:body todo.schema/todo-response}}}}]

   ["/api/todo/:id"
    {:middleware [auth-middleware/wrap-require-identity]
     :parameters {:path todo.schema/id-path}
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
    {:middleware [auth-middleware/wrap-require-identity
                  auth-middleware/wrap-require-admin]
     :get {:summary "List users"
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
    {:middleware [auth-middleware/wrap-require-identity
                  auth-middleware/wrap-require-admin]
     :parameters {:path user.schema/uuid-path}
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

  ["/api/users/:uuid/refresh-tokens"
   {:middleware [auth-middleware/wrap-require-identity
                 auth-middleware/wrap-require-admin]
    :parameters {:path user.schema/uuid-path}
    :get {:summary "List user refresh tokens"
          :tags ["Users" "Auth"]
          :handler #'auth-handler/list-refresh-tokens-response
          :responses {200 {:body refresh.schema/refresh-token-list-response}
                      400 {:body [:map [:error :string]]}
                      404 {:body [:map [:error :string]]}}}}]

  ["/api/users/:uuid/refresh-tokens/:token-id"
   {:middleware [auth-middleware/wrap-require-identity
                 auth-middleware/wrap-require-admin]
    :parameters {:path [:map
                        [:uuid :string]
                        [:token-id refresh.schema/token-id-schema]]}
    :delete {:summary "Revoke a refresh token"
             :tags ["Users" "Auth"]
             :handler #'auth-handler/revoke-refresh-token-response
             :responses {200 {:body [:map [:status :string]]}
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
                        auth-middleware/wrap-authentication
                        logging/wrap-log-response-shape
                        ring-coercion/coerce-response-middleware
                        ring-coercion/coerce-request-middleware
                        ring-coercion/coerce-exceptions-middleware]}}))

(def swagger-ui-handler
  (swagger-ui/create-swagger-ui-handler
   {:path "/docs"
    :url "/openapi.json"
    :config {:displayRequestDuration true
             :deepLinking true}}))

(defn- spa-index-handler
  [{:keys [request-method uri] :as request}]
  (when (and (= request-method :get)
             (not (cstr/starts-with? uri "/api"))
             (not (cstr/starts-with? uri "/openapi"))
             (not (cstr/starts-with? uri "/docs")))
    (when-let [index (response/file-response "index.html" {:root static-asset-root})]
      (response/content-type index "text/html; charset=utf-8"))))

(def handler
  (-> (ring/ring-handler
       router
       (ring/routes
        swagger-ui-handler
        (ring/create-file-handler {:path "/" :root static-asset-root})
        spa-index-handler
        (ring/redirect-trailing-slash-handler)
        (ring/create-default-handler
         {:not-found http/not-found})))
      (wrap-cors
       :access-control-allow-origin [#"(?i)^https?://localhost(:\\d+)?$"
                                     #"(?i)^https?://127\\.0\\.0\\.1(:\\d+)?$"]
       :access-control-allow-methods [:get :post :put :patch :delete :options]
       :access-control-allow-headers ["accept" "accept-language" "content-type" "authorization"]
       :access-control-allow-credentials true)
      wrap-cookies
      wrap-request-logging))

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
