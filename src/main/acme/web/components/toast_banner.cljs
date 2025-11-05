(ns acme.web.components.toast-banner
  (:require
   [acme.web.feature.toast.events :as toast-events]
   [acme.web.feature.toast.subs :as toast-subs]
   [re-frame.core :as rf]))

(defn toast-banner []
  (let [toast (rf/subscribe [::toast-subs/toast])]
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
                      :on-click #(rf/dispatch [::toast-events/dismiss-toast])}
             "Dismiss"]]])))))
