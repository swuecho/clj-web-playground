(ns acme.web.feature.auth.login
  (:require
   [acme.web.feature.auth.events :as auth-events]
   [acme.web.feature.auth.subs :as auth-subs]
   [re-frame.core :as rf]
   [reagent.core :as r]))


(defn login-panel []
  (r/with-let [email (r/atom "")
               password (r/atom "")
               logging-in? (rf/subscribe [::auth-subs/logging-in?])
               auth-error (rf/subscribe [::auth-subs/error])]
    (fn []
      [:div {:class "min-h-screen flex items-center justify-center bg-base-200"}
       [:div {:class "w-full max-w-md rounded-lg bg-base-100 p-8 shadow-lg"}
        [:h2 {:class "mb-6 text-2xl font-semibold text-center text-base-content"} "Login to Your Account"]
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
           (if @logging-in? "Signing in" "Login")]]]]])))
