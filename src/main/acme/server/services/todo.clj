(ns acme.server.services.todo
  (:require
   [acme.server.models.todo :as todo]))

(defn- normalize-instance [row]
  (when row
    (let [record (into {} (seq row))]
      (update record :completed #(if (nil? %) false (boolean %))))))

(defn list-todos []
  (todo/all))

(defn fetch-todo [id]
  (some-> (todo/fetch id) normalize-instance))

(defn create-todo! [{:keys [title completed]}]
  (normalize-instance (todo/create! {:title title
                                     :completed completed})))

(defn update-todo! [id changes]
  (some-> (todo/update! id changes) normalize-instance))

(defn delete-todo! [id]
  (todo/delete! id))

(comment 
  (in-ns 'acme.server.services.todo)
  )
(comment 
  (in-ns 'acme.server.services.todo)
  (require 'acme.server.services.todo)
  (list-todos)
  (todo/all)
  )