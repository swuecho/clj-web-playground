(ns acme.server.schemas.auth
  (:require
   [acme.server.schemas.user :as user.schema]
   [acme.server.schemas.validation.common :as validation.common]))

(def login-body
  [:map
   [:email user.schema/email-schema]
   [:password user.schema/password-schema]])

(def register-body
  [:map
   [:email user.schema/email-schema]
   [:password user.schema/password-schema]
   [:name {:optional true} validation.common/non-blank-string]])

(def login-response
  [:map
   [:access_token validation.common/non-blank-string]
   [:token_type validation.common/non-blank-string]
   [:expires_at validation.common/non-blank-string]
   [:expires_in [:int {:min 1}]]
   [:user user.schema/user-response]])

(def refresh-response
  login-response)

(def logout-response
  [:map
   [:status validation.common/non-blank-string]])
