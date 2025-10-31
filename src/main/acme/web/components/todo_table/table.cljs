(ns acme.web.components.todo-table.table
  (:require
   [reagent.core :as r]
   [acme.web.components.todo-row-actions :refer [todo-row-actions]]))

(defn status-pill [completed?]
  (let [completed? (boolean completed?)]
    [:span {:class (str "badge "
                        (if completed?
                          "badge-success"
                          "badge-warning"))}
     (if completed? "Completed" "Pending")]))

(defn- sortable-title [{:keys [field direction dispatch-sort]} label target-field]
  (let [active? (= target-field field)
        next-direction (if active?
                         (if (= direction :asc) :desc :asc)
                         :asc)
        indicator (when active?
                    (if (= direction :asc) "↑" "↓"))]
    (r/as-element
     [:button {:type "button"
               :class (str "btn btn-ghost btn-xs gap-1 px-2 py-1 font-semibold"
                           (when active? " text-secondary"))
               :on-click #(dispatch-sort target-field next-direction)}
      [:span label]
      (when indicator
        [:span {:aria-hidden "true"} indicator])
      [:span {:class "sr-only"}
       (if (= next-direction :asc)
         (str "Sort by " label " ascending")
         (str "Sort by " label " descending"))]])))

(defn build-columns [{:keys [field direction dispatch-sort]}]
  (let [config {:field field
                :direction direction
                :dispatch-sort dispatch-sort}]
    (clj->js
     [{:title "ID"
       :dataIndex "id"
       :key "id"
       :align "left"
       :width 90
       :render (fn [id _record _index]
                 (r/as-element [:span {:class "font-mono text-sm"} id]))}
      {:title "Title"
       :dataIndex "title"
       :key "title"
       :align "left"
       :className "whitespace-pre-wrap"
       :render (fn [title _record _index]
                 (r/as-element [:span {:class "text-base-content"} (or title "-")]))}
      {:title (sortable-title config "Completed" :completed)
       :dataIndex "completed"
       :key "completed"
       :align "center"
       :width 140
       :render (fn [completed _record _index]
                 (r/as-element [status-pill completed]))}
      {:title (sortable-title config "Created" :created_at)
       :dataIndex "created_at"
       :key "created_at"
       :align "left"
       :width 200
       :render (fn [created-at _record _index]
                 (r/as-element [:span {:class "font-mono text-xs sm:text-sm"}
                                (or created-at "-")]))}
      {:title (sortable-title config "Updated" :updated_at)
       :dataIndex "updated_at"
       :key "updated_at"
       :align "left"
       :width 200
       :render (fn [updated-at _record _index]
                 (r/as-element [:span {:class "font-mono text-xs sm:text-sm"}
                                (or updated-at "-")]))}
      {:title "Actions"
       :key "actions"
       :align "right"
       :width 180
       :render (fn [_ record _index]
                 (let [todo (js->clj record :keywordize-keys true)]
                   (r/as-element [todo-row-actions (:id todo)])))}])))

(defn format-rows [todos]
  (->> todos
       (map (fn [todo]
              (-> todo
                  (assoc :key (str (:id todo)))
                  (update :completed boolean))))
       (clj->js)))
