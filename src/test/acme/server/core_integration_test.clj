(ns acme.server.core-integration-test
  (:require
   [acme.server.auth :as auth]
   [acme.server.core :as core]
   [acme.server.db :as db]
   [acme.server.services.refresh-tokens :as refresh-tokens]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [ring.mock.request :as mock])
  (:import
   (java.util UUID)))

(defn- edn-request
  ([method uri]
   (edn-request method uri nil))
  ([method uri body]
   (let [req (mock/request method uri)
         with-accept (mock/header req "accept" "application/edn")]
     (if (some? body)
       (-> with-accept
           (mock/content-type "application/edn")
           (mock/body (pr-str body)))
       with-accept))))

(defn- response-body->edn [response]
  (some-> response :body slurp edn/read-string))

(defn- set-cookie-values [response]
  (let [value (get-in response [:headers "Set-Cookie"])]
    (cond
      (nil? value) []
      (string? value) [value]
      (sequential? value) value
      :else [])))

(deftest login-endpoint-validates-request
  (let [response (core/handler (edn-request :post "/api/auth/login"
                                            {:email "   "
                                             :password "short"}))]
    (is (= 400 (:status response)))
    (is (= {:error "Email is required"}
           (response-body->edn response)))))

(deftest login-endpoint-success
  (let [user {:uuid "user-1"
              :name "Test User"
              :email "user@example.com"
              :role "user"
              :password_hash "hash"}
        token {:token "jwt-token"
               :token-type "Bearer"
               :expires-at "2024-01-01T00:00:00Z"
               :expires-in 3600}]
    (with-redefs [db/query-one (fn [[sql value]]
                                 (when (and (string? sql)
                                            (str/includes? sql "lower(email) = ?"))
                                   user))
                  auth/verify-password (fn [password password-hash]
                                         (and (= "Password123" password)
                                              (= "hash" password-hash)))
                  auth/issue-token (fn [_] token)
                  refresh-tokens/issue-token-for-user! (fn [uuid]
                                                         (is (= "user-1" uuid))
                                                         {:token "refresh-token"})]
      (let [response (core/handler (edn-request :post "/api/auth/login"
                                                {:email "user@example.com"
                                                 :password "Password123"}))
            body (response-body->edn response)
            cookies (set-cookie-values response)]
        (is (= 200 (:status response)))
        (is (= "jwt-token" (:access_token body)))
        (is (= "Bearer" (:token_type body)))
        (is (= "2024-01-01T00:00:00Z" (:expires_at body)))
        (is (= 3600 (:expires_in body)))
        (is (= {:uuid "user-1"
                :name "Test User"
                :email "user@example.com"
                :role "user"}
               (:user body)))
        (is (some #(str/starts-with? % (str refresh-tokens/cookie-name "=")) cookies))))))

(deftest refresh-endpoint-requires-cookie
  (let [response (core/handler (edn-request :post "/api/auth/refresh"))]
    (is (= 401 (:status response)))
    (is (= {:error "Refresh token is missing"}
           (response-body->edn response)))))

(deftest refresh-endpoint-rotates-token
  (let [user-id (str (UUID/randomUUID))
        user {:uuid user-id
              :name "Sample"
              :email "sample@example.com"
              :role "user"}
        token {:token "jwt-token"
               :token-type "Bearer"
               :expires-at "2024-01-01T00:00:00Z"
               :expires-in 3600}]
    (with-redefs [refresh-tokens/rotate-token! (fn [token-value]
                                                 (is (= "old-refresh-token" token-value))
                                                 {:user-uuid user-id
                                                  :refresh {:token "new-refresh"}})
                  db/query-one (fn [[sql value]]
                                 (when (and (string? sql)
                                            (str/includes? sql "where uuid = ?"))
                                   (is (= (UUID/fromString user-id) value))
                                   user))
                  auth/issue-token (fn [_] token)
                  refresh-tokens/issue-token-for-user! (fn [_] {:token "new-refresh"})]
      (let [request (-> (edn-request :post "/api/auth/refresh")
                        (mock/cookie refresh-tokens/cookie-name " old-refresh-token ")
                        (assoc :cookies {refresh-tokens/cookie-name {:value " old-refresh-token "}}))
            response (core/handler request)
            body (response-body->edn response)
            cookies (set-cookie-values response)]
        (is (= 200 (:status response)))
        (is (= "jwt-token" (:access_token body)))
        (is (= "Bearer" (:token_type body)))
        (is (= "2024-01-01T00:00:00Z" (:expires_at body)))
        (is (= 3600 (:expires_in body)))
        (is (some #(str/starts-with? % (str refresh-tokens/cookie-name "=")) cookies))))))

(deftest logout-endpoint-clears-cookie
  (let [revoked (atom nil)]
    (with-redefs [refresh-tokens/parse-token (fn [value]
                                               (is (= "token-value" value))
                                               {:id "token-id"})
                  refresh-tokens/revoke-token! (fn [token-id]
                                                 (reset! revoked token-id))
                  refresh-tokens/clear-refresh-cookie (fn []
                                                        {:value ""
                                                         :path "/"
                                                         :max-age 0})]
      (let [request (-> (edn-request :post "/api/auth/logout")
                        (mock/cookie refresh-tokens/cookie-name "token-value")
                        (assoc :cookies {refresh-tokens/cookie-name {:value "token-value"}}))
            response (core/handler request)
            cookies (set-cookie-values response)]
        (is (= "token-id" @revoked))
        (is (= 200 (:status response)))
        (is (= {:status "signed-out"}
               (response-body->edn response)))
        (is (some #(str/includes? % "Max-Age=0") cookies)))))) 
