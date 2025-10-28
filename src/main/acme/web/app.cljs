(ns acme.web.app
  (:require
   [re-frame.core :as rf]
   [reagent.dom :as rdom]
   [acme.web.events :as events]
   [acme.web.subs]
   [acme.web.views :as views]))

(defn mount-root []
  (rf/clear-subscription-cache!)
  (when-let [el (.getElementById js/document "root")]
    (rdom/render [views/main-panel] el)))

(defn init []
  (rf/dispatch-sync [::events/initialize])
  (rf/dispatch [::events/fetch-users])
  (mount-root))

(defn ^:dev/after-load reload! []
  (mount-root))
