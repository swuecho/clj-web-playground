(ns acme.server.schemas.user
  (:require
   [acme.server.schemas.validation.common :as validation.common]
   [acme.server.schemas.validation.users :as user.validation]))

(def uuid-path
  [:map
   [:uuid validation.common/uuid-string]])

(def password-schema
  [:and
   :string
   [:fn {:error/message (str "must be at least " user.validation/password-min-length " characters")}
    #(>= (count %) user.validation/password-min-length)]])

(def role-schema
  [:enum "admin" "user"])

(def email-schema
  [:and
   validation.common/non-blank-string
   [:fn {:error/message "must be a valid email"}
    user.validation/valid-email?]])

(def create-body
  [:map
   [:name validation.common/non-blank-string]
   [:age [:int {:min 0}]]
   [:email email-schema]
   [:password password-schema]
   [:uuid {:optional true}
    validation.common/uuid-string]])

(def update-body
  [:map
   [:name {:optional true} validation.common/non-blank-string]
   [:age {:optional true} [:int {:min 0}]]
   [:email {:optional true} email-schema]
   [:password {:optional true} password-schema]])

(def user-response
  [:map {:closed true}
   [:uuid validation.common/non-blank-string]
   [:name validation.common/non-blank-string]
   [:age [:int {:min 0}]]
   [:email email-schema]
   [:role role-schema]])

(def user-list-response
  [:sequential user-response])
