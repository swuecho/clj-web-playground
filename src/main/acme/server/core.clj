(ns acme.server.core
  (:gen-class)
  (:require
   [clojure.string :as str]
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

(defn- user-exists? [uuid]
  (some? (jdbc/execute-one! (ds)
                            ["select 1 from \"UserTable\" where uuid = ? limit 1" uuid]
                            result-opts)))

(defn- validate-user [{:keys [uuid name age]}]
  (let [trimmed-name (some-> name str/trim)
        parsed-age (normalize-age age)
        supplied-uuid (some-> uuid str not-empty)
        duplicate? (when supplied-uuid
                     (user-exists? supplied-uuid))]
    (cond
      (or (nil? trimmed-name) (str/blank? trimmed-name))
      {:status 400 :message "Name is required"}

      (or (nil? parsed-age) (neg? parsed-age))
      {:status 400 :message "Age must be a non-negative integer"}

      duplicate?
      {:status 409 :message "A user with that uuid already exists"}

      :else
      {:status 201
       :user {:uuid supplied-uuid
              :name trimmed-name
              :age parsed-age}})))

(defn- validate-update [{:keys [name age] :as params}]
  (let [name-present? (contains? params :name)
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
        uuid (some-> uuid str/trim)]
    (if (str/blank? uuid)
      (respond-json {:error "User uuid is required"} 400)
      (let [{:keys [status message updates]} (validate-update body-params)]
        (if updates
          (if-let [{:keys [sql params]} (build-update-sql updates)]
            (let [statement (conj params uuid)]
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
        uuid (some-> uuid str/trim)]
    (if (str/blank? uuid)
      (respond-json {:error "User uuid is required"} 400)
      (let [deleted (jdbc/with-transaction [tx (ds)]
                       (jdbc/execute-one! tx
                                          ["delete from \"UserTable\" where uuid = ? returning uuid, name, age" uuid]
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
  (ring/ring-handler
   router
   (ring/routes
    (ring/redirect-trailing-slash-handler)
    (ring/create-default-handler
     {:not-found not-found}))))

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
