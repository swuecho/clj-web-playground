(ns acme.server.models.todo
  (:require
   [methodical.core :as m]
   [toucan2.core :as t2]))

(def model ::todo)

(m/defmethod t2/table-name model
  [_]
  :todo_items)

(m/defmethod t2/default-connectable model
  [_]
  :default)

(t2/define-default-fields model
  [:id :title :completed :created_at :updated_at])

(defn all []
  (t2/select model {:order-by [[:id :asc]]}))

(defn fetch [id]
  (when id
    (t2/select-one model :toucan/pk id)))

(defn create! [{:keys [title completed] :as attrs}]
  (let [payload (merge {:title title}
                       (when (some? completed)
                         {:completed (boolean completed)}))]
    (t2/insert-returning-instance! model payload)))

(defn update! [id changes]
  (let [allowed #{:title :completed}
        sanitized (reduce-kv (fn [acc k v]
                               (if (contains? allowed k)
                                 (assoc acc k (if (= k :completed) (boolean v) v))
                                 acc))
                             {}
                             changes)]
    (when (seq sanitized)
      (when (pos? (t2/update! model :toucan/pk id sanitized))
        (fetch id)))))

(defn delete! [id]
  (t2/delete! model :toucan/pk id))
