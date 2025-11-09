(ns acme.server.schemas.validation.common
  (:require
   [clojure.string :as str]))

(def non-blank-string
  [:and
   :string
   [:fn {:error/message "must be a non-blank string"}
    (complement str/blank?)]])

(def uuid-regex
  #"(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

(def uuid-string
  [:and
   :string
   [:fn {:error/message "must be a valid uuid"}
    #(re-matches uuid-regex %)]])
