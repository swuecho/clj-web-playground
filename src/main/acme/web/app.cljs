(ns acme.web.app
  (:require [replicant.dom :as rdom]))

(defn views []
  [:div
   [:section.todoapp
    [:header.header
     [:h1 "todos"]]]])

(defn render! []
  (rdom/render
   (js/document.getElementById "root")
   (views)))

(defn init []
  (render!))

(defn reload! []
  (render!))
