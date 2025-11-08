(ns acme.server.handlers.auth
  (:require
   [clojure.string :as str]
   [acme.server.auth :as auth]
   [acme.server.db :as db]
   [acme.server.http :as http]
   [acme.server.users.validation :as user.validation]))

(defn- fetch-user-by-email [email]
  (when-let [normalized (user.validation/normalize-email email)]
    (db/query-one
     ["select uuid::text as uuid,
              \"name\" as name,
              age as age,
              email,
              password_hash
        from \"UserTable\"
        where lower(email) = ?
        limit 1"
      normalized])))

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
          (let [{:keys [token token-type expires-at expires-in]}
                (auth/issue-token user)
                sanitized (dissoc user :password_hash)]
            (http/respond-json {:access_token token
                                :token_type token-type
                                :expires_at expires-at
                                :expires_in expires-in
                                :user sanitized}))
          (http/respond-json {:error "Invalid email or password"} 401))
        (http/respond-json {:error "Invalid email or password"} 401))))
)
