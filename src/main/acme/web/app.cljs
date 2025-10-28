(ns acme.web.app
  (:require
   [re-frame.core :as rf]
   [reagent.dom.client :as rdom]
   [acme.web.events :as events]
   [acme.web.subs]
   [acme.web.views :as views]))

(defonce root* (atom nil))

(defn mount-root []
  (rf/clear-subscription-cache!)
  (when-let [el (.getElementById js/document "root")]
    (let [root (or @root*
                   (reset! root* (rdom/create-root el)))]
      (rdom/render root [views/main-panel]))))

(defn init []
  (rf/dispatch-sync [::events/initialize])
  (rf/dispatch [::events/fetch-users])
  (mount-root))

(defn ^:dev/after-load reload! []
  (mount-root))
