(ns acme.web.feature.auth.subs
  (:require
   [re-frame.core :as rf]))

(rf/reg-sub
 ::is-logged-in?
 (fn [db]
   (:isLoggedIn? db)))

(rf/reg-sub
 ::user
 (fn [db]
   (:user db)))
