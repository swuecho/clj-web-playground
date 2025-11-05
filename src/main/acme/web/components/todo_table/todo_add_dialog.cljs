(ns acme.web.components.todo-table.todo-add-dialog
  (:require
   [acme.web.feature.todos.events :as todo-events]
   [acme.web.feature.todos.subs :as todo-subs]
   [re-frame.core :as rf]))

(defn todo-add-dialog []
  (let [visible? (rf/subscribe [::todo-subs/todo-add-visible?])
        title (rf/subscribe [::todo-subs/todo-add-title])
        completed? (rf/subscribe [::todo-subs/todo-add-completed?])
        submitting? (rf/subscribe [::todo-subs/todo-add-submitting?])
        errors (rf/subscribe [::todo-subs/todo-add-errors])]
    (fn []
      (when @visible?
        [:div {:class "modal modal-open"}
         [:div {:class "modal-box space-y-5"}
          [:div {:class "flex items-start justify-between"}
           [:h2 {:class "text-xl font-semibold"} "Add Todo"]
           [:button {:type "button"
                     :class "btn btn-sm btn-ghost"
                     :on-click #(rf/dispatch [::todo-events/close-add-todo-dialog])
                     :disabled @submitting?}
            "✕"]]
          [:div {:class "space-y-4"}
           [:div {:class "grid gap-2 sm:grid-cols-[60px_1fr] sm:items-center"}
            [:label {:for "add-todo-title"
                     :class "text-sm font-medium text-base-content"}
             "Title"]
            [:div {:class "space-y-1"}
             [:input {:id "add-todo-title"
                      :type "text"
                      :value @title
                      :on-change #(rf/dispatch [::todo-events/update-add-todo-field :title (.. % -target -value)])
                      :class (str "input input-bordered w-full "
                                  (when (get @errors :title) "input-error"))}]
             (when-let [title-error (get @errors :title)]
               [:span {:class "text-error text-sm"} title-error])]]
           [:div {:class "grid gap-2 sm:grid-cols-[120px_auto] sm:items-center"}
            [:label {:for "add-todo-completed"
                     :class "text-sm font-medium text-base-content"}
             "Completed?"]
            [:div {:class "flex items-center"}
             [:input {:id "add-todo-completed"
                      :type "checkbox"
                      :checked (boolean @completed?)
                      :on-change #(rf/dispatch [::todo-events/update-add-todo-field :completed? (.. % -target -checked)])
                      :class "toggle toggle-primary"}]]]]
          [:div {:class "modal-action"}
           [:button {:type "button"
                     :class "btn btn-ghost"
                     :on-click #(rf/dispatch [::todo-events/close-add-todo-dialog])
                     :disabled @submitting?}
            "Cancel"]
           [:button {:type "button"
                     :class (str "btn btn-primary "
                                 (when @submitting? "loading"))
                     :on-click #(rf/dispatch [::todo-events/add-todo])
                     :disabled @submitting?}
            (if @submitting? "Saving" "Save")]]]
         [:div {:class "modal-backdrop"}
          [:button {:type "button"
                    :class "btn"
                    :on-click #(rf/dispatch [::todo-events/close-add-todo-dialog])
                    :disabled @submitting?}
           "Close"]]]))))
