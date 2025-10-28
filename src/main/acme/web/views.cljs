(ns acme.web.views
  (:require
   [re-frame.core :as rf]
   [acme.web.events :as events]
   [acme.web.subs :as subs]))

(defn toast-banner []
  (let [toast (rf/subscribe [::subs/toast])]
    (fn []
      (when-let [{:keys [message variant]} (:current @toast)]
        (let [{:keys [background shadow]}
              (case variant
                :success {:background "#16a34a"
                          :shadow "0 10px 30px rgba(22, 163, 74, 0.35)"}
                :error {:background "#dc2626"
                        :shadow "0 10px 30px rgba(220, 38, 38, 0.35)"}
                {:background "#2563eb"
                 :shadow "0 10px 30px rgba(37, 99, 235, 0.35)"})]
          [:div {:style {:position "fixed"
                         :top "1.5rem"
                         :right "1.5rem"
                         :background background
                         :color "white"
                         :padding "0.75rem 1rem"
                         :border-radius "0.5rem"
                         :box-shadow shadow
                         :display "flex"
                         :align-items "center"
                         :gap "0.75rem"
                         :z-index 1100}}
           [:span message]
           [:button {:on-click #(rf/dispatch [::events/dismiss-toast])
                     :style {:background "rgba(255,255,255,0.2)"
                             :border "none"
                             :color "white"
                             :padding "0.25rem 0.5rem"
                             :border-radius "0.375rem"
                             :cursor "pointer"}}
            "×"]])))))

(defn add-user-dialog []
  (let [visible? (rf/subscribe [::subs/add-user-visible?])
        name (rf/subscribe [::subs/add-user-name])
        age (rf/subscribe [::subs/add-user-age])
        submitting? (rf/subscribe [::subs/add-user-submitting?])
        errors (rf/subscribe [::subs/add-user-errors])]
    (fn []
      (when @visible?
        [:div {:style {:position "fixed"
                       :top 0
                       :left 0
                       :width "100vw"
                       :height "100vh"
                       :background "rgba(15, 23, 42, 0.5)"
                       :display "flex"
                       :align-items "center"
                       :justify-content "center"
                       :z-index 1000}}
         [:div {:style {:background "white"
                         :border-radius "0.75rem"
                         :box-shadow "0 10px 40px rgba(15, 23, 42, 0.15)"
                         :padding "1.5rem"
                         :width "min(400px, 90vw)"}}
          [:h2 {:style {:margin-bottom "1rem"
                        :font-size "1.25rem"}} "Add User"]
          [:div {:style {:display "flex"
                         :flex-direction "column"
                         :gap "0.75rem"}}
           [:label {:style {:display "flex"
                             :flex-direction "column"
                             :gap "0.25rem"}}
            [:span "Name"]
            [:input {:type "text"
                     :value @name
                     :on-change #(rf/dispatch [::events/update-add-user-field :name (.. % -target -value)])
                     :style {:padding "0.5rem"
                             :border (if (get @errors :name) "1px solid #dc2626" "1px solid #d1d5db")
                             :border-radius "0.375rem"}}]
            (when-let [name-error (get @errors :name)]
              [:span {:style {:color "#dc2626"
                              :font-size "0.875rem"}}
               name-error])]
           [:label {:style {:display "flex"
                             :flex-direction "column"
                             :gap "0.25rem"}}
            [:span "Age"]
            [:input {:type "number"
                     :min 0
                     :value @age
                     :on-change #(rf/dispatch [::events/update-add-user-field :age (.. % -target -value)])
                     :style {:padding "0.5rem"
                             :border (if (get @errors :age) "1px solid #dc2626" "1px solid #d1d5db")
                             :border-radius "0.375rem"}}]
            (when-let [age-error (get @errors :age)]
              [:span {:style {:color "#dc2626"
                              :font-size "0.875rem"}}
               age-error])]]
          [:div {:style {:display "flex"
                         :justify-content "flex-end"
                         :gap "0.75rem"
                         :margin-top "1.5rem"}}
           [:button {:on-click #(rf/dispatch [::events/close-add-user-dialog])
                     :disabled @submitting?
                     :style {:background "transparent"
                             :border "none"
                             :color "#4b5563"
                             :padding "0.5rem 1rem"
                             :cursor (if @submitting? "not-allowed" "pointer")}}
            "Cancel"]
           [:button {:on-click #(rf/dispatch [::events/add-user])
                     :disabled @submitting?
                     :style {:background (if @submitting? "#86efac" "#16a34a")
                             :color "white"
                             :border "none"
                             :padding "0.5rem 1rem"
                             :border-radius "0.375rem"
                             :cursor (if @submitting? "not-allowed" "pointer")}}
            (if @submitting? "Saving..." "Save")]]]]))))

(defn users-table []
  (let [users (rf/subscribe [::subs/users])
        loading? (rf/subscribe [::subs/loading?])
        error (rf/subscribe [::subs/error])]
    (fn []
      [:div {:style {:max-width "960px"
                     :margin "0 auto"
                     :padding "1.5rem"}}
       [toast-banner]
       [add-user-dialog]
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
                          :padding "0.5rem"}} "Age"]]]
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
                            :padding "0.5rem"}} age]])]])
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
