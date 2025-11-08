(ns acme.server.handlers.todos
  (:require
   [clojure.string :as str]
   [acme.server.http :as http]
   [acme.server.services.todo :as todo.service]))

(defn list-response [{:keys [identity]}]
  (http/respond-json (todo.service/list-todos identity)))

(defn create-response [{:keys [identity parameters]}]
  (let [{:keys [title completed]} (:body parameters)
        title (some-> title str/trim)
        payload (cond-> {:title title}
                  (some? completed) (assoc :completed completed))]
    (if-let [created (todo.service/create-todo! identity payload)]
      (http/respond-json created 201)
      (http/respond-json {:error "Unable to determine current user"} 403))))

(defn fetch-response [{:keys [identity parameters]}]
  (let [id (get-in parameters [:path :id])]
    (if-let [record (todo.service/fetch-todo identity id)]
      (http/respond-json record)
      (http/not-found nil))))

(defn update-response [{:keys [identity parameters]}]
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
      (if-let [record (todo.service/update-todo! identity id (cond-> {}
                                                               (contains? body :title) (assoc :title title)
                                                               (contains? body :completed) (assoc :completed completed)))]
        (http/respond-json record)
        (http/not-found nil)))))

(defn delete-response [{:keys [identity parameters]}]
  (let [id (get-in parameters [:path :id])
        deleted (todo.service/delete-todo! identity id)]
    (if (pos? deleted)
      (http/respond-json {:status "deleted"})
      (http/not-found nil))))
