(ns acme.server.db
  (:require
   [clojure.string :as str]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]
   [methodical.core :as m]
   [toucan2.connection :as conn]
   [toucan2.core :as t2]
   [toucan2.jdbc.connection]
   [toucan2.jdbc.options :as jdbc.options])
  (:import
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

(def database-url
  (or (System/getenv "DATABASE_URL") default-database-url))

(defonce datasource
  (delay
    (jdbc/get-datasource (uri->db-spec database-url))))

(defn ds []
  @datasource)

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
