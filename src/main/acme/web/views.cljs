(ns acme.web.views
  (:require
   [re-frame.core :as rf]
   [acme.web.events :as events]
   [acme.web.subs :as subs]
   [acme.web.components :as components]))

(defn users-table []
  (let [users (rf/subscribe [::subs/users])
        loading? (rf/subscribe [::subs/loading?])
        error (rf/subscribe [::subs/error])
        container-style {:max-width "960px"
                         :margin "0 auto"
                         :padding "1.5rem"}
        table-style {:width "100%"
                     :border-collapse "separate"
                     :border-spacing 0
                     :table-layout "auto"}
        header-style {:text-align "left"
                      :border-bottom "1px solid #d1d5db"
                      :padding "0.75rem 0.5rem"
                      :font-weight 600
                      :white-space "nowrap"}
        cell-style {:border-bottom "1px solid #e5e7eb"
                    :padding "0.75rem 0.5rem"
                    :vertical-align "middle"
                    :line-height 1.4}
        uuid-cell-style {:font-family "monospace"
                         :word-break "break-all"}
        action-cell-style {:padding-right 0}
        empty-state-style {:margin "2rem 0"
                           :color "#64748b"}]
    (fn []
      [:div {:style container-style}
       [components/toast-banner]
       [components/add-user-dialog]
       [:h1 {:style {:margin-bottom "1rem"}} "Users"]
       (cond
         @loading? [:p "Loading users..."]
         @error [:p {:style {:color "#b91c1c"}} @error]
         (empty? @users) [:p {:style empty-state-style} "No users found."]
         :else
         [:table {:style table-style}
          [:thead
           [:tr
            [:th {:style header-style} "UUID"]
            [:th {:style header-style} "Name"]
            [:th {:style header-style} "Age"]
            [:th {:style (merge header-style {:text-align "right"})} "Actions"]]]
          [:tbody
           (for [{:keys [uuid name age]} @users]
             ^{:key uuid}
             [:tr
              [:td {:style (merge cell-style uuid-cell-style)} uuid]
              [:td {:style cell-style} name]
              [:td {:style cell-style} age]
              [:td {:style (merge cell-style action-cell-style {:text-align "right"})}
               [:div {:style {:display "flex"
                              :justify-content "flex-end"
                              :flex-wrap "wrap"
                              :gap "0.5rem"}}
                [components/user-row-actions uuid]]]])]])
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
