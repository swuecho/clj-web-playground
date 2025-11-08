(ns acme.server.schemas.auth
  (:require
   [clojure.string :as str]
   [acme.server.schemas.user :as user.schema]))

(def non-blank-string
  [:and
   :string
   [:fn {:error/message "must be a non-blank string"}
    (complement str/blank?)]])

(def login-body
  [:map
   [:email user.schema/email-schema]
   [:password user.schema/password-schema]])

(def register-body
  [:map
   [:email user.schema/email-schema]
   [:password user.schema/password-schema]
   [:name {:optional true} non-blank-string]])

(def login-response
  [:map
   [:access_token non-blank-string]
   [:token_type non-blank-string]
   [:expires_at non-blank-string]
   [:expires_in [:int {:min 1}]]
   [:user user.schema/user-response]])
