(ns acme.server.db
  (:require
   [clojure.string :as str]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs])
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

(def result-opts {:builder-fn rs/as-unqualified-lower-maps})
