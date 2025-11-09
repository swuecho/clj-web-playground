(ns acme.server.handlers.auth
  (:require
   [clojure.string :as str]
   [acme.server.auth :as auth]
   [acme.server.db :as db]
   [acme.server.handlers.users :as user-handlers]
   [acme.server.http :as http]
   [acme.server.refresh-tokens :as refresh-tokens]
   [acme.server.users.validation :as user.validation])
  (:import
   (java.util UUID)))

(defn- ->uuid [value]
  (try
    (UUID/fromString (str/trim (str value)))
    (catch Exception _
      nil)))

(defn- fetch-user-by-email [email]
  (when-let [normalized (user.validation/normalize-email email)]
    (db/query-one
     ["select uuid::text as uuid,
              \"name\" as name,
              age as age,
              email,
              role,
              password_hash
      from \"UserTable\"
      where lower(email) = ?
      limit 1"
      normalized])))

(defn- fetch-user-by-uuid [uuid]
  (when-let [id (->uuid uuid)]
    (db/query-one
     ["select uuid::text as uuid,
              \"name\" as name,
              age as age,
              email,
              role,
              password_hash
      from \"UserTable\"
      where uuid = ?
      limit 1"
      id])))

(defn- attach-refresh-cookie [response refresh]
  (if-let [cookie (refresh-tokens/build-refresh-cookie refresh)]
    (assoc response :cookies {refresh-tokens/cookie-name cookie})
    response))

(defn- session-payload
  ([user]
   (session-payload user nil))
  ([user refresh]
   (let [{:keys [token token-type expires-at expires-in]}
         (auth/issue-token user)
         sanitized (dissoc user :password_hash)
         refresh-info (or refresh (refresh-tokens/issue-token-for-user! (:uuid sanitized)))]
     (if (and refresh-info (:token refresh-info))
       (-> (http/respond-json {:access_token token
                               :token_type token-type
                               :expires_at expires-at
                               :expires_in expires-in
                               :user sanitized})
           (attach-refresh-cookie refresh-info))
       (http/respond-json {:error "Unable to issue refresh token"} 500)))))

(defn- refresh-cookie-value [{:keys [cookies]}]
  (get-in cookies [refresh-tokens/cookie-name :value]))

(defn login-response [{:keys [parameters body-params]}]
  (let [body (or (:body parameters) body-params {})
        raw-email (:email body)
        raw-password (:password body)
        email (user.validation/normalize-email raw-email)
        password (some-> raw-password str str/trim)]
    (cond
      (str/blank? (or raw-email ""))
      (http/respond-json {:error "Email is required"} 400)

      (not (user.validation/valid-email? email))
      (http/respond-json {:error "Email is invalid"} 400)

      (str/blank? password)
      (http/respond-json {:error "Password is required"} 400)

      :else
      (if-let [user (fetch-user-by-email email)]
        (if (auth/verify-password password (:password_hash user))
          (session-payload user)
          (http/respond-json {:error "Invalid email or password"} 401))
        (http/respond-json {:error "Invalid email or password"} 401)))))

(defn- derive-name [{:keys [name email]}]
  (let [trimmed (some-> name str str/trim)]
    (if (seq trimmed)
      trimmed
      (or (some-> email
                  (str/split #"@" 2)
                  first)
          email
          ""))))

(defn register-response [{:keys [parameters body-params]}]
  (let [body (or (:body parameters) body-params {})
        sanitized {:name (derive-name body)
                   :age 0
                   :email (:email body)
                   :password (:password body)}]
    (user-handlers/add-user-response {:parameters {:body sanitized}
                                      :body-params sanitized})))

(defn refresh-response [request]
  (let [token (some-> (refresh-cookie-value request) str str/trim)]
    (if (str/blank? token)
      (http/respond-json {:error "Refresh token is missing"} 401)
      (if-let [{:keys [user-uuid refresh]} (refresh-tokens/rotate-token! token)]
        (if-let [user (fetch-user-by-uuid user-uuid)]
          (session-payload user refresh)
          (http/respond-json {:error "Account not found"} 404))
        (-> (http/respond-json {:error "Refresh token is invalid or expired"} 401)
            (assoc :cookies {refresh-tokens/cookie-name (refresh-tokens/clear-refresh-cookie)}))))))

(defn list-refresh-tokens-response [{:keys [parameters path-params]}]
  (let [uuid (or (get-in parameters [:path :uuid]) (:uuid path-params))
        trimmed (some-> uuid str str/trim)]
    (cond
      (str/blank? trimmed)
      (http/respond-json {:error "User uuid is required"} 400)

      (nil? (->uuid trimmed))
      (http/respond-json {:error "Invalid uuid format"} 400)

      (nil? (fetch-user-by-uuid trimmed))
      (http/not-found nil)

      :else
      (http/respond-json (or (refresh-tokens/list-tokens-for-user trimmed) [])))))

(defn revoke-refresh-token-response [{:keys [parameters path-params]}]
  (let [uuid (or (get-in parameters [:path :uuid]) (:uuid path-params))
        token-id (or (get-in parameters [:path :token-id]) (:token-id path-params))
        trimmed-uuid (some-> uuid str str/trim)
        trimmed-token (some-> token-id str str/trim)]
    (cond
      (or (str/blank? trimmed-uuid) (str/blank? trimmed-token))
      (http/respond-json {:error "User uuid and token id are required"} 400)

      (or (nil? (->uuid trimmed-uuid)) (nil? (->uuid trimmed-token)))
      (http/respond-json {:error "Invalid uuid format"} 400)

      :else
      (if (refresh-tokens/revoke-token! trimmed-token trimmed-uuid)
        (http/respond-json {:status "revoked"})
        (http/respond-json {:error "Refresh token not found"} 404)))))

(defn logout-response [request]
  (let [token (refresh-cookie-value request)]
    (when (seq token)
      (when-let [{:keys [id]} (refresh-tokens/parse-token token)]
        (refresh-tokens/revoke-token! id)))
    (-> (http/respond-json {:status "signed-out"})
        (assoc :cookies {refresh-tokens/cookie-name (refresh-tokens/clear-refresh-cookie)}))))
