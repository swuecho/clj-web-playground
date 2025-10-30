(ns acme.server.handlers.todos
  (:require
   [clojure.string :as str]
   [acme.server.http :as http]
   [acme.server.services.todo :as todo.service]))

(defn list-response [_]
  (http/respond-json (todo.service/list-todos)))

(defn create-response [{:keys [parameters]}]
  (let [{:keys [title completed]} (:body parameters)
        title (some-> title str/trim)
        payload (cond-> {:title title}
                  (some? completed) (assoc :completed completed))]
    (http/respond-json (todo.service/create-todo! payload) 201)))

(defn fetch-response [{:keys [parameters]}]
  (let [id (get-in parameters [:path :id])]
    (if-let [record (todo.service/fetch-todo id)]
      (http/respond-json record)
      (http/not-found nil))))

(defn update-response [{:keys [parameters]}]
  (let [id (get-in parameters [:path :id])
        body (or (:body parameters) {})
        title (when (contains? body :title)
                (some-> (:title body) str str/trim))
        completed (when (contains? body :completed)
                    (:completed body))]
    (cond
      (empty? body)
      (http/respond-json {:error "Supply at least one field to update"} 400)

      (and (contains? body :title)
           (str/blank? (or title "")))
      (http/respond-json {:error "Title is required"} 400)

      :else
      (if-let [record (todo.service/update-todo! id (cond-> {}
                                                       (contains? body :title) (assoc :title title)
                                                       (contains? body :completed) (assoc :completed completed)))]
        (http/respond-json record)
        (http/not-found nil)))))

(defn delete-response [{:keys [parameters]}]
  (let [id (get-in parameters [:path :id])
        deleted (todo.service/delete-todo! id)]
    (if (pos? deleted)
      (http/respond-json {:status "deleted"})
      (http/not-found nil))))
