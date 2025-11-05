(ns acme.web.feature.app.events
  (:require
   [acme.web.db :as db]
   [re-frame.core :as rf]))

(rf/reg-event-db
 ::initialize
 (fn [_ _]
   db/default-db))
