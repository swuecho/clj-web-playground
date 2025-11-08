(ns acme.server.handlers.users
  (:require
   [clojure.string :as str]
   [clojure.walk :as walk]
   [acme.server.auth :as auth]
   [acme.server.db :as db]
   [acme.server.http :as http]
   [acme.server.users.validation :as user.validation])
  (:import
   (java.sql SQLException)
   (java.util UUID)))

(def ^:const password-min-length 8)

(defn- normalize-email [email]
  (user.validation/normalize-email email))

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
    (some? (db/query-one ["select 1 as exists from \"UserTable\" where uuid = ? limit 1" uuid]))
    false))

(defn- normalize-request-map [params]
  (cond
    (map? params) (walk/keywordize-keys params)
    (nil? params) {}
    :else {}))

(defn- email-conflict? ([email] (email-conflict? email nil))
  ([email exclude-uuid]
   (let [normalized (normalize-email email)
         parsed-uuid (some-> exclude-uuid ->uuid)]
     (if (seq normalized)
       (boolean
        (if parsed-uuid
          (db/query-one ["select 1 from \"UserTable\" where lower(email) = ? and uuid <> ? limit 1"
                         normalized parsed-uuid])
          (db/query-one ["select 1 from \"UserTable\" where lower(email) = ? limit 1"
                         normalized])))
       false))))

(defn- validate-user [input]
  (let [params (normalize-request-map input)
        {:keys [uuid name age email password]} params
        trimmed-name (some-> name str/trim)
        parsed-age (normalize-age age)
        supplied-uuid (some-> uuid str str/trim not-empty)
        parsed-uuid (some-> supplied-uuid ->uuid)
        normalized-email (normalize-email email)
        normalized-password (some-> password str str/trim)
        duplicate? (when parsed-uuid
                     (user-exists? parsed-uuid))
        email-duplicate? (email-conflict? normalized-email)]
    (cond
      (or (nil? trimmed-name) (str/blank? trimmed-name))
      {:status 400 :message "Name is required"}

      (or (nil? parsed-age) (neg? parsed-age))
      {:status 400 :message "Age must be a non-negative integer"}

      (str/blank? (or email ""))
      {:status 400 :message "Email is required"}

      (not (user.validation/valid-email? normalized-email))
      {:status 400 :message "Email is invalid"}

      (and supplied-uuid (nil? parsed-uuid))
      {:status 400 :message "Invalid uuid format"}

      duplicate?
      {:status 409 :message "A user with that uuid already exists"}

      email-duplicate?
      {:status 409 :message "Email is already in use"}

      (str/blank? (or normalized-password ""))
      {:status 400 :message "Password is required"}

      (< (count normalized-password) password-min-length)
      {:status 400 :message (format "Password must be at least %d characters" password-min-length)}

      :else
      {:status 201
       :user {:uuid supplied-uuid
              :name trimmed-name
              :age parsed-age
              :email normalized-email
              :password normalized-password}})))

(defn- validate-update [uuid input]
  (let [params (normalize-request-map input)
        {:keys [name age email password]} params
        name-present? (contains? params :name)
        age-present? (contains? params :age)
        email-present? (contains? params :email)
        password-present? (contains? params :password)
        trimmed-name (when name-present? (some-> name str/trim))
        parsed-age (when age-present? (normalize-age age))
        normalized-email (when email-present? (normalize-email email))
        normalized-password (when password-present? (some-> password str str/trim))]
    (cond
      (not (or name-present? age-present? email-present? password-present?))
      {:status 400 :message "Supply at least one field to update"}

      (and name-present? (or (nil? trimmed-name) (str/blank? trimmed-name)))
      {:status 400 :message "Name is required"}

      (and age-present? (or (nil? parsed-age) (neg? parsed-age)))
      {:status 400 :message "Age must be a non-negative integer"}

      (and email-present? (str/blank? (or email "")))
      {:status 400 :message "Email is required"}

      (and email-present? (not (user.validation/valid-email? normalized-email)))
      {:status 400 :message "Email is invalid"}

      (and email-present? (email-conflict? normalized-email uuid))
      {:status 409 :message "Email is already in use"}

      (and password-present? (str/blank? (or normalized-password "")))
      {:status 400 :message "Password is required"}

      (and password-present? (< (count normalized-password) password-min-length))
      {:status 400 :message (format "Password must be at least %d characters" password-min-length)}

      :else
      {:status 200
       :updates (cond-> {}
                  name-present? (assoc :name trimmed-name)
                  age-present? (assoc :age parsed-age)
                  email-present? (assoc :email normalized-email)
                  password-present? (assoc :password normalized-password))})))

(defn- ensure-unique-uuid [{:keys [uuid] :as user}]
  (if uuid
    user
    (loop [candidate (str (UUID/randomUUID))]
      (if (user-exists? candidate)
        (recur (str (UUID/randomUUID)))
        (assoc user :uuid candidate)))))

(defn- build-update-sql [{:keys [name age email password-hash]}]
  (let [set-fragments (cond-> []
                         name (conj "\"name\" = ?")
                         age (conj "age = ?")
                         email (conj "email = ?")
                         password-hash (conj "password_hash = ?"))
        params (cond-> []
                 name (conj name)
                 age (conj age)
                 email (conj email)
                 password-hash (conj password-hash))]
    (when (seq set-fragments)
      {:sql (str "update \"UserTable\" set " (str/join ", " set-fragments) " where uuid = ? returning uuid::text as uuid, \"name\" as name, age as age, email, role")
       :params params})))

(defn users-response [_]
  (let [users (db/query ["select uuid::text as uuid, \"name\" as name, age as age, email, role from \"UserTable\" order by \"name\" asc"])]
    (http/respond-json users)))

(defn add-user-response [{:keys [parameters body-params]}]
  (let [body (or (:body parameters) body-params)
        {:keys [status message user]} (validate-user body)]
    (if user
      (let [sanitized (ensure-unique-uuid user)]
        (try
          (let [password-hash (auth/hash-password (:password sanitized))
                created (db/with-transaction
                          (db/query-one ["insert into \"UserTable\" (uuid, name, age, email, password_hash) values (?, ?, ?, ?, ?) returning uuid::text as uuid, \"name\" as name, age as age, email, role"
                                         (:uuid sanitized)
                                         (:name sanitized)
                                         (:age sanitized)
                                         (:email sanitized)
                                         password-hash]))]
            (http/respond-json created status))
          (catch SQLException ex
            (if (= "23505" (.getSQLState ex))
              (http/respond-json {:error "A user with that uuid or email already exists"} 409)
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
            {:keys [status message updates]} (validate-update uuid-param body)]
        (if updates
          (let [prepared-updates (cond-> updates
                                   (:password updates)
                                   (-> (assoc :password-hash (auth/hash-password (:password updates)))
                                       (dissoc :password)))]
            (if-let [{:keys [sql params]} (build-update-sql prepared-updates)]
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
                      (db/query-one ["delete from \"UserTable\" where uuid = ? returning uuid::text as uuid, \"name\" as name, age as age, email, role" uuid-param]))]
        (if deleted
          (http/respond-json deleted)
          (http/not-found nil))))))
)
