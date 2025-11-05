(ns acme.web.app
  (:require
   [acme.web.feature.app.events :as app-events]
   [acme.web.feature.toast.events]
   [acme.web.feature.toast.subs]
   [acme.web.feature.todos.events :as todo-events]
   [acme.web.feature.todos.subs]
   [acme.web.feature.users.events :as user-events]
   [acme.web.feature.users.subs]
   [acme.web.views :as views]
   [re-frame.core :as rf]
   [reagent.dom.client :as rdom]))

(defonce root* (atom nil))

(defn mount-root []
  (rf/clear-subscription-cache!)
  (when-let [el (.getElementById js/document "root")]
    (let [root (or @root*
                   (reset! root* (rdom/create-root el)))]
      (rdom/render root [views/main-panel]))))

(defn init []
  (rf/dispatch-sync [::app-events/initialize])
  (rf/dispatch [::user-events/fetch-users])
  (rf/dispatch [::todo-events/fetch-todos])
  (mount-root))

(defn ^:dev/after-load reload! []
  (mount-root))
