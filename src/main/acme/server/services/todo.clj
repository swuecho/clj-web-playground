(ns acme.server.services.todo
  (:require
   [acme.server.models.todo :as todo]))

(defn- instance->map [row]
  (some-> row (into {})))

(defn list-todos []
  (mapv instance->map (todo/all)))

(defn fetch-todo [id]
  (some-> (todo/fetch id) instance->map))

(defn create-todo! [{:keys [title completed]}]
  (instance->map (todo/create! {:title title
                                :completed completed})))

(defn update-todo! [id changes]
  (some-> (todo/update! id changes) instance->map))

(defn delete-todo! [id]
  (todo/delete! id))
