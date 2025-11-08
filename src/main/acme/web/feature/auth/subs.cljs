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

(rf/reg-sub
 ::token
 (fn [db]
   (get-in db [:auth :token])))

(rf/reg-sub
 ::logging-in?
 (fn [db]
   (get-in db [:auth :logging-in?])))

(rf/reg-sub
 ::error
 (fn [db]
   (get-in db [:auth :error])))
