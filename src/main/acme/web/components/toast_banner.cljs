(ns acme.web.components.toast-banner
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
