(ns acme.server.schemas.user
  (:require
   [clojure.string :as str]))

(def ^:private non-blank-string
  [:and
   :string
   [:fn {:error/message "must be a non-blank string"}
    (complement str/blank?)]] )

(def ^:private uuid-regex
  #"(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

(def uuid-path
  [:map
   [:uuid [:and
           :string
           [:fn {:error/message "must be a valid uuid"}
            #(re-matches uuid-regex % )]]]])

(def create-body
  [:map
   [:name non-blank-string]
   [:age [:int {:min 0}]]
   [:uuid {:optional true}
    [:and
     :string
     [:fn {:error/message "must be a valid uuid"}
      #(re-matches uuid-regex %)]]]])

(def update-body
  [:map
   [:name {:optional true} non-blank-string]
  [:age {:optional true} [:int {:min 0}]]])

(def user-response
  [:map {:closed true}
   [:uuid non-blank-string]
   [:name non-blank-string]
   [:age [:int {:min 0}]]])

(def user-list-response
  [:sequential user-response])
