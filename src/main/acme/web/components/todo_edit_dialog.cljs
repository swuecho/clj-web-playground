(ns acme.web.components.todo-edit-dialog
  (:require
   [re-frame.core :as rf]
   [acme.web.events :as events]
   [acme.web.subs :as subs]))

(defn todo-edit-dialog []
  (let [visible? (rf/subscribe [::subs/todo-edit-visible?])
        todo-id (rf/subscribe [::subs/todo-edit-id])
        title (rf/subscribe [::subs/todo-edit-title])
        completed? (rf/subscribe [::subs/todo-edit-completed?])
        submitting? (rf/subscribe [::subs/todo-edit-submitting?])
        errors (rf/subscribe [::subs/todo-edit-errors])]
    (fn []
      (when @visible?
        [:div {:class "modal modal-open"}
         [:div {:class "modal-box space-y-5"}
          [:div {:class "flex items-start justify-between"}
           [:div
            [:h2 {:class "text-xl font-semibold"} "Edit Todo"]
            [:p {:class "text-sm text-base-content/60"} (str "ID: " (or @todo-id "-"))]]
           [:button {:type "button"
                     :class "btn btn-sm btn-ghost"
                     :on-click #(rf/dispatch [::events/close-edit-todo-dialog])
                     :disabled @submitting?}
            "✕"]]
          [:div {:class "grid gap-4"}
           [:div {:class "form-control"}
            [:label {:class "label"}
             [:span {:class "label-text"} "Title"]]
            [:input {:type "text"
                     :value @title
                     :on-change #(rf/dispatch [::events/update-edit-todo-field :title (.. % -target -value)])
                     :class (str "input input-bordered "
                                 (when (get @errors :title) "input-error"))}]
            (when-let [title-error (get @errors :title)]
              [:span {:class "text-error text-sm"} title-error])]
           [:div {:class "form-control"}
            [:label {:class "label cursor-pointer justify-start gap-3"}
             [:span {:class "label-text"} "Completed?"]
             [:input {:type "checkbox"
                      :checked (boolean @completed?)
                      :on-change #(rf/dispatch [::events/update-edit-todo-field :completed? (.. % -target -checked)])
                      :class "toggle toggle-primary"}]]]]
          [:div {:class "modal-action"}
           [:button {:type "button"
                     :class "btn btn-ghost"
                     :on-click #(rf/dispatch [::events/close-edit-todo-dialog])
                     :disabled @submitting?}
            "Cancel"]
           [:button {:type "button"
                     :class (str "btn btn-primary "
                                 (when @submitting? "loading"))
                     :on-click #(rf/dispatch [::events/update-todo])
                     :disabled @submitting?}
            (if @submitting? "Saving" "Save changes")]]]
         [:div {:class "modal-backdrop"}
          [:button {:type "button"
                    :class "btn"
                    :on-click #(rf/dispatch [::events/close-edit-todo-dialog])
                    :disabled @submitting?}
           "Close"]]]))))
