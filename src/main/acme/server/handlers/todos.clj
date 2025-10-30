(ns acme.server.handlers.todos
  (:require
   [clojure.string :as str]
   [acme.server.http :as http]
   [acme.server.models.todo :as todo]))

(defn- ->int [value]
  (cond
    (int? value) value
    (integer? value) (int value)
    (string? value)
    (let [trimmed (str/trim value)]
      (when-not (str/blank? trimmed)
        (try
          (Integer/parseInt trimmed)
          (catch NumberFormatException _
            nil))))
    :else nil))

(defn- ->boolean [value]
  (cond
    (nil? value) nil
    (boolean? value) value
    (string? value)
    (let [trimmed (str/lower-case (str/trim value))]
      (cond
        (contains? #{"true" "1" "yes" "y"} trimmed) true
        (contains? #{"false" "0" "no" "n"} trimmed) false
        :else nil))
    (number? value) (not (zero? (int value)))
    :else nil))

(defn list-response [_]
  (http/respond-json (todo/all)))

(defn create-response [{:keys [body-params]}]
  (let [title (some-> (:title body-params) str str/trim)
        completed (->boolean (:completed body-params))]
    (cond
      (or (nil? title) (str/blank? title))
      (http/respond-json {:error "Title is required"} 400)

      :else
      (http/respond-json (todo/create! {:title title
                                        :completed completed})
                         201))))

(defn fetch-response [{:keys [path-params]}]
  (let [id (->int (:id path-params))]
    (cond
      (nil? id)
      (http/respond-json {:error "Invalid todo id"} 400)

      :else
      (if-let [record (todo/fetch id)]
        (http/respond-json record)
        (http/not-found nil)))))

(defn update-response [{:keys [path-params body-params]}]
  (let [id (->int (:id path-params))
        title (when (contains? body-params :title)
                (some-> (:title body-params) str str/trim))
        completed (when (contains? body-params :completed)
                    (->boolean (:completed body-params)))]
    (cond
      (nil? id)
      (http/respond-json {:error "Invalid todo id"} 400)

      (and (contains? body-params :title)
           (or (nil? title) (str/blank? title)))
      (http/respond-json {:error "Title is required"} 400)

      (and (contains? body-params :completed) (nil? completed))
      (http/respond-json {:error "Completed must be boolean"} 400)

      (not (or (contains? body-params :title)
               (contains? body-params :completed)))
      (http/respond-json {:error "Supply at least one field to update"} 400)

      :else
      (if-let [record (todo/update! id (cond-> {}
                                         (contains? body-params :title) (assoc :title title)
                                         (contains? body-params :completed) (assoc :completed completed)))]
        (http/respond-json record)
        (http/not-found nil)))))

(defn delete-response [{:keys [path-params]}]
  (let [id (->int (:id path-params))]
    (cond
      (nil? id)
      (http/respond-json {:error "Invalid todo id"} 400)

      :else
      (let [deleted (todo/delete! id)]
        (if (pos? deleted)
          (http/respond-json {:status "deleted"})
          (http/not-found nil))))))
