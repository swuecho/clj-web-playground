(ns acme.server.users.validation
  (:require
   [clojure.string :as str]))

(def email-regex
  #"^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$")

(defn normalize-email [email]
  (let [value (some-> email str str/trim str/lower-case)]
    (when (seq value)
      value)))

(defn valid-email? [email]
  (boolean
   (when-let [value (normalize-email email)]
     (re-matches email-regex value))))
