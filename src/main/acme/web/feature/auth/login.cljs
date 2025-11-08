(ns acme.web.feature.auth.login
  (:require
   [acme.web.feature.auth.events :as auth-events]
   [acme.web.feature.auth.subs :as auth-subs]
   [re-frame.core :as rf]
   [reagent.core :as r]))


(defn login-panel []
  (r/with-let [email (r/atom "")
               password (r/atom "")
               register-email (r/atom "")
               register-password (r/atom "")
               register-confirm (r/atom "")
               logging-in? (rf/subscribe [::auth-subs/logging-in?])
               auth-error (rf/subscribe [::auth-subs/error])
               auth-mode (rf/subscribe [::auth-subs/auth-mode])
               registering? (rf/subscribe [::auth-subs/registering?])
               register-error (rf/subscribe [::auth-subs/register-error])]
    (fn []
      (let [mode @auth-mode
            tab-class (fn [target]
                        (str "tab flex-1 " (when (= mode target) "tab-active")))
            set-mode! (fn [target]
                        (when (not= mode target)
                          (rf/dispatch [::auth-events/set-auth-mode target])
                          (when (= target :login)
                            (rf/dispatch [::auth-events/clear-register-state]))))]
        [:div {:class "min-h-screen flex items-center justify-center bg-base-200"}
         [:div {:class "w-full max-w-md rounded-lg bg-base-100 p-8 shadow-lg"}
          [:h2 {:class "mb-6 text-2xl font-semibold text-center text-base-content"}
           "Access Your Workspace"]
          [:div {:class "tabs tabs-boxed mb-6"}
           [:button {:type "button"
                     :class (tab-class :login)
                     :on-click #(set-mode! :login)}
            "Login"]
           [:button {:type "button"
                     :class (tab-class :register)
                     :on-click #(set-mode! :register)}
            "Register"]]
          (if (= mode :login)
            [:form {:class "space-y-5"}
             [:div {:class "form-control"}
              [:label {:class "label"}
               [:span {:class "label-text"} "Email"]]
              [:input {:type "email"
                       :placeholder "Email"
                       :class "input input-bordered w-full"
                       :value @email
                       :on-change #(reset! email (.. % -target -value))}]]
             [:div {:class "form-control"}
              [:label {:class "label"}
               [:span {:class "label-text"} "Password"]]
              [:input {:type "password"
                       :placeholder "Password"
                       :class "input input-bordered w-full"
                       :value @password
                       :on-change #(reset! password (.. % -target -value))}]]
             (when-let [err @auth-error]
               [:div {:class "alert alert-error text-sm"}
                [:span err]])
             [:div {:class "form-control"}
              [:button {:type "button"
                        :class (str "btn btn-primary w-full "
                                    (when @logging-in? "loading"))
                        :on-click #(rf/dispatch [::auth-events/login
                                                 {:email @email
                                                  :password @password}])
                        :disabled @logging-in?}
               (if @logging-in? "Signing in" "Login")]]]
            [:form {:class "space-y-4"}
             [:div {:class "form-control"}
              [:label {:class "label"}
               [:span {:class "label-text"} "Email"]]
              [:input {:type "email"
                       :placeholder "Email"
                       :class "input input-bordered w-full"
                       :value @register-email
                       :on-change #(reset! register-email (.. % -target -value))}]]
             [:div {:class "form-control"}
              [:label {:class "label"}
               [:span {:class "label-text"} "Password"]]
              [:input {:type "password"
                       :placeholder "Password"
                       :class "input input-bordered w-full"
                       :value @register-password
                       :on-change #(reset! register-password (.. % -target -value))}]]
             [:div {:class "form-control"}
              [:label {:class "label"}
               [:span {:class "label-text"} "Confirm Password"]]
              [:input {:type "password"
                       :placeholder "Confirm Password"
                       :class "input input-bordered w-full"
                       :value @register-confirm
                       :on-change #(reset! register-confirm (.. % -target -value))}]]
             (when-let [err @register-error]
               [:div {:class "alert alert-error text-sm"}
                [:span err]])
             [:div {:class "form-control"}
              [:button {:type "button"
                        :class (str "btn btn-primary w-full "
                                    (when @registering? "loading"))
                        :on-click #(rf/dispatch [::auth-events/register
                                                 {:email @register-email
                                                  :password @register-password
                                                  :confirm-password @register-confirm}])
                        :disabled @registering?}
               (if @registering? "Creating account" "Create Account")]]])]]))))
