(ns acme.web.components.add-user-dialog
  (:require
   [re-frame.core :as rf]
   [acme.web.events :as events]
   [acme.web.subs :as subs]))

(defn add-user-dialog []
  (let [visible? (rf/subscribe [::subs/add-user-visible?])
        name (rf/subscribe [::subs/add-user-name])
        age (rf/subscribe [::subs/add-user-age])
        submitting? (rf/subscribe [::subs/add-user-submitting?])
        errors (rf/subscribe [::subs/add-user-errors])]
    (fn []
      (when @visible?
        [:div {:class "modal modal-open"}
         [:div {:class "modal-box space-y-5"}
          [:div {:class "flex items-start justify-between"}
           [:h2 {:class "text-xl font-semibold"} "Add User"]
           [:button {:type "button"
                     :class "btn btn-sm btn-ghost"
                     :on-click #(rf/dispatch [::events/close-add-user-dialog])
                     :disabled @submitting?}
            "✕"]]
          [:div {:class "grid gap-4"}
           [:div {:class "form-control"}
            [:label {:class "label"}
             [:span {:class "label-text"} "Name"]]
            [:input {:type "text"
                     :value @name
                     :on-change #(rf/dispatch [::events/update-add-user-field :name (.. % -target -value)])
                     :class (str "input input-bordered "
                                 (when (get @errors :name) "input-error"))}]
            (when-let [name-error (get @errors :name)]
              [:span {:class "text-error text-sm"} name-error])]
           [:div {:class "form-control"}
            [:label {:class "label"}
             [:span {:class "label-text"} "Age"]]
            [:input {:type "number"
                     :min 0
                     :value @age
                     :on-change #(rf/dispatch [::events/update-add-user-field :age (.. % -target -value)])
                     :class (str "input input-bordered "
                                 (when (get @errors :age) "input-error"))}]
            (when-let [age-error (get @errors :age)]
              [:span {:class "text-error text-sm"} age-error])]]
          [:div {:class "modal-action"}
           [:button {:type "button"
                     :class "btn btn-ghost"
                     :on-click #(rf/dispatch [::events/close-add-user-dialog])
                     :disabled @submitting?}
            "Cancel"]
           [:button {:type "button"
                     :class (str "btn btn-primary "
                                 (when @submitting? "loading"))
                     :on-click #(rf/dispatch [::events/add-user])
                     :disabled @submitting?}
            (if @submitting? "Saving" "Save")]]]
         [:div {:class "modal-backdrop"}
          [:button {:type "button"
                    :class "btn"
                    :on-click #(rf/dispatch [::events/close-add-user-dialog])
                    :disabled @submitting?}
           "Close"]]]))))
