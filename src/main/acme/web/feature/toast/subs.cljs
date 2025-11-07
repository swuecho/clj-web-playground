(ns acme.web.feature.toast.subs
  (:require
   [re-frame.core :as rf]))

(rf/reg-sub
 ::toast
 (fn [db]
   (:toast db)))
