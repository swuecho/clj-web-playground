(ns acme.server.refresh-tokens
  (:require
   [acme.server.db :as db]
   [buddy.hashers :as hashers]
   [clojure.string :as str])
  (:import
   (java.security SecureRandom)
   (java.time Instant)
   (java.util Base64 UUID)))

(def default-refresh-ttl-days 30)
(def cookie-name "acme-refresh")
(def ^:private truthy-values #{"1" "true" "yes" "on"})

(defn- truthy? [value]
  (boolean (some-> value str/lower-case truthy-values)))

(defn- parse-positive-long [value]
  (try
    (let [parsed (Long/parseLong (str/trim (str value)))]
      (when (pos? parsed) parsed))
    (catch Exception _
      nil)))

(defn refresh-ttl-seconds []
  (or (some-> (System/getenv "ACME_REFRESH_TTL_SECONDS") parse-positive-long)
      (let [days (or (some-> (System/getenv "ACME_REFRESH_TTL_DAYS") parse-positive-long)
                     default-refresh-ttl-days)]
        (* days 86400))))

(defn- cookie-same-site []
  (case (some-> (System/getenv "ACME_REFRESH_COOKIE_SAMESITE") str/lower-case)
    "strict" :strict
    "lax" :lax
    "none" :none
    :lax))

(defn- cookie-secure? []
  (truthy? (System/getenv "ACME_REFRESH_COOKIE_SECURE")))

(defn cookie-config []
  {:path "/"
   :http-only true
   :same-site (cookie-same-site)
   :secure (cookie-secure?)})

(def ^SecureRandom secure-random (SecureRandom.))

(defn- random-bytes [n]
  (let [bytes (byte-array n)]
    (.nextBytes secure-random bytes)
    bytes))

(defn- generate-secret []
  (let [encoder (.withoutPadding (Base64/getUrlEncoder))]
    (.encodeToString encoder (random-bytes 32))))

(defn- now []
  (Instant/now))

(defn- instant->sql [^Instant inst]
  (java.sql.Timestamp/from inst))

(defn- expires-at []
  (instant->sql (.plusSeconds (now) (refresh-ttl-seconds))))

(defn parse-token [value]
  (when (and (string? value) (str/includes? value "."))
    (let [[token-id secret] (str/split value #"\." 2)]
      (when (and (seq token-id) (seq secret))
        {:id token-id
         :secret secret}))))

(defn- ->uuid [value]
  (try
    (UUID/fromString (str/trim value))
    (catch Exception _
      nil)))

(defn- serialize-row [row]
  (when row
    (let [revoked? (boolean (:revoked row))]
      (-> row
          (update :created_at (fn [t] (some-> t str)))
          (update :last_used_at (fn [t] (some-> t str)))
          (update :expires_at (fn [t] (some-> t str)))
          (update :revoked_at (fn [t] (some-> t str)))
          (assoc :revoked? revoked?)
          (dissoc :revoked)))))

(defn issue-token-for-user!
  ([user-uuid]
   (issue-token-for-user! user-uuid {}))
  ([user-uuid {:keys [tx?]}]
   (when-let [uuid (->uuid user-uuid)]
     (let [token-id (UUID/randomUUID)
           secret (generate-secret)
           token-hash (hashers/derive secret)
           expires (expires-at)
           statement ["insert into \"RefreshToken\" (id, user_uuid, token_hash, expires_at)
                      values (?, ?, ?, ?) returning id::text as id, user_uuid::text as user_uuid,
                                        created_at, last_used_at, expires_at, revoked_at" 
                      token-id uuid token-hash expires]
           record (if tx?
                    (db/query-one statement)
                    (db/with-transaction
                      (db/query-one statement)))]
       {:token (str token-id "." secret)
        :record (serialize-row record)}))))

(defn- valid-row? [{:keys [revoked_at expires_at]}]
  (and (nil? revoked_at)
       (some-> expires_at (.toInstant) (.isAfter (now)))))

(defn rotate-token!
  "Validate the provided refresh token, revoke it, and issue a new one. Returns
  {:user-uuid uuid :refresh {:token <raw> :record {...}}} when successful."
  [token]
  (when-let [{:keys [id secret]} (parse-token token)]
    (when-let [uuid (->uuid id)]
      (db/with-transaction
        (let [row (db/query-one ["select id::text as id,
                                         user_uuid::text as user_uuid,
                                         token_hash,
                                         created_at,
                                         last_used_at,
                                         expires_at,
                                         revoked_at
                                  from \"RefreshToken\"
                                  where id = ? for update" uuid])]
          (when (and row
                     (valid-row? row)
                     (hashers/check secret (:token_hash row)))
            (db/query-one ["update \"RefreshToken\"
                           set revoked_at = now(), last_used_at = now()
                           where id = ?" uuid])
            (let [{:keys [token] :as refresh-result}
                  (issue-token-for-user! (:user_uuid row) {:tx? true})]
              {:user-uuid (:user_uuid row)
               :refresh refresh-result})))))))

(defn revoke-token!
  ([token-id]
   (revoke-token! token-id nil))
  ([token-id user-uuid]
   (when-let [id (->uuid token-id)]
     (let [base ["update \"RefreshToken\" set revoked_at = now() where id = ? and revoked_at is null returning id::text as id" id]
           statement (if-let [user (some-> user-uuid ->uuid)]
                       ["update \"RefreshToken\"
                         set revoked_at = now()
                         where id = ? and user_uuid = ? and revoked_at is null
                         returning id::text as id" id user]
                       base)]
       (db/query-one statement)))))

(defn list-tokens-for-user [user-uuid]
  (when-let [uuid (->uuid user-uuid)]
    (->> (db/query ["select id::text as id,
                           created_at,
                           last_used_at,
                           expires_at,
                           revoked_at,
                           (case when revoked_at is not null then true
                                 when expires_at <= now() then true
                                 else false end) as revoked
                    from \"RefreshToken\"
                    where user_uuid = ?
                    order by created_at desc" uuid])
         (map serialize-row)
         (remove nil?)
         vec)))

(defn build-refresh-cookie [refresh]
  (when-let [token (:token refresh)]
    (assoc (cookie-config)
           :value token
           :max-age (refresh-ttl-seconds))))

(defn clear-refresh-cookie []
  (assoc (cookie-config)
         :value ""
         :max-age 0))

(defn cookie->opts [{:keys [value] :as cookie}]
  (when value
    (let [opts (dissoc cookie :value)]
      {:value value
       :opts opts})))
