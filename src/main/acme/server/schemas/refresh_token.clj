(ns acme.server.schemas.refresh-token
  (:require
   [clojure.string :as str]))

(def non-blank-string
  [:and
   :string
   [:fn {:error/message "must be a non-blank string"}
    (complement str/blank?)]] )

(def uuid-regex
  #"(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

(def token-id-schema
  [:and
   :string
   [:fn {:error/message "must be a valid uuid"}
    #(re-matches uuid-regex %)]])

(def token-id-path
  [:map
   [:token-id token-id-schema]])

(def refresh-token-response
  [:map {:closed true}
   [:id token-id-schema]
   [:created_at {:optional true} [:maybe non-blank-string]]
   [:last_used_at {:optional true} [:maybe non-blank-string]]
   [:expires_at non-blank-string]
   [:revoked_at {:optional true} [:maybe non-blank-string]]
   [:revoked? :boolean]])

(def refresh-token-list-response
  [:sequential refresh-token-response])
