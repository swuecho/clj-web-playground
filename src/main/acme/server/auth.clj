(ns acme.server.auth
  (:require
   [buddy.hashers :as hashers]
   [buddy.sign.jwt :as jwt]
   [clojure.string :as str])
  (:import
   (java.time Instant)))

(def default-jwt-secret
  "Fallback secret for dev environments. Override via ACME_JWT_SECRET.")

(def default-token-ttl-seconds 3600)

(def issuer "acme-web")

(defn- parse-positive-long [value]
  (try
    (let [parsed (Long/parseLong (str/trim (str value)))]
      (when (pos? parsed) parsed))
    (catch Exception _
      nil)))

(defn jwt-secret []
  (let [env-secret (some-> (System/getenv "ACME_JWT_SECRET") str/trim)]
    (if (seq env-secret)
      env-secret
      default-jwt-secret)))

(defn token-ttl-seconds []
  (or (some-> (System/getenv "ACME_JWT_TTL_SECONDS") parse-positive-long)
      (when-let [minutes (some-> (System/getenv "ACME_JWT_TTL_MINUTES") parse-positive-long)]
        (* minutes 60))
      default-token-ttl-seconds))

(defn hash-password [password]
  (hashers/derive password))

(defn verify-password [password password-hash]
  (boolean
   (when (and (seq (str password)) (seq (str password-hash)))
     (hashers/check password password-hash))))

(defn issue-token [{:keys [uuid email name]}]
  (let [now (Instant/now)
        ttl (token-ttl-seconds)
        exp (.plusSeconds now ttl)
        claims {:sub uuid
                :email email
                :name name
                :iss issuer
                :iat (.getEpochSecond now)
                :exp (.getEpochSecond exp)}
        token (jwt/sign claims (jwt-secret) {:alg :hs256})]
    {:token token
     :token-type "Bearer"
     :expires-at (.toString exp)
     :expires-in ttl
     :claims claims}))

(defn decode-token [token]
  (try
    (jwt/unsign token (jwt-secret) {:alg :hs256})
    (catch Exception _
      nil)))
