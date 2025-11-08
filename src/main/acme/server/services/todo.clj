(ns acme.server.services.todo
  (:require
   [acme.server.models.todo :as todo])
  (:import
   (java.util UUID)))

(defn- normalize-instance [row]
  (when row
    (let [record (into {} (seq row))]
      (-> record
          (update :completed #(if (nil? %) false (boolean %)))
          (update :user_id #(some-> % str))))))

(defn- ->uuid [value]
  (cond
    (instance? UUID value) value
    (string? value)
    (try
      (UUID/fromString (str value))
      (catch IllegalArgumentException _
        nil))
    :else nil))

(defn- identity-uuid [identity]
  (some-> (:sub identity) ->uuid))

(defn- admin? [identity]
  (= "admin" (:role identity)))

(defn- can-access? [identity todo-record]
  (or (admin? identity)
      (let [owner (:user_id todo-record)]
        (and owner
             (= owner (identity-uuid identity))))))

(defn- accessible-record [identity id]
  (when-let [record (todo/fetch id)]
    (when (can-access? identity record)
      record)))

(defn list-todos [identity]
  (let [records (if (admin? identity)
                  (todo/all)
                  (if-let [user-id (identity-uuid identity)]
                    (todo/all-by-user user-id)
                    []))]
    (mapv normalize-instance (or records []))))

(defn fetch-todo [identity id]
  (some-> (accessible-record identity id) normalize-instance))

(defn create-todo! [identity {:keys [title completed]}]
  (if-let [user-id (identity-uuid identity)]
    (normalize-instance (todo/create! {:title title
                                       :completed completed
                                       :user_id user-id}))
    nil))

(defn update-todo! [identity id changes]
  (when (accessible-record identity id)
    (some-> (todo/update! id changes) normalize-instance)))

(defn delete-todo! [identity id]
  (if (accessible-record identity id)
    (todo/delete! id)
    0))

(comment
  (in-ns 'acme.server.services.todo)
  (require 'acme.server.services.todo)
  (list-todos {:role "admin"})
  (todo/all))
