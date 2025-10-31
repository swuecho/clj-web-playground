(ns acme.web.components.user-row-actions
  (:require
   [re-frame.core :as rf]
   [acme.web.events :as events]
   [acme.web.subs :as subs]
   [acme.web.components.action-button :as action-button]))

(defn user-row-actions [uuid]
  (let [pending? (rf/subscribe [::subs/delete-pending? uuid])]
    (fn [uuid]
      [:div {:class "flex flex-wrap justify-end gap-2"}
       [action-button/action-button {:label "View"
                                     :variant :ghost
                                     :on-click #(js/console.log "view" uuid)}]
       [action-button/action-button {:label "Edit"
                                     :variant :primary
                                     :on-click #(rf/dispatch [::events/open-edit-user-dialog uuid])}]
       [action-button/action-button {:label (if @pending? "Deleting..." "Delete")
                                     :variant :danger
                                     :disabled? @pending?
                                     :on-click #(when (and (not @pending?)
                                                           (js/confirm "Delete this user?"))
                                                  (rf/dispatch [::events/delete-user uuid]))}]])))
