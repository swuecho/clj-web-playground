(ns acme.web.feature.auth.login
  (:require
   [acme.web.feature.auth.events :as auth-events]
   [re-frame.core :as rf]))


(defn login-panel []
  [:div {:class "min-h-screen flex items-center justify-center bg-base-200"}
   [:div {:class "w-full max-w-md rounded-lg bg-base-100 p-8 shadow-lg"}
    [:h2 {:class "mb-6 text-2xl font-semibold text-center text-base-content"} "Login to Your Account"]
    [:form {:class "space-y-5"}
     [:div {:class "form-control"}
      [:label {:class "label"}
       [:span {:class "label-text"} "Email"]]
      [:input {:type "email"
               :placeholder "Email"
               :class "input input-bordered w-full"}]]
     [:div {:class "form-control"}
      [:label {:class "label"}
       [:span {:class "label-text"} "Password"]]
      [:input {:type "password"
               :placeholder "Password"
               :class "input input-bordered w-full"}]]
     [:div {:class "form-control"}
      [:button {:type "button"
                :class "btn btn-primary w-full"
                :on-click #(rf/dispatch [::auth-events/set-logged-in? {:email "demo@a.com" :password "demopass"}])}
       "Login"]]]]])

