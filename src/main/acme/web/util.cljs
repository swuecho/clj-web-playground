(ns acme.web.util
        (:require
         [clojure.string :as str]))


(defn camel-key [k]
  (cond
    (keyword? k)
    (let [n (name k)]
      (if (str/includes? n "-")
        (let [[h & rst] (str/split n #"-")]
          (apply str h (map str/capitalize rst)))
        n))
    :else k))

(defn style->js [style-map]
  (->> style-map
       (map (fn [[k v]]
              [(camel-key k) v]))
       (into {})
       (clj->js)))