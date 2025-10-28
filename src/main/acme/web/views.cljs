(ns acme.web.views
  (:require
   [re-frame.core :as rf]
   [reagent.core :as r]
   [re-com.core :as rc]
   [acme.web.events :as events]
   [acme.web.subs :as subs]
   [acme.web.components :as components]))

(defn users-table []
  (let [users (rf/subscribe [::subs/users])
        loading? (rf/subscribe [::subs/loading?])
        error (rf/subscribe [::subs/error])]
    (r/with-let [table-model (r/atom [])]
      (fn []
        (let [current-users (or @users [])]
          (reset! table-model current-users)
          [:div {:style {:max-width "960px"
                         :margin "0 auto"
                         :padding "1.5rem"}}
           [components/toast-banner]
           [components/add-user-dialog]
           [:h1 {:style {:margin-bottom "1rem"}} "Users"]
           (cond
             @loading? [:p "Loading users..."]
             @error [:p {:style {:color "#b91c1c"}} @error]
             (empty? current-users) [:p "No users found."]
             :else
             (let [row-height 44
                   columns [{:id :uuid
                             :header-label "UUID"
                             :width 240
                             :height row-height
                             :align :left
                             :row-label-fn (fn [{:keys [uuid]}]
                                             [:span {:style {:font-family "monospace"}}
                                              uuid])
                             :sort-by true}
                            {:id :name
                             :header-label "Name"
                             :width 210
                             :height row-height
                             :align :left
                             :row-label-fn (fn [{:keys [name]}]
                                             [:span name])
                             :sort-by true}
                            {:id :age
                             :header-label "Age"
                             :width 110
                             :height row-height
                             :align :right
                             :row-label-fn (fn [{:keys [age]}]
                                             [:span age])
                             :sort-by true}
                            {:id :actions
                             :header-label "Actions"
                             :width 240
                             :height row-height
                             :align :left
                             :row-label-fn (fn [{:keys [uuid]}]
                                             [components/user-row-actions uuid])}]]
               [rc/simple-v-table
                :model table-model
                :columns columns
                :row-height row-height
                :column-header-height row-height
                :table-padding 12
                :fixed-column-count 0
                ;; hide unused row-header scaffolding so it doesn't overlap the data table
                :parts {:left-section {:style {:display "none"}}
                        :top-left {:style {:display "none"}}
                        :row-headers {:style {:display "none"}}
                        :bottom-left {:style {:display "none"}}}
                :class "users-table"]))
           [:div {:style {:margin-top "1.5rem"}}
            [:button {:on-click #(rf/dispatch [::events/fetch-users])
                      :style {:background "#2563eb"
                              :color "white"
                              :border "none"
                              :padding "0.5rem 1rem"
                              :border-radius "0.375rem"
                              :cursor "pointer"}}
             "Reload"]
            [:button {:on-click #(rf/dispatch [::events/open-add-user-dialog])
                      :style {:margin-left "0.75rem"
                              :background "#16a34a"
                              :color "white"
                              :border "none"
                              :padding "0.5rem 1rem"
                              :border-radius "0.375rem"
                              :cursor "pointer"}}
             "Add User"]]])))))
(defn main-panel []
  [users-table])
