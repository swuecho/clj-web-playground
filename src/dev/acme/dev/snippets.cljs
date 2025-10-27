(ns acme.dev.snippets
  (:require [acme.web.app :as app]
            [cljs.pprint :as pprint]
            [reagent.dom.client :as rdom]))

(def demo-form-data
  {:name "Ada Lovelace"
   :email "ada@analytical.engine"})

(defonce preview-root (atom nil))

(defn seed-demo! []
  (reset! app/form-data demo-form-data))

(defn seed-empty! []
  (app/reset-state!))

(defn- ensure-preview-container []
  (or (.getElementById js/document "form-preview")
      (let [node (.createElement js/document "div")]
        (set! (.-id node) "form-preview")
        (.appendChild (.-body js/document) node)
        node)))

(defn- render-preview []
  (let [container (ensure-preview-container)
        instance (or @preview-root
                     (reset! preview-root (rdom/create-root container)))]
    (rdom/render
     instance
     [:aside {:style {:margin-top "1rem"
                      :padding "0.75rem"
                      :border "1px dashed #999"
                      :max-width "280px"
                      :font-family "monospace"}}
      [:strong "Form snapshot"]
      [:pre (with-out-str (pprint/pprint @app/form-data))]
      [:div {:style {:margin-top "0.5rem"}}
       [:button {:on-click app/restart!} "Reset form"]]])
    nil))

(defn mount-preview! []
  (render-preview)
  (remove-watch app/form-data ::live-preview)
  (add-watch app/form-data ::live-preview
             (fn [_ _ _ _]
               (render-preview)))
  :mounted)

(defn unmount-preview! []
  (remove-watch app/form-data ::live-preview)
  (when-let [instance @preview-root]
    (rdom/unmount instance)
    (reset! preview-root nil))
  (when-let [container (.getElementById js/document "form-preview")]
    (.remove container))
  :unmounted)

(defn demo-session! []
  (seed-demo!)
  (mount-preview!))
