(ns acme.web.feature.users.components.edit-user-dialog
  (:require
   [acme.web.feature.users.events :as users-events]
   [acme.web.feature.users.subs :as users-subs]
   [re-frame.core :as rf]))

(defn edit-user-dialog []
  (let [visible? (rf/subscribe [::users-subs/edit-user-visible?])
        uuid (rf/subscribe [::users-subs/edit-user-uuid])
        name (rf/subscribe [::users-subs/edit-user-name])
        age (rf/subscribe [::users-subs/edit-user-age])
        email (rf/subscribe [::users-subs/edit-user-email])
        password (rf/subscribe [::users-subs/edit-user-password])
        submitting? (rf/subscribe [::users-subs/edit-user-submitting?])
        errors (rf/subscribe [::users-subs/edit-user-errors])]
    (fn []
      (when @visible?
        [:div {:class "modal modal-open"}
         [:div {:class "modal-box space-y-5"}
          [:div {:class "flex items-start justify-between"}
           [:div
            [:h2 {:class "text-xl font-semibold"} "Edit User"]
            [:p {:class "text-sm text-base-content/60"} (str "ID: " (or @uuid ""))]]
           [:button {:type "button"
                     :class "btn btn-sm btn-ghost"
                     :on-click #(rf/dispatch [::users-events/close-edit-user-dialog])
                     :disabled @submitting?}
            "✕"]]
          [:div {:class "grid gap-4"}
           [:div {:class "form-control"}
            [:label {:class "label"}
             [:span {:class "label-text"} "Name"]]
            [:input {:type "text"
                     :value @name
                     :on-change #(rf/dispatch [::users-events/update-edit-user-field :name (.. % -target -value)])
                     :class (str "input input-bordered "
                                 (when (get @errors :name) "input-error"))}]
            (when-let [name-error (get @errors :name)]
              [:span {:class "text-error text-sm"} name-error])]
           [:div {:class "form-control"}
            [:label {:class "label"}
             [:span {:class "label-text"} "Email"]]
            [:input {:type "email"
                     :value @email
                     :on-change #(rf/dispatch [::users-events/update-edit-user-field :email (.. % -target -value)])
                     :class (str "input input-bordered "
                                 (when (get @errors :email) "input-error"))}]
            (when-let [email-error (get @errors :email)]
              [:span {:class "text-error text-sm"} email-error])]
           [:div {:class "form-control"}
            [:label {:class "label"}
             [:span {:class "label-text"} "Age"]]
            [:input {:type "number"
                     :min 0
                     :value @age
                     :on-change #(rf/dispatch [::users-events/update-edit-user-field :age (.. % -target -value)])
                     :class (str "input input-bordered "
                                 (when (get @errors :age) "input-error"))}]
            (when-let [age-error (get @errors :age)]
              [:span {:class "text-error text-sm"} age-error])]
           [:div {:class "form-control"}
            [:label {:class "label"}
             [:span {:class "label-text"} "New Password"]
             [:span {:class "label-text-alt"} "Leave blank to keep current"]]
            [:input {:type "password"
                     :value @password
                     :on-change #(rf/dispatch [::users-events/update-edit-user-field :password (.. % -target -value)])
                     :class (str "input input-bordered "
                                 (when (get @errors :password) "input-error"))}]
            (when-let [password-error (get @errors :password)]
              [:span {:class "text-error text-sm"} password-error])]]
          [:div {:class "modal-action"}
           [:button {:type "button"
                     :class "btn btn-ghost"
                     :on-click #(rf/dispatch [::users-events/close-edit-user-dialog])
                     :disabled @submitting?}
            "Cancel"]
           [:button {:type "button"
                     :class (str "btn btn-primary "
                                 (when @submitting? "loading"))
                     :on-click #(rf/dispatch [::users-events/update-user])
                     :disabled @submitting?}
            (if @submitting? "Saving" "Save changes")]]]
         [:div {:class "modal-backdrop"}
          [:button {:type "button"
                    :class "btn"
                    :on-click #(rf/dispatch [::users-events/close-edit-user-dialog])
                    :disabled @submitting?}
           "Close"]]]))))
