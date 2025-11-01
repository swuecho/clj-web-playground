(ns acme.web.components.todo-table.controls
  (:require
   [re-frame.core :as rf]
   [acme.web.events :as events]))

(def per-page-options [10 25 50 100])

(defn filter-controls [filters]
  (let [{:keys [completed created updated]} filters
        created (or created {})
        updated (or updated {})
        completed-filter (or completed :all)
        created-after (or (:after created) "")
        created-before (or (:before created) "")
        updated-after (or (:after updated) "")
        updated-before (or (:before updated) "")
        filters-default? (and (= completed-filter :all)
                              (empty? created-after)
                              (empty? created-before)
                              (empty? updated-after)
                              (empty? updated-before))]
    [:div {:class "grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6"}
     [:div {:class "form-control w-full"}
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
     [:div {:class "form-control w-full"}
      [:label {:class "label"}
       [:span {:class "label-text font-semibold"} "Created After"]]
      [:input {:type "datetime-local"
               :class "input input-bordered input-sm"
               :value created-after
               :on-change #(rf/dispatch [::events/update-todo-filter
                                         [:created :after]
                                         (.. % -target -value)])}]]
     [:div {:class "form-control w-full"}
      [:label {:class "label"}
       [:span {:class "label-text font-semibold"} "Created Before"]]
      [:input {:type "datetime-local"
               :class "input input-bordered input-sm"
               :value created-before
               :on-change #(rf/dispatch [::events/update-todo-filter
                                         [:created :before]
                                         (.. % -target -value)])}]]
     [:div {:class "form-control w-full"}
      [:label {:class "label"}
       [:span {:class "label-text font-semibold"} "Updated After"]]
      [:input {:type "datetime-local"
               :class "input input-bordered input-sm"
               :value updated-after
               :on-change #(rf/dispatch [::events/update-todo-filter
                                         [:updated :after]
                                         (.. % -target -value)])}]]
     [:div {:class "form-control w-full"}
      [:label {:class "label"}
       [:span {:class "label-text font-semibold"} "Updated Before"]]
      [:input {:type "datetime-local"
               :class "input input-bordered input-sm"
               :value updated-before
               :on-change #(rf/dispatch [::events/update-todo-filter
                                         [:updated :before]
                                         (.. % -target -value)])}]]
     [:div {:class "flex items-end"}
      [:button {:type "button"
                :class "btn btn-ghost btn-sm w-full sm:w-auto"
                :on-click #(rf/dispatch [::events/clear-todo-filters])
                :disabled filters-default?}
       "Clear Filters"]]]))

(defn action-buttons []
  [:div {:class "flex flex-wrap gap-3"}
   [:button {:type "button"
             :class "btn btn-outline btn-sm"
             :on-click #(rf/dispatch [::events/fetch-todos])}
    "Reload"]
   [:button {:type "button"
             :class "btn btn-primary btn-sm"
             :on-click #(rf/dispatch [::events/open-add-todo-dialog])}
    "Add Todo"]])

(defn pagination-controls [{:keys [summary per-page page total-pages can-prev? can-next? prev-page next-page]}]
  [:div {:class "flex flex-wrap items-center justify-between gap-4"}
   [:div {:class "text-sm font-medium text-base-content/70"} summary]
   [:div {:class "flex flex-wrap items-center gap-3"}
    [:div {:class "form-control w-full sm:w-32"}
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
     [:span {:class "text-sm font-medium text-base-content/80"}
      (str page " of " total-pages)]
     [:button {:type "button"
               :class "btn btn-outline btn-sm"
               :disabled (not can-next?)
               :on-click #(when can-next?
                            (rf/dispatch [::events/set-todo-page next-page]))}
      "Next"]]]])
