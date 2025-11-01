(ns acme.web.components.user-table.user-row-actions
  (:require
   [re-frame.core :as rf]
   [acme.web.events :as events]
   [acme.web.subs :as subs]
   [acme.web.components.base.action-button :as action-button]
   [acme.web.components.base.icons :as icons]))

(defn user-row-actions [uuid]
  (let [pending? (rf/subscribe [::subs/delete-pending? uuid])]
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
                                     :on-click #(rf/dispatch [::events/open-edit-user-dialog uuid])}]
       [action-button/action-button {:label [icons/delete-2-icon]
                                     :variant :default
                                     :aria-label (if @pending? "Deleting user" "Delete user")
                                     :title (if @pending? "Deleting user" "Delete user")
                                     :disabled? @pending?
                                     :on-click #(when (and (not @pending?)
                                                           (js/confirm "Delete this user?"))
                                                  (rf/dispatch [::events/delete-user uuid]))}]])))
