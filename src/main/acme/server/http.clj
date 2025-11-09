(ns acme.server.http
  (:require
   [clojure.string :as str]
   ))



(def json-format "application/json")

(defn respond-json
  ([data]
   (respond-json data 200))
  ([data status]
   {:status status
    :body data
    :muuntaja/response {:format json-format}}))

(defn not-found [_]
  (respond-json {:error "Not found"} 404))

(defn json-content-type? [content-type]
  (some-> content-type str/lower-case (str/starts-with? "application/json")))
