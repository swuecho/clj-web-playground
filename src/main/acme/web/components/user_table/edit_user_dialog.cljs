(ns acme.web.components.user-table.edit-user-dialog
  (:require
   [re-frame.core :as rf]
   [acme.web.events :as events]
   [acme.web.subs :as subs]))

(defn edit-user-dialog []
  (let [visible? (rf/subscribe [::subs/edit-user-visible?])
        uuid (rf/subscribe [::subs/edit-user-uuid])
        name (rf/subscribe [::subs/edit-user-name])
        age (rf/subscribe [::subs/edit-user-age])
        submitting? (rf/subscribe [::subs/edit-user-submitting?])
        errors (rf/subscribe [::subs/edit-user-errors])]
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
                     :on-click #(rf/dispatch [::events/close-edit-user-dialog])
                     :disabled @submitting?}
            "✕"]]
          [:div {:class "grid gap-4"}
           [:div {:class "form-control"}
            [:label {:class "label"}
             [:span {:class "label-text"} "Name"]]
            [:input {:type "text"
                     :value @name
                     :on-change #(rf/dispatch [::events/update-edit-user-field :name (.. % -target -value)])
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
                     :on-change #(rf/dispatch [::events/update-edit-user-field :age (.. % -target -value)])
                     :class (str "input input-bordered "
                                 (when (get @errors :age) "input-error"))}]
            (when-let [age-error (get @errors :age)]
              [:span {:class "text-error text-sm"} age-error])]]
          [:div {:class "modal-action"}
           [:button {:type "button"
                     :class "btn btn-ghost"
                     :on-click #(rf/dispatch [::events/close-edit-user-dialog])
                     :disabled @submitting?}
            "Cancel"]
           [:button {:type "button"
                     :class (str "btn btn-primary "
                                 (when @submitting? "loading"))
                     :on-click #(rf/dispatch [::events/update-user])
                     :disabled @submitting?}
            (if @submitting? "Saving" "Save changes")]]]
         [:div {:class "modal-backdrop"}
          [:button {:type "button"
                    :class "btn"
                    :on-click #(rf/dispatch [::events/close-edit-user-dialog])
                    :disabled @submitting?}
           "Close"]]]))))
