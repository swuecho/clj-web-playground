(ns acme.web.components.todo-table
  (:require
   [reagent.core :as r]
   [re-frame.core :as rf]
   [acme.web.events :as events]
   [acme.web.subs :as subs]
   [acme.web.components.todo-row-actions :refer [todo-row-actions]]
   ["@rc-component/table" :default rc-table]))

(def sort-options
  [{:value :completed :label "Completed"}
   {:value :created_at :label "Created"}
   {:value :updated_at :label "Updated"}])

(def direction-options
  [{:value :asc :label "Ascending"}
   {:value :desc :label "Descending"}])

(defn- status-pill [completed?]
  (let [completed? (boolean completed?)]
    [:span {:class (str "badge "
                        (if completed?
                          "badge-success"
                          "badge-warning"))}
     (if completed? "Completed" "Pending")]))

(defn todo-table []
  (let [todos (rf/subscribe [::subs/sorted-todos])
        loading? (rf/subscribe [::subs/todos-loading?])
        error (rf/subscribe [::subs/todos-error])
        sort-config (rf/subscribe [::subs/todo-sort])]
    (fn []
      (let [{:keys [field direction]} @sort-config
            field (or field :created_at)
            direction (or direction :desc)
            table-data (->> @todos
                            (map (fn [todo]
                                   (-> todo
                                       (assoc :key (str (:id todo)))
                                       (update :completed boolean))))
                            (clj->js))
            columns (clj->js
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
                      {:title "Completed"
                       :dataIndex "completed"
                       :key "completed"
                       :align "center"
                       :width 140
                       :render (fn [completed _record _index]
                                 (r/as-element [status-pill completed]))}
                      {:title "Created"
                       :dataIndex "created_at"
                       :key "created_at"
                       :align "left"
                       :width 200
                       :render (fn [created-at _record _index]
                                 (r/as-element [:span {:class "font-mono text-xs sm:text-sm"}
                                                (or created-at "-")]))}
                      {:title "Updated"
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
                                   (r/as-element [todo-row-actions (:id todo)])))}])
            status-section (cond
                              @loading? [:div {:class "alert alert-info"}
                                         [:span "Loading todos..."]]
                              @error [:div {:class "alert alert-error"}
                                      [:span @error]]
                              (empty? @todos) [:div {:class "alert alert-warning"}
                                               [:span "No todos found. Add your first item to get started."]]
                              :else nil)]
        [:div {:class "space-y-6"}
         [:div {:class "flex flex-wrap items-center justify-between gap-4"}
          [:div {:class "flex flex-wrap items-center gap-3"}
           [:label {:class "form-control max-w-xs"}
            [:div {:class "label"}
             [:span {:class "label-text font-semibold"} "Sort field"]]
            [:select {:class "select select-bordered select-sm"
                      :value (name field)
                      :on-change #(let [value (keyword (.. % -target -value))]
                                    (rf/dispatch [::events/set-todo-sort {:field value
                                                                           :direction direction}]))}
             (for [{:keys [value label]} sort-options]
               ^{:key (name value)}
               [:option {:value (name value)} label])]]
           [:label {:class "form-control max-w-xs"}
            [:div {:class "label"}
             [:span {:class "label-text font-semibold"} "Sort direction"]]
            [:select {:class "select select-bordered select-sm"
                      :value (name direction)
                      :on-change #(let [value (keyword (.. % -target -value))]
                                    (rf/dispatch [::events/set-todo-sort {:field field
                                                                           :direction value}]))}
             (for [{:keys [value label]} direction-options]
               ^{:key (name value)}
               [:option {:value (name value)} label])]]]
          [:div {:class "flex flex-wrap gap-3"}
           [:button {:type "button"
                     :class "btn btn-outline btn-sm"
                     :on-click #(rf/dispatch [::events/fetch-todos])}
            "Reload"]
           [:button {:type "button"
                     :class "btn btn-primary btn-sm"
                     :on-click #(rf/dispatch [::events/open-add-todo-dialog])}
            "Add Todo"]]]
         (when status-section
           status-section)
         (when (seq @todos)
           [:div {:class "rounded-2xl border border-base-200 bg-base-100 shadow-sm"}
            [:> rc-table {:columns columns
                          :data table-data
                          :rowKey "key"
                          :className "todo-table"
                          :tableLayout "auto"
                          :scroll #js {:x "max-content"}}]])]))))
