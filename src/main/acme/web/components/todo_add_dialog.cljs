(ns acme.web.components.todo-add-dialog
  (:require
   [re-frame.core :as rf]
   [acme.web.events :as events]
   [acme.web.subs :as subs]))

(defn todo-add-dialog []
  (let [visible? (rf/subscribe [::subs/todo-add-visible?])
        title (rf/subscribe [::subs/todo-add-title])
        completed? (rf/subscribe [::subs/todo-add-completed?])
        submitting? (rf/subscribe [::subs/todo-add-submitting?])
        errors (rf/subscribe [::subs/todo-add-errors])]
    (fn []
      (when @visible?
        [:div {:class "modal modal-open"}
         [:div {:class "modal-box space-y-5"}
          [:div {:class "flex items-start justify-between"}
           [:h2 {:class "text-xl font-semibold"} "Add Todo"]
           [:button {:type "button"
                     :class "btn btn-sm btn-ghost"
                     :on-click #(rf/dispatch [::events/close-add-todo-dialog])
                     :disabled @submitting?}
            "✕"]]
          [:div {:class "grid gap-4"}
           [:div {:class "form-control"}
            [:label {:class "label"}
             [:span {:class "label-text"} "Title"]]
            [:input {:type "text"
                     :value @title
                     :on-change #(rf/dispatch [::events/update-add-todo-field :title (.. % -target -value)])
                     :class (str "input input-bordered "
                                 (when (get @errors :title) "input-error"))}]
            (when-let [title-error (get @errors :title)]
              [:span {:class "text-error text-sm"} title-error])]
           [:div {:class "form-control"}
            [:label {:class "label cursor-pointer justify-start gap-3"}
             [:span {:class "label-text"} "Completed?"]
             [:input {:type "checkbox"
                      :checked (boolean @completed?)
                      :on-change #(rf/dispatch [::events/update-add-todo-field :completed? (.. % -target -checked)])
                      :class "toggle toggle-primary"}]]]]
          [:div {:class "modal-action"}
           [:button {:type "button"
                     :class "btn btn-ghost"
                     :on-click #(rf/dispatch [::events/close-add-todo-dialog])
                     :disabled @submitting?}
            "Cancel"]
           [:button {:type "button"
                     :class (str "btn btn-primary "
                                 (when @submitting? "loading"))
                     :on-click #(rf/dispatch [::events/add-todo])
                     :disabled @submitting?}
            (if @submitting? "Saving" "Save")]]]
         [:div {:class "modal-backdrop"}
          [:button {:type "button"
                    :class "btn"
                    :on-click #(rf/dispatch [::events/close-add-todo-dialog])
                    :disabled @submitting?}
           "Close"]]]))))
