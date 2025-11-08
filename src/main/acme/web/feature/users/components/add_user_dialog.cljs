(ns acme.web.feature.users.components.add-user-dialog
  (:require
   [acme.web.feature.users.events :as users-events]
   [acme.web.feature.users.subs :as users-subs]
   [re-frame.core :as rf]))

(defn add-user-dialog []
  (let [visible? (rf/subscribe [::users-subs/add-user-visible?])
        name (rf/subscribe [::users-subs/add-user-name])
        age (rf/subscribe [::users-subs/add-user-age])
        email (rf/subscribe [::users-subs/add-user-email])
        password (rf/subscribe [::users-subs/add-user-password])
        submitting? (rf/subscribe [::users-subs/add-user-submitting?])
        errors (rf/subscribe [::users-subs/add-user-errors])]
    (fn []
      (when @visible?
        [:div {:class "modal modal-open"}
         [:div {:class "modal-box space-y-5"}
          [:div {:class "flex items-start justify-between"}
           [:h2 {:class "text-xl font-semibold"} "Add User"]
           [:button {:type "button"
                     :class "btn btn-sm btn-ghost"
                     :on-click #(rf/dispatch [::users-events/close-add-user-dialog])
                     :disabled @submitting?}
            "✕"]]
          [:div {:class "space-y-4"}
           [:div {:class "grid gap-2 sm:grid-cols-[60px_1fr] sm:items-center"}
            [:label {:for "add-user-name"
                     :class "text-sm font-medium text-base-content"}
             "Name"]
            [:div {:class "space-y-1"}
             [:input {:id "add-user-name"
                      :type "text"
                      :value @name
                      :on-change #(rf/dispatch [::users-events/update-add-user-field :name (.. % -target -value)])
                      :class (str "input input-bordered w-full "
                                  (when (get @errors :name) "input-error"))}]
             (when-let [name-error (get @errors :name)]
               [:span {:class "text-error text-sm"} name-error])]]
           [:div {:class "grid gap-2 sm:grid-cols-[60px_1fr] sm:items-center"}
            [:label {:for "add-user-email"
                     :class "text-sm font-medium text-base-content"}
             "Email"]
            [:div {:class "space-y-1"}
             [:input {:id "add-user-email"
                      :type "email"
                      :value @email
                      :on-change #(rf/dispatch [::users-events/update-add-user-field :email (.. % -target -value)])
                      :class (str "input input-bordered w-full "
                                  (when (get @errors :email) "input-error"))}]
             (when-let [email-error (get @errors :email)]
               [:span {:class "text-error text-sm"} email-error])]]
           [:div {:class "grid gap-2 sm:grid-cols-[60px_1fr] sm:items-center"}
            [:label {:for "add-user-age"
                     :class "text-sm font-medium text-base-content"}
             "Age"]
            [:div {:class "space-y-1"}
             [:input {:id "add-user-age"
                      :type "number"
                      :min 0
                      :value @age
                      :on-change #(rf/dispatch [::users-events/update-add-user-field :age (.. % -target -value)])
                      :class (str "input input-bordered w-full "
                                  (when (get @errors :age) "input-error"))}]
             (when-let [age-error (get @errors :age)]
               [:span {:class "text-error text-sm"} age-error])]]
           [:div {:class "grid gap-2 sm:grid-cols-[60px_1fr] sm:items-center"}
            [:label {:for "add-user-password"
                     :class "text-sm font-medium text-base-content"}
             "Password"]
            [:div {:class "space-y-1"}
             [:input {:id "add-user-password"
                      :type "password"
                      :value @password
                      :on-change #(rf/dispatch [::users-events/update-add-user-field :password (.. % -target -value)])
                      :class (str "input input-bordered w-full "
                                  (when (get @errors :password) "input-error"))}]
             (when-let [password-error (get @errors :password)]
               [:span {:class "text-error text-sm"} password-error])]]]
          [:div {:class "modal-action"}
           [:button {:type "button"
                     :class "btn btn-ghost"
                     :on-click #(rf/dispatch [::users-events/close-add-user-dialog])
                     :disabled @submitting?}
            "Cancel"]
           [:button {:type "button"
                     :class (str "btn btn-primary "
                                 (when @submitting? "loading"))
                     :on-click #(rf/dispatch [::users-events/add-user])
                     :disabled @submitting?}
            (if @submitting? "Saving" "Save")]]]
         [:div {:class "modal-backdrop"}
          [:button {:type "button"
                    :class "btn"
                    :on-click #(rf/dispatch [::users-events/close-add-user-dialog])
                    :disabled @submitting?}
           "Close"]]]))))
