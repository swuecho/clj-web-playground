(ns acme.web.components.todo-table
  (:require
   [reagent.core :as r]
   [re-frame.core :as rf]
   [acme.web.events :as events]
   [acme.web.subs :as subs]
   [acme.web.components.todo-row-actions :refer [todo-row-actions]]
   ["@rc-component/table" :default rc-table]))

(def per-page-options [10 25 50 100])

(defn- status-pill [completed?]
  (let [completed? (boolean completed?)]
    [:span {:class (str "badge "
                        (if completed?
                          "badge-success"
                          "badge-warning"))}
     (if completed? "Completed" "Pending")]))

(defn todo-table []
  (let [loading? (rf/subscribe [::subs/todos-loading?])
        error (rf/subscribe [::subs/todos-error])
        sort-config (rf/subscribe [::subs/todo-sort])
        filters-config (rf/subscribe [::subs/todo-filters])
        pagination-config (rf/subscribe [::subs/todo-pagination])]
    (fn []
      (let [{:keys [field direction]} @sort-config
            filters (or @filters-config {})
            {:keys [completed created updated]} filters
            created (or created {})
            updated (or updated {})
            {:keys [items page per-page total total-pages start end]}
            (merge {:items [] :page 1 :per-page 25 :total 0 :total-pages 1 :start 0 :end 0}
                   (or @pagination-config {}))
            field (or field :created_at)
            direction (or direction :desc)
            dispatch-sort (fn [target-field target-direction]
                            (rf/dispatch [::events/set-todo-sort {:field target-field
                                                                  :direction target-direction}]))
            sortable-title (fn [label target-field]
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
                                    (str "Sort by " label " descending"))]])))]
        (let [page-items items
              table-data (->> page-items
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
                        {:title (sortable-title "Completed" :completed)
                         :dataIndex "completed"
                         :key "completed"
                         :align "center"
                         :width 140
                         :render (fn [completed _record _index]
                                   (r/as-element [status-pill completed]))}
                        {:title (sortable-title "Created" :created_at)
                         :dataIndex "created_at"
                         :key "created_at"
                         :align "left"
                         :width 200
                         :render (fn [created-at _record _index]
                                   (r/as-element [:span {:class "font-mono text-xs sm:text-sm"}
                                                  (or created-at "-")]))}
                        {:title (sortable-title "Updated" :updated_at)
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
              completed-filter (or completed :all)
              created-after (or (:after created) "")
              created-before (or (:before created) "")
              updated-after (or (:after updated) "")
              updated-before (or (:before updated) "")
              filters-default? (and (= completed-filter :all)
                                    (empty? created-after)
                                    (empty? created-before)
                                    (empty? updated-after)
                                    (empty? updated-before))
              status-section (cond
                               @loading? [:div {:class "alert alert-info"}
                                          [:span "Loading todos..."]]
                               @error [:div {:class "alert alert-error"}
                                       [:span @error]]
                               (zero? total) [:div {:class "alert alert-warning"}
                                              [:span "No todos found. Add your first item to get started."]]
                               :else nil)
              prev-page (max 1 (dec page))
              next-page (min total-pages (inc page))
              can-prev? (> page 1)
              can-next? (< page total-pages)
              display-start (if (pos? total) start 0)
              display-end (if (pos? total) end 0)
              summary (if (pos? total)
                        (str "Showing " display-start "-" display-end " of " total " todos")
                        "No todos to display")
              filter-controls [:div {:class "flex flex-wrap items-end gap-4"}
                               [:div {:class "form-control w-full sm:w-44"}
                                [:label {:class "label"}
                                 [:span {:class "label-text font-semibold"} "Status"]]
                                [:select {:class "select select-bordered select-sm"
                                          :value (name completed-filter)
                                          :on-change #(rf/dispatch [::events/update-todo-filter
                                                                    [:completed]
                                                                    (keyword (.. % -target -value))])}
                                 [:option {:value "all"} "All"]
                                 [:option {:value "completed"} "Completed"]
                                 [:option {:value "pending"} "Pending"]]]
                               [:div {:class "form-control w-full sm:w-56"}
                                [:label {:class "label"}
                                 [:span {:class "label-text font-semibold"} "Created After"]]
                                [:input {:type "datetime-local"
                                         :class "input input-bordered input-sm"
                                         :value created-after
                                         :on-change #(rf/dispatch [::events/update-todo-filter
                                                                   [:created :after]
                                                                   (.. % -target -value)])}]]
                               [:div {:class "form-control w-full sm:w-56"}
                                [:label {:class "label"}
                                 [:span {:class "label-text font-semibold"} "Created Before"]]
                                [:input {:type "datetime-local"
                                         :class "input input-bordered input-sm"
                                         :value created-before
                                         :on-change #(rf/dispatch [::events/update-todo-filter
                                                                   [:created :before]
                                                                   (.. % -target -value)])}]]
                               [:div {:class "form-control w-full sm:w-56"}
                                [:label {:class "label"}
                                 [:span {:class "label-text font-semibold"} "Updated After"]]
                                [:input {:type "datetime-local"
                                         :class "input input-bordered input-sm"
                                         :value updated-after
                                         :on-change #(rf/dispatch [::events/update-todo-filter
                                                                   [:updated :after]
                                                                   (.. % -target -value)])}]]
                               [:div {:class "form-control w-full sm:w-56"}
                                [:label {:class "label"}
                                 [:span {:class "label-text font-semibold"} "Updated Before"]]
                                [:input {:type "datetime-local"
                                         :class "input input-bordered input-sm"
                                         :value updated-before
                                         :on-change #(rf/dispatch [::events/update-todo-filter
                                                                   [:updated :before]
                                                                   (.. % -target -value)])}]]
                               [:div {:class "flex h-full items-end"}
                                [:button {:type "button"
                                          :class "btn btn-ghost btn-sm"
                                          :on-click #(rf/dispatch [::events/clear-todo-filters])
                                          :disabled filters-default?}
                                 "Clear Filters"]]]
              action-buttons [:div {:class "flex flex-wrap gap-3"}
                              [:button {:type "button"
                                        :class "btn btn-outline btn-sm"
                                        :on-click #(rf/dispatch [::events/fetch-todos])}
                               "Reload"]
                              [:button {:type "button"
                                        :class "btn btn-primary btn-sm"
                                        :on-click #(rf/dispatch [::events/open-add-todo-dialog])}
                               "Add Todo"]]
              pagination-controls [:div {:class "flex flex-wrap items-center justify-between gap-4"}
                                   [:div {:class "text-sm text-base-content/70"} summary]
                                   [:div {:class "flex flex-wrap items-center gap-3"}
                                    [:div {:class "form-control w-full sm:w-32"}
                                     [:label {:class "label"}
                                      [:span {:class "label-text font-semibold"} "Per Page"]]
                                     [:select {:class "select select-bordered select-sm"
                                               :value (str per-page)
                                               :on-change #(rf/dispatch [::events/set-todo-per-page
                                                                          (.. % -target -value)])}
                                      (for [option per-page-options]
                                        ^{:key option}
                                        [:option {:value (str option)} (str option)])]]
                                    [:div {:class "flex items-center gap-2"}
                                     [:button {:type "button"
                                               :class "btn btn-outline btn-sm"
                                               :disabled (not can-prev?)
                                               :on-click #(when can-prev?
                                                            (rf/dispatch [::events/set-todo-page prev-page]))}
                                      "Prev"]
                                     [:span {:class "text-sm font-medium"}
                                      (str "Page " page " of " total-pages)]
                                     [:button {:type "button"
                                               :class "btn btn-outline btn-sm"
                                               :disabled (not can-next?)
                                               :on-click #(when can-next?
                                                            (rf/dispatch [::events/set-todo-page next-page]))}
                                      "Next"]]]]]
          [:div {:class "space-y-6"}
           [:div {:class "space-y-4"}
            filter-controls
            action-buttons
            pagination-controls]
           (when status-section
             status-section)
           (when (pos? total)
             [:div {:class "rounded-2xl border border-base-200 bg-base-100 shadow-sm"}
              [:> rc-table {:columns columns
                            :data table-data
                            :rowKey "key"
                            :className "todo-table"
                            :tableLayout "auto"
                            :scroll #js {:x "max-content"}}]])
           (when (and (pos? total) (> total per-page))
             pagination-controls)])))))
