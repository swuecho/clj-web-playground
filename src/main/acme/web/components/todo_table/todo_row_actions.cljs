(ns acme.web.components.todo-table.todo-row-actions
  (:require
   [re-frame.core :as rf]
   [acme.web.events :as events]
   [acme.web.subs :as subs]
   [acme.web.components.base.action-button :as action-button]
   [acme.web.components.base.icons :as icons]))

(defn todo-row-actions [todo-id]
  (let [id-str (some-> todo-id str)
        pending? (rf/subscribe [::subs/todo-delete-pending? id-str])]
    (fn [todo-id]
      (let [id-str (some-> todo-id str)]
        [:div {:class "flex flex-wrap justify-end gap-2"}
         [action-button/action-button {:label [icons/edit-2-icon]
                                       :variant :default
                                       :aria-label "Edit todo"
                                       :title "Edit todo"
                                       :on-click #(rf/dispatch [::events/open-edit-todo-dialog id-str])}]
         [action-button/action-button {:label [icons/delete-2-icon]
                                       :aria-label (if @pending? "Deleting..." "Delete")
                                       :variant :default
                                       :disabled? @pending?
                                       :on-click #(when (and (not @pending?)
                                                             (js/confirm "Delete this todo?"))
                                                    (rf/dispatch [::events/delete-todo id-str]))}]]))))
