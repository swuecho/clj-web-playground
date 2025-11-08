(ns acme.server.middleware.auth
  (:require
   [clojure.string :as str]
   [acme.server.auth :as auth]
   [acme.server.http :as http]))

(defn admin? [identity]
  (= "admin" (:role identity)))

(defn- bearer-token [{:keys [headers]}]
  (when-let [raw (get headers "authorization")]
    (let [value (str/trim raw)
          lc (str/lower-case value)]
      (when (str/starts-with? lc "bearer ")
        (subs value 7)))))

(defn wrap-authentication [handler]
  (fn [request]
    (if-let [token (bearer-token request)]
      (if-let [claims (auth/decode-token token)]
        (handler (assoc request :identity claims))
        (http/respond-json {:error "Invalid or expired token"} 401))
      (handler request))))

(defn wrap-require-identity [handler]
  (fn [request]
    (if (:identity request)
      (handler request)
      (http/respond-json {:error "Unauthorized"} 401))))

(defn wrap-require-admin [handler]
  (fn [request]
    (let [identity (:identity request)]
      (cond
        (nil? identity)
        (http/respond-json {:error "Unauthorized"} 401)

        (admin? identity)
        (handler request)

        :else
        (http/respond-json {:error "Forbidden"} 403)))))
