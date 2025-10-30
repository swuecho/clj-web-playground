(ns acme.server.handlers.users
  (:require
   [clojure.string :as str]
   [clojure.walk :as walk]
   [acme.server.db :as db]
   [acme.server.http :as http])
  (:import
   (java.sql SQLException)
   (java.util UUID)))

(defn- normalize-age [age]
  (cond
    (int? age) age
    (integer? age) (int age)
    (string? age)
    (let [trimmed (str/trim age)]
      (when-not (str/blank? trimmed)
        (try
          (Integer/parseInt trimmed)
          (catch NumberFormatException _
            nil))))
    :else nil))

(defn- ->uuid [value]
  (cond
    (instance? UUID value) value
    (string? value)
    (let [trimmed (str/trim value)]
      (when-not (str/blank? trimmed)
        (try
          (UUID/fromString trimmed)
          (catch IllegalArgumentException _
            nil))))
    :else nil))

(defn- user-exists? [uuid]
  (if-let [uuid (->uuid uuid)]
    (some? (db/query-one ["select 1 from \"UserTable\" where uuid = ? limit 1" uuid]))
    false))

(defn- normalize-request-map [params]
  (cond
    (map? params) (walk/keywordize-keys params)
    (nil? params) {}
    :else {}))

(defn- validate-user [input]
  (let [params (normalize-request-map input)
        {:keys [uuid name age]} params
        trimmed-name (some-> name str/trim)
        parsed-age (normalize-age age)
        supplied-uuid (some-> uuid str str/trim not-empty)
        parsed-uuid (some-> supplied-uuid ->uuid)
        duplicate? (when parsed-uuid
                     (user-exists? parsed-uuid))]
    (cond
      (or (nil? trimmed-name) (str/blank? trimmed-name))
      {:status 400 :message "Name is required"}

      (or (nil? parsed-age) (neg? parsed-age))
      {:status 400 :message "Age must be a non-negative integer"}

      (and supplied-uuid (nil? parsed-uuid))
      {:status 400 :message "Invalid uuid format"}

      duplicate?
      {:status 409 :message "A user with that uuid already exists"}

      :else
      {:status 201
       :user {:uuid supplied-uuid
              :name trimmed-name
              :age parsed-age}})))

(defn- validate-update [input]
  (let [params (normalize-request-map input)
        {:keys [name age]} params
        name-present? (contains? params :name)
        age-present? (contains? params :age)
        trimmed-name (when name-present? (some-> name str/trim))
        parsed-age (when age-present? (normalize-age age))]
    (cond
      (not (or name-present? age-present?))
      {:status 400 :message "Supply at least one field to update"}

      (and name-present? (or (nil? trimmed-name) (str/blank? trimmed-name)))
      {:status 400 :message "Name is required"}

      (and age-present? (or (nil? parsed-age) (neg? parsed-age)))
      {:status 400 :message "Age must be a non-negative integer"}

      :else
      {:status 200
       :updates (cond-> {}
                  name-present? (assoc :name trimmed-name)
                  age-present? (assoc :age parsed-age))})))

(defn- ensure-unique-uuid [{:keys [uuid] :as user}]
  (if uuid
    user
    (loop [candidate (str (UUID/randomUUID))]
      (if (user-exists? candidate)
        (recur (str (UUID/randomUUID)))
        (assoc user :uuid candidate)))))

(defn- build-update-sql [{:keys [name age]}]
  (let [set-fragments (cond-> []
                         name (conj "\"name\" = ?")
                         age (conj "age = ?"))
        params (cond-> []
                 name (conj name)
                 age (conj age))]
    (when (seq set-fragments)
      {:sql (str "update \"UserTable\" set " (str/join ", " set-fragments) " where uuid = ? returning uuid, name, age")
       :params params})))

(defn users-response [_]
  (let [users (db/query ["select uuid, name, age from \"UserTable\" order by name asc"])]
    (http/respond-json users)))

(defn add-user-response [{:keys [parameters body-params]}]
  (let [body (or (:body parameters) body-params)
        {:keys [status message user]} (validate-user body)]
    (if user
      (let [sanitized (ensure-unique-uuid user)]
        (try
          (let [created (db/with-transaction
                          (db/query-one ["insert into \"UserTable\" (uuid, name, age) values (?, ?, ?) returning uuid, name, age"
                                         (:uuid sanitized)
                                         (:name sanitized)
                                         (:age sanitized)]))]
            (http/respond-json created status))
          (catch SQLException ex
            (if (= "23505" (.getSQLState ex))
              (http/respond-json {:error "A user with that uuid already exists"} 409)
              (throw ex)))))
      (http/respond-json {:error message} status))))

(defn update-user-response [{:keys [parameters path-params body-params]}]
  (let [uuid (or (get-in parameters [:path :uuid]) (:uuid path-params))
        uuid (some-> uuid str/trim)
        uuid-param (->uuid uuid)]
    (cond
      (str/blank? uuid)
      (http/respond-json {:error "User uuid is required"} 400)

      (nil? uuid-param)
      (http/respond-json {:error "Invalid uuid format"} 400)

      :else
      (let [body (or (:body parameters) body-params)
            {:keys [status message updates]} (validate-update body)]
        (if updates
          (if-let [{:keys [sql params]} (build-update-sql updates)]
            (let [statement (conj params uuid-param)]
              (try
                (let [updated (db/with-transaction
                                (db/query-one (into [sql] statement)))]
                  (if updated
                    (http/respond-json updated status)
                    (http/not-found nil)))
                (catch SQLException ex
                  (throw ex))))
            (http/respond-json {:error "Supply at least one field to update"} 400))
          (http/respond-json {:error message} status))))))

(defn delete-user-response [{:keys [parameters path-params]}]
  (let [uuid (or (get-in parameters [:path :uuid]) (:uuid path-params))
        uuid (some-> uuid str/trim)
        uuid-param (->uuid uuid)]
    (cond
      (str/blank? uuid)
      (http/respond-json {:error "User uuid is required"} 400)

      (nil? uuid-param)
      (http/respond-json {:error "Invalid uuid format"} 400)

      :else
      (let [deleted (db/with-transaction
                      (db/query-one ["delete from \"UserTable\" where uuid = ? returning uuid, name, age" uuid-param]))]
        (if deleted
          (http/respond-json deleted)
          (http/not-found nil))))))
