(ns acme.server.http
  (:require
   [clojure.string :as str]
   [muuntaja.core :as m]))

(def muuntaja-instance
  (m/create
   (assoc m/default-options :default-format "application/json")))

(def json-format "application/json")

(def json-content-type (str json-format "; charset=utf-8"))

(defn respond-json
  ([data]
   (respond-json data 200))
  ([data status]
   {:status status
    :headers {"Content-Type" json-content-type}
    :body (m/encode muuntaja-instance json-format data)}))

(defn not-found [_]
  (respond-json {:error "Not found"} 404))

(defn json-content-type? [content-type]
  (some-> content-type str/lower-case (str/starts-with? "application/json")))
