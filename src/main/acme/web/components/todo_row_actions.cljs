(ns acme.web.components.todo-row-actions
  (:require
   [re-frame.core :as rf]
   [acme.web.events :as events]
   [acme.web.subs :as subs]
   [acme.web.components.action-button :as action-button]))

(defn todo-row-actions [todo-id]
  (let [id-str (some-> todo-id str)
        pending? (rf/subscribe [::subs/todo-delete-pending? id-str])]
    (fn [todo-id]
      (let [id-str (some-> todo-id str)]
        [:div {:class "flex flex-wrap justify-end gap-2"}
         [action-button/action-button {:label "Edit"
                                       :variant :primary
                                       :on-click #(rf/dispatch [::events/open-edit-todo-dialog todo-id])}]
         [action-button/action-button {:label (if @pending? "Deleting..." "Delete")
                                       :variant :danger
                                       :disabled? @pending?
                                       :on-click #(when (and (not @pending?)
                                                             (js/confirm "Delete this todo?"))
                                                    (rf/dispatch [::events/delete-todo todo-id]))}]]))))
