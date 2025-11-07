(ns acme.web.feature.auth.events
  (:require
   [acme.web.feature.todos.events :as todo-events]
   [acme.web.feature.users.events :as user-events]
   [re-frame.core :as rf]))

(rf/reg-event-fx
 ::set-logged-in?
 (fn [{:keys [db]} [_ {:keys [email password]}]]
   {:db (-> db
            (assoc :isLoggedIn? true)
            (assoc :user {:email email
                          :password password
                          :username email}))
    :dispatch-n [[::user-events/fetch-users]
                 [::todo-events/fetch-todos]]}))
