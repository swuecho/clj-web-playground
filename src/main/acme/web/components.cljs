(ns acme.web.components
  (:require
   [re-frame.core :as rf]
   [acme.web.events :as events]
   [acme.web.subs :as subs]))

(defn toast-banner []
  (let [toast (rf/subscribe [::subs/toast])]
    (fn []
      (when-let [{:keys [message variant]} (:current @toast)]
        (let [alert-class (case variant
                            :success "alert-success"
                            :error "alert-error"
                            "alert-info")]
          [:div {:class "toast toast-end toast-top z-[1200]"}
           [:div {:class (str "alert " alert-class " flex items-center gap-3")}
            [:span message]
            [:button {:type "button"
                      :class "btn btn-sm btn-ghost"
                      :on-click #(rf/dispatch [::events/dismiss-toast])}
             "Dismiss"]]])))))

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

(defn action-button
  [{:keys [label variant on-click disabled?]}]
  (let [variant-class (case variant
                        :primary "btn-primary"
                        :success "btn-success"
                        :danger "btn-error"
                        :ghost "btn-ghost"
                        :info "btn-info"
                        :warning "btn-warning"
                        "btn-neutral")]
    [:button {:type "button"
              :on-click on-click
              :disabled disabled?
              :class (str "btn btn-sm " variant-class)}
     label]))

(defn user-row-actions [uuid]
  (let [pending? (rf/subscribe [::subs/delete-pending? uuid])]
    (fn [uuid]
      [:div {:class "flex flex-wrap justify-end gap-2"}
       [action-button {:label "View"
                       :variant :ghost
                       :on-click #(js/console.log "view" uuid)}]
       [action-button {:label "Edit"
                       :variant :primary
                       :on-click #(rf/dispatch [::events/open-edit-user-dialog uuid])}]
       [action-button {:label (if @pending? "Deleting..." "Delete")
                       :variant :danger
                       :disabled? @pending?
                       :on-click #(when (and (not @pending?)
                                             (js/confirm "Delete this user?"))
                                    (rf/dispatch [::events/delete-user uuid]))}]])))

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
