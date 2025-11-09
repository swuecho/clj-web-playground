(ns acme.server.schemas.todo
  (:require
   [acme.server.schemas.validation.common :as validation.common]))

(def id-path
  [:map
   [:id [:int {:min 1}]]])

(def create-body
  [:map
   [:title validation.common/non-blank-string]
   [:completed {:optional true} :boolean]])

(def update-body
  [:map
   [:title {:optional true} validation.common/non-blank-string]
   [:completed {:optional true} :boolean]])

(def todo-response
  [:map {:closed false}
   [:id [:int {:min 1}]]
   [:title :string]
   [:completed :boolean]
   [:user_id validation.common/uuid-string]
   [:created_at {:optional true} :any]
   [:updated_at {:optional true} :any]])

(def todo-list-response
  [:sequential todo-response])
