(ns acme.web.components.user-table.user-row-actions
  (:require
   [acme.web.feature.users.events :as users-events]
   [acme.web.feature.users.subs :as users-subs]
   [acme.web.components.base.action-button :as action-button]
   [acme.web.components.base.icons :as icons]
   [re-frame.core :as rf]))

(defn user-row-actions [uuid]
  (let [pending? (rf/subscribe [::users-subs/delete-pending? uuid])]
    (fn [uuid]
      [:div {:class "flex flex-wrap justify-end gap-2"}
       [action-button/action-button {:label [icons/eye-icon]
                                     :variant :default
                                     :aria-label "View user"
                                     :title "View user"
                                     :on-click #(js/console.log "view" uuid)}]
       [action-button/action-button {:label [icons/edit-2-icon]
                                     :variant :default
                                     :aria-label "Edit user"
                                     :title "Edit user"
                                     :on-click #(rf/dispatch [::users-events/open-edit-user-dialog uuid])}]
       [action-button/action-button {:label [icons/delete-2-icon]
                                     :variant :default
                                     :aria-label (if @pending? "Deleting user" "Delete user")
                                     :title (if @pending? "Deleting user" "Delete user")
                                     :disabled? @pending?
                                     :on-click #(when (and (not @pending?)
                                                           (js/confirm "Delete this user?"))
                                                  (rf/dispatch [::users-events/delete-user uuid]))}]])))
