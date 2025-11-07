(ns acme.web.feature.auth.events
  (:require
    [re-frame.core :as rf]))

(rf/reg-event-db
 ::set-logged-in?
 (fn [db [_ {:keys [email password]}]]
   (-> db
       (assoc :isLoggedIn? true)
       (assoc :user {:password password
                     :username email}))))