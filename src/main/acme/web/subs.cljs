(ns acme.web.subs
  (:require
   [re-frame.core :as rf]))

(rf/reg-sub
 ::users
 (fn [db]
   (:users db)))

(rf/reg-sub
 ::loading?
 (fn [db]
   (:loading? db)))

(rf/reg-sub
 ::error
 (fn [db]
   (:error db)))

(rf/reg-sub
 ::add-user
 (fn [db]
   (:add-user db)))

(rf/reg-sub
 ::add-user-visible?
 (fn [db]
   (get-in db [:add-user :visible?])))

(rf/reg-sub
 ::add-user-name
 (fn [db]
   (get-in db [:add-user :name])))

(rf/reg-sub
 ::add-user-age
 (fn [db]
   (get-in db [:add-user :age])))

(rf/reg-sub
 ::add-user-submitting?
 (fn [db]
   (get-in db [:add-user :submitting?])))

(rf/reg-sub
 ::add-user-errors
 (fn [db]
   (get-in db [:add-user :errors])))

(rf/reg-sub
 ::toast
 (fn [db]
  (:toast db)))

(rf/reg-sub
 ::edit-user
 (fn [db]
   (:edit-user db)))

(rf/reg-sub
 ::edit-user-visible?
 (fn [db]
   (get-in db [:edit-user :visible?])))

(rf/reg-sub
 ::edit-user-uuid
 (fn [db]
   (get-in db [:edit-user :uuid])))

(rf/reg-sub
 ::edit-user-name
 (fn [db]
   (get-in db [:edit-user :name])))

(rf/reg-sub
 ::edit-user-age
 (fn [db]
   (get-in db [:edit-user :age])))

(rf/reg-sub
 ::edit-user-submitting?
 (fn [db]
   (get-in db [:edit-user :submitting?])))

(rf/reg-sub
 ::edit-user-errors
 (fn [db]
   (get-in db [:edit-user :errors])))

(rf/reg-sub
 ::pending-deletes
 (fn [db]
   (:pending-deletes db)))

(rf/reg-sub
 ::delete-pending?
 :<- [::pending-deletes]
 (fn [pending [_ uuid]]
   (contains? pending uuid)))
