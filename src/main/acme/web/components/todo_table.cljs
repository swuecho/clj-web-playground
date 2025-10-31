(ns acme.web.components.todo-table
  (:require
   [re-frame.core :as rf]
   [acme.web.events :as events]
   [acme.web.subs :as subs]
   [acme.web.components.todo-table.table :as table]
   [acme.web.components.todo-table.controls :as controls]
   ["@rc-component/table" :default rc-table]))

(defn todo-table []
  (let [loading? (rf/subscribe [::subs/todos-loading?])
        error (rf/subscribe [::subs/todos-error])
        sort-config (rf/subscribe [::subs/todo-sort])
        filters-config (rf/subscribe [::subs/todo-filters])
        pagination-config (rf/subscribe [::subs/todo-pagination])]
    (fn []
      (let [is-loading? @loading?
            error-message @error
            {:keys [field direction]} (or @sort-config {})
            filters (or @filters-config {})
            {:keys [items page per-page total total-pages start end]}
            (merge {:items [] :page 1 :per-page 25 :total 0 :total-pages 1 :start 0 :end 0}
                   (or @pagination-config {}))
            field (or field :created_at)
            direction (or direction :desc)
            dispatch-sort (fn [target-field target-direction]
                            (rf/dispatch [::events/set-todo-sort {:field target-field
                                                                  :direction target-direction}]))
            columns (table/build-columns {:field field
                                          :direction direction
                                          :dispatch-sort dispatch-sort})
            table-data (table/format-rows items)
            status-section (cond
                             is-loading? [:div {:class "alert alert-info"}
                                          [:span "Loading todos..."]]
                             error-message [:div {:class "alert alert-error"}
                                            [:span error-message]]
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
            pagination-props {:summary summary
                              :per-page per-page
                              :page page
                              :total-pages total-pages
                              :can-prev? can-prev?
                              :can-next? can-next?
                              :prev-page prev-page
                              :next-page next-page}
            header-subtext (cond
                             is-loading? "Fetching the latest todos…"
                             error-message "We hit a snag retrieving your todos. Try reloading."
                             (pos? total) summary
                             :else "Keep track of every task and stay ahead of your work.")]
        [:div {:class "space-y-6"}
         [:section {:class "rounded-sm border border-base-200 bg-gradient-to-r from-base-100 via-base-100 to-base-200/70 p-6 shadow-md"}
          [:div {:class "flex flex-wrap items-center justify-between gap-4"}
           [:div {:class "space-y-1"}
            [:h2 {:class "text-2xl font-semibold text-base-content"} "Todo Overview"]
            [:p {:class "text-sm text-base-content/70"} header-subtext]]
           [controls/action-buttons]]
          [:div {:class "mt-6"}
           [controls/filter-controls filters]]]
         (when status-section
           [:div {:class "rounded-sm border border-base-200 bg-base-100/95 p-5 shadow-sm"}
            status-section])
         (when (pos? total)
           [:div {:class "rounded-sm border border-base-200 bg-base-100 shadow-md overflow-hidden"}
            [:> rc-table {:columns columns
                          :data table-data
                          :rowKey "key"
                          :className "todo-table text-sm"
                          :rowClassName (fn [_ _]
                                          "odd:bg-base-100 even:bg-base-200 transition-colors hover:bg-primary/5 last:[&_td]:border-b-0")
                          :tableLayout "auto"
                          :scroll #js {:x "max-content"}}]
            [:div {:class "border-t border-base-200 bg-base-100/95 px-5 py-4"}
             [controls/pagination-controls pagination-props]]])]))))
