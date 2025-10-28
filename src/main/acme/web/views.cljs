(ns acme.web.views
  (:require
   [re-frame.core :as rf]
   [acme.web.events :as events]
   [acme.web.subs :as subs]
   [acme.web.components :as components]))

(defn users-table []
  (let [users (rf/subscribe [::subs/users])
        loading? (rf/subscribe [::subs/loading?])
        error (rf/subscribe [::subs/error])]
    (fn []
      [:div {:style {:max-width "960px"
                     :margin "0 auto"
                     :padding "1.5rem"}}
       [components/toast-banner]
       [components/add-user-dialog]
       [:h1 {:style {:margin-bottom "1rem"}} "Users"]
       (cond
         @loading? [:p "Loading users..."]
         @error [:p {:style {:color "#b91c1c"}} @error]
         (empty? @users) [:p "No users found."]
         :else
         [:table {:style {:width "100%"
                          :border-collapse "collapse"}}
          [:thead
           [:tr
            [:th {:style {:text-align "left"
                          :border-bottom "1px solid #d1d5db"
                          :padding "0.5rem"}} "UUID"]
            [:th {:style {:text-align "left"
                          :border-bottom "1px solid #d1d5db"
                          :padding "0.5rem"}} "Name"]
            [:th {:style {:text-align "left"
                          :border-bottom "1px solid #d1d5db"
                          :padding "0.5rem"}} "Age"]
            [:th {:style {:text-align "left"
                          :border-bottom "1px solid #d1d5db"
                          :padding "0.5rem"}} "Actions"]]]
          [:tbody
           (for [{:keys [uuid name age]} @users]
             ^{:key uuid}
             [:tr
              [:td {:style {:border-bottom "1px solid #e5e7eb"
                            :padding "0.5rem"
                            :font-family "monospace"}} uuid]
              [:td {:style {:border-bottom "1px solid #e5e7eb"
                            :padding "0.5rem"}} name]
              [:td {:style {:border-bottom "1px solid #e5e7eb"
                            :padding "0.5rem"}} age]
              [:td {:style {:border-bottom "1px solid #e5e7eb"
                            :padding "0.5rem"}}
               [:div {:style {:display "flex"
                               :gap "0.375rem"}}
                [components/action-button {:label "View"
                                            :color "#6b7280"
                                            :on-click #(.log js/console "view" uuid)}]
                [components/action-button {:label "Edit"
                                            :color "#2563eb"
                                            :on-click #(.log js/console "edit" uuid)}]
                [components/action-button {:label "Delete"
                                            :color "#dc2626"
                                            :on-click #(.log js/console "delete" uuid)}]]]])]])
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
         "Add User"]]])))

(defn main-panel []
  [users-table])
