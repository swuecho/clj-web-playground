(ns acme.web.app
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdom]))

(defonce form-data (r/atom {:name ""
                            :email ""}))

(defn handle-submit [event]
  (.preventDefault event)
  (js/console.log "Submitting form" (clj->js @form-data)))

(defn text-input [id label field]
  [:div
   [:label {:for id} label]
   [:input {:id id
            :value (get @form-data field)
            :on-change #(swap! form-data assoc field (.. % -target -value))}]])

(defn form-view []
  [:form {:on-submit handle-submit}
   [text-input "name" "Name" :name]
   [text-input "email" "Email" :email]
   [:button {:type "submit"} "Submit"]])

(defonce root-instance (atom nil))

(defn mount-root []
  (when-let [container (.getElementById js/document "root")]
    (let [instance (or @root-instance
                       (reset! root-instance (rdom/create-root container)))]
      (rdom/render instance [form-view]))))

(defn ^:export init []
  (mount-root))

(defn reload! []
  (mount-root))
