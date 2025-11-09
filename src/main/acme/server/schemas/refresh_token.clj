(ns acme.server.schemas.refresh-token
  (:require
   [acme.server.schemas.validation.common :as validation.common]))

(def token-id-schema
  validation.common/uuid-string)

(def token-id-path
  [:map
   [:token-id token-id-schema]])

(def refresh-token-response
  [:map {:closed true}
   [:id token-id-schema]
   [:created_at {:optional true} [:maybe validation.common/non-blank-string]]
   [:last_used_at {:optional true} [:maybe validation.common/non-blank-string]]
   [:expires_at validation.common/non-blank-string]
   [:revoked_at {:optional true} [:maybe validation.common/non-blank-string]]
   [:revoked? :boolean]])

(def refresh-token-list-response
  [:sequential refresh-token-response])
