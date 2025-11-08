(ns acme.server.schemas.user
  (:require
   [clojure.string :as str]
   [acme.server.users.validation :as user.validation]))

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

(def password-min-length 8)

(def password-schema
  [:and
   :string
   [:fn {:error/message (str "must be at least " password-min-length " characters")}
    #(>= (count %) password-min-length)]])

(def email-schema
  [:and
   non-blank-string
   [:fn {:error/message "must be a valid email"}
    user.validation/valid-email?]])

(def create-body
  [:map
   [:name non-blank-string]
   [:age [:int {:min 0}]]
   [:email email-schema]
   [:password password-schema]
   [:uuid {:optional true}
    [:and
     :string
     [:fn {:error/message "must be a valid uuid"}
      #(re-matches uuid-regex %)]]]])

(def update-body
  [:map
   [:name {:optional true} non-blank-string]
   [:age {:optional true} [:int {:min 0}]]
   [:email {:optional true} email-schema]
   [:password {:optional true} password-schema]])

(def user-response
  [:map {:closed true}
   [:uuid non-blank-string]
   [:name non-blank-string]
   [:age [:int {:min 0}]]
   [:email email-schema]])

(def user-list-response
  [:sequential user-response])
