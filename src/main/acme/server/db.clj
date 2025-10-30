(ns acme.server.db
  (:require
   [clojure.string :as str]
   [integrant.core :as ig]
   [methodical.core :as m]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [toucan2.connection :as conn]
   [toucan2.core :as t2]
   [toucan2.jdbc.connection]
   [toucan2.jdbc.options :as jdbc.options])
  (:import
   (java.lang AutoCloseable)
   (java.net URI)))

(def default-database-url "postgresql://hwu:using555@192.168.0.135:5432/hwu")

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

(defn resolve-database-url
  "Resolve the database URL from `opts` or the environment, falling back to
  `default-database-url`."
  ([] (resolve-database-url nil))
  ([database-url]
   (or database-url
       (System/getenv "DATABASE_URL")
       default-database-url)))

(defonce managed-datasource (atom nil))

(defonce default-datasource
  (delay
    (jdbc/get-datasource (uri->db-spec (resolve-database-url)))))

(defn- record-datasource! [ds]
  (reset! managed-datasource ds))

(defn- clear-datasource! []
  (reset! managed-datasource nil))

(defn ds []
  (or @managed-datasource
      @default-datasource))

(swap! jdbc.options/global-options
       assoc
       :builder-fn rs/as-unqualified-lower-maps)

(m/defmethod conn/do-with-connection :default
  [_connectable f]
  (conn/do-with-connection (ds) f))

(defn query
  "Execute a SQL statement and realize the full result set."
  [statement]
  (t2/query statement))

(defn query-one
  "Execute a SQL statement and return the first row, if any."
  [statement]
  (t2/query-one statement))

(defmacro with-transaction
  "Run `body` within a transaction using the default datasource."
  [& body]
  `(conn/with-transaction [] ~@body))

(defmethod ig/init-key :acme.server/db
  [_ {:keys [database-url]}]
  (let [resolved-url (resolve-database-url database-url)
        ds (jdbc/get-datasource (uri->db-spec resolved-url))]
    (record-datasource! ds)
    ds))

(defmethod ig/halt-key! :acme.server/db
  [_ datasource]
  (try
    (when (instance? AutoCloseable datasource)
      (.close ^AutoCloseable datasource))
    (finally
      (clear-datasource!))))
