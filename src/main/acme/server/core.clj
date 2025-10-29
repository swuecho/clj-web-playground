(ns acme.server.core
  (:gen-class)
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [clojure.walk :as walk]
   [muuntaja.core :as m]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [reitit.ring :as ring]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [ring.adapter.jetty :as jetty])
  (:import
   (java.net URI)
   (java.sql SQLException)))

(def default-database-url "postgresql://hwu:using555@192.168.0.135:5432/hwu")

(def muuntaja-instance
  (m/create
   (assoc m/default-options :default-format "application/json")))

(def json-format "application/json")

(def json-content-type (str json-format "; charset=utf-8"))

(defn- elapsed-ms [started]
  (long (/ (- (System/nanoTime) started) 1000000)))

(defn- present-map [value]
  (when (and (map? value) (seq value))
    value))

(defn- summarize-request [request]
  (let [method (some-> request :request-method name str/upper-case)
        uri (:uri request)
        details (cond-> {}
                   (:query-string request)
                   (assoc :query-string (:query-string request))
                   (present-map (:path-params request))
                   (assoc :path-params (:path-params request))
                   (present-map (:query-params request))
                   (assoc :query-params (:query-params request))
                   (present-map (:body-params request))
                   (assoc :body-params (:body-params request)))]
    {:method method
     :uri uri
     :details details}))

(defn- summarize-response [response elapsed]
  (let [content-type (get-in response [:headers "Content-Type"])]
    (cond-> {:status (:status response)
             :elapsed-ms elapsed}
      content-type (assoc :content-type content-type))))

(defn- wrap-request-logging [handler]
  (fn [request]
    (let [{:keys [method uri details]} (summarize-request request)
          started (System/nanoTime)]
      (if (seq details)
        (log/infof "-> %s %s %s" method uri (pr-str details))
        (log/infof "-> %s %s" method uri))
      (try
        (let [response (handler request)
              elapsed (elapsed-ms started)
              summary (summarize-response response elapsed)]
          (log/infof "<- %s %s %s" method uri (pr-str summary))
          response)
        (catch Exception ex
          (let [elapsed (elapsed-ms started)]
            (log/errorf ex "X %s %s failed after %dms" method uri elapsed)
            (throw ex)))))))

(defn- respond-json
  ([data]
   (respond-json data 200))
  ([data status]
   {:status status
    :headers {"Content-Type" json-content-type}
    :body (m/encode muuntaja-instance json-format data)}))

(defn- normalize-db-url [url]
  (if (str/starts-with? url "jdbc:")
    (subs url 5)
    url))

