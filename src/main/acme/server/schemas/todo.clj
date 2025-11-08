(ns acme.server.schemas.todo
  (:require
   [clojure.string :as str]))

(def ^:private non-blank-string
  [:and
   :string
   [:fn {:error/message "must be a non-blank string"}
    (complement str/blank?)]] )

(def id-path
  [:map
   [:id [:int {:min 1}]]])

(def create-body
  [:map
   [:title non-blank-string]
   [:completed {:optional true} :boolean]])

(def update-body
  [:map
   [:title {:optional true} non-blank-string]
   [:completed {:optional true} :boolean]])

(def ^:private uuid-regex
  #"(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

(def ^:private uuid-string
  [:and
   :string
   [:fn {:error/message "must be a valid uuid"}
    #(re-matches uuid-regex %)]])

(def todo-response
  [:map {:closed false}
   [:id [:int {:min 1}]]
   [:title :string]
   [:completed :boolean]
   [:user_id uuid-string]
   [:created_at {:optional true} :any]
   [:updated_at {:optional true} :any]])

(def todo-list-response
  [:sequential todo-response])