(defn- uri->db-spec [database-uri]
  (let [uri (URI. (normalize-db-url database-uri))
        path (.getPath uri)
        dbname (when (and path (not (str/blank? path)))
                 (str/replace-first path #"^/" ""))
        [user password] (when-let [info (.getUserInfo uri)]
                          (str/split info #":"))
        port (.getPort uri)]
    (cond-> {:dbtype "postgresql"}
      (.getHost uri) (assoc :host (.getHost uri))
      dbname (assoc :dbname dbname)
      user (assoc :user user)
      password (assoc :password password)
      (pos? port) (assoc :port port))))

(def database-url
  (or (System/getenv "DATABASE_URL") default-database-url))

(defonce datasource
  (delay
    (jdbc/get-datasource (uri->db-spec database-url))))

(defn- ds []
  @datasource)

(def result-opts {:builder-fn rs/as-unqualified-lower-maps})

(defn- normalize-age [age]
  (cond
    (int? age) age
    (integer? age) (int age)
    (string? age)
    (let [trimmed (str/trim age)]
      (when-not (str/blank? trimmed)
        (try
          (Integer/parseInt trimmed)
          (catch NumberFormatException _
            nil))))
    :else nil))

(defn- ->uuid [value]
  (cond
    (instance? java.util.UUID value) value
    (string? value)
    (let [trimmed (str/trim value)]
      (when-not (str/blank? trimmed)
        (try
          (java.util.UUID/fromString trimmed)
          (catch IllegalArgumentException _
            nil))))
    :else nil))

(defn- user-exists? [uuid]
  (if-let [uuid (->uuid uuid)]
    (some? (jdbc/execute-one! (ds)
                              ["select 1 from \"UserTable\" where uuid = ? limit 1" uuid]
                              result-opts))
    false))

(defn- normalize-request-map [params]
  (cond
    (map? params) (walk/keywordize-keys params)
    (nil? params) {}
    :else {}))

(defn- validate-user [input]
  (let [params (normalize-request-map input)
        {:keys [uuid name age]} params
        trimmed-name (some-> name str/trim)
        parsed-age (normalize-age age)
        supplied-uuid (some-> uuid str str/trim not-empty)
        parsed-uuid (some-> supplied-uuid ->uuid)
        duplicate? (when parsed-uuid
                     (user-exists? parsed-uuid))]
    (cond
      (or (nil? trimmed-name) (str/blank? trimmed-name))
      {:status 400 :message "Name is required"}

      (or (nil? parsed-age) (neg? parsed-age))
      {:status 400 :message "Age must be a non-negative integer"}

      (and supplied-uuid (nil? parsed-uuid))
      {:status 400 :message "Invalid uuid format"}

      duplicate?
      {:status 409 :message "A user with that uuid already exists"}

      :else
      {:status 201
       :user {:uuid supplied-uuid
              :name trimmed-name
              :age parsed-age}})))

(defn- validate-update [input]
  (let [params (normalize-request-map input)
        {:keys [name age]} params
        name-present? (contains? params :name)
        age-present? (contains? params :age)
        trimmed-name (when name-present? (some-> name str/trim))
        parsed-age (when age-present? (normalize-age age))]
    (cond
      (not (or name-present? age-present?))
      {:status 400 :message "Supply at least one field to update"}

      (and name-present? (or (nil? trimmed-name) (str/blank? trimmed-name)))
      {:status 400 :message "Name is required"}

      (and age-present? (or (nil? parsed-age) (neg? parsed-age)))
      {:status 400 :message "Age must be a non-negative integer"}

      :else
      {:status 200
       :updates (cond-> {}
                  name-present? (assoc :name trimmed-name)
                  age-present? (assoc :age parsed-age))})))

(defn- ensure-unique-uuid [{:keys [uuid] :as user}]
  (if uuid
    user
    (loop [candidate (str (java.util.UUID/randomUUID))]
      (if (user-exists? candidate)
        (recur (str (java.util.UUID/randomUUID)))
        (assoc user :uuid candidate)))))

(defn- not-found [_]
  (respond-json {:error "Not found"} 404))

(defn- users-response [_]
  (let [users (jdbc/execute! (ds)
                             ["select uuid, name, age from \"UserTable\" order by name asc"]
                             result-opts)]
    (respond-json users)))

(defn- add-user-response [{:keys [body-params]}]
  (let [{:keys [status message user]} (validate-user body-params)]
    (if user
      (let [sanitized (ensure-unique-uuid user)]
        (try
          (let [created (jdbc/with-transaction [tx (ds)]
                          (jdbc/execute-one! tx
                                             ["insert into \"UserTable\" (uuid, name, age) values (?, ?, ?) returning uuid, name, age"
                                              (:uuid sanitized)
                                              (:name sanitized)
                                              (:age sanitized)]
                                             result-opts))]
            (respond-json created status))
          (catch SQLException ex
            (if (= "23505" (.getSQLState ex))
              (respond-json {:error "A user with that uuid already exists"} 409)
              (throw ex)))))
      (respond-json {:error message} status))))

(defn- build-update-sql [{:keys [name age]}]
  (let [set-fragments (cond-> []
                         name (conj "\"name\" = ?")
                         age (conj "age = ?"))
        params (cond-> []
                 name (conj name)
                 age (conj age))]
    (when (seq set-fragments)
      {:sql (str "update \"UserTable\" set " (str/join ", " set-fragments) " where uuid = ? returning uuid, name, age")
       :params params})))

(defn- update-user-response [{:keys [path-params body-params]}]
  (let [uuid (:uuid path-params)
        uuid (some-> uuid str/trim)
        uuid-param (->uuid uuid)]
    (cond
      (str/blank? uuid)
      (respond-json {:error "User uuid is required"} 400)

      (nil? uuid-param)
      (respond-json {:error "Invalid uuid format"} 400)

      :else
      (let [{:keys [status message updates]} (validate-update body-params)]
        (if updates
          (if-let [{:keys [sql params]} (build-update-sql updates)]
            (let [statement (conj params uuid-param)]
              (try
                (let [updated (jdbc/with-transaction [tx (ds)]
                                 (jdbc/execute-one! tx
                                                    (into [sql] statement)
                                                    result-opts))]
                  (if updated
                    (respond-json updated status)
                    (not-found nil)))
                (catch SQLException ex
                  (throw ex))))
            (respond-json {:error "Supply at least one field to update"} 400))
          (respond-json {:error message} status))))))

(defn- delete-user-response [{:keys [path-params]}]
  (let [uuid (:uuid path-params)
        uuid (some-> uuid str/trim)
        uuid-param (->uuid uuid)]
    (cond
      (str/blank? uuid)
      (respond-json {:error "User uuid is required"} 400)

      (nil? uuid-param)
      (respond-json {:error "Invalid uuid format"} 400)

      :else
      (let [deleted (jdbc/with-transaction [tx (ds)]
                       (jdbc/execute-one! tx
                                          ["delete from \"UserTable\" where uuid = ? returning uuid, name, age" uuid-param]
                                          result-opts))]
        (if deleted
          (respond-json deleted)
          (not-found nil))))))

(defn- health-response [_]
  (try
    (jdbc/execute-one! (ds) ["select 1 as ok"] result-opts)
    (respond-json {:status "ok"})
    (catch Exception _
      (respond-json {:status "error"} 500))))

(def routes
  [["/api/health" {:get health-response}]
   ["/api/users" {:get users-response
                   :post add-user-response}]
   ["/api/users/:uuid" {:put update-user-response
                         :patch update-user-response
                         :delete delete-user-response}]])

(def router
  (ring/router
   routes
   {:data {:muuntaja muuntaja-instance
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
      {:not-found not-found})))))

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
