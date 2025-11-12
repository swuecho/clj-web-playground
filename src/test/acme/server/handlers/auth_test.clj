(ns acme.server.handlers.auth-test
  (:require
   [acme.server.auth :as auth]
   [acme.server.db :as db]
   [acme.server.handlers.auth :as auth.handlers]
   [acme.server.handlers.users :as user-handlers]
   [acme.server.services.refresh-tokens :as refresh-tokens]
   [clojure.test :refer [deftest is testing]]
   [ring.util.response :as response])
  (:import
   (java.util UUID)))

(deftest login-response-validates-input
  (testing "missing email"
    (let [response (auth.handlers/login-response {:parameters {:body {:email "   "
                                                                      :password "Password123"}}})]
      (is (= 400 (:status response)))
      (is (= {:error "Email is required"} (:body response)))))
  (testing "invalid email"
    (let [response (auth.handlers/login-response {:parameters {:body {:email "invalid"
                                                                      :password "Password123"}}})]
      (is (= 400 (:status response)))
      (is (= {:error "Email is invalid"} (:body response)))))
  (testing "missing password"
    (let [response (auth.handlers/login-response {:parameters {:body {:email "user@example.com"
                                                                      :password "    "}}})]
      (is (= 400 (:status response)))
      (is (= {:error "Password is required"} (:body response))))))

(deftest login-response-authenticates-user
  (let [user {:uuid "user-1"
              :name "Test User"
              :email "user@example.com"
              :role "user"
              :password_hash "hash"}
        cookie-holder (atom nil)
        token-payload {:token "jwt-token"
                       :token-type "Bearer"
                       :expires-at "2024-01-01T00:00:00Z"
                       :expires-in 3600}]
    (with-redefs [db/query-one (fn [[_ email]]
                                 (is (= "user@example.com" email))
                                 user)
                  auth/verify-password (fn [password password-hash]
                                         (is (= "Password123" password))
                                         (is (= "hash" password-hash))
                                         true)
                  auth/issue-token (fn [_] token-payload)
                  refresh-tokens/issue-token-for-user! (fn [uuid]
                                                         (is (= "user-1" uuid))
                                                         {:token "refresh-token"})
                  refresh-tokens/build-refresh-cookie (fn [_]
                                                        {:value "refresh-token"
                                                         :path "/"
                                                         :max-age 10})
                  refresh-tokens/cookie->opts (fn [cookie]
                                                {:value (:value cookie)
                                                 :opts {:path (:path cookie)
                                                        :max-age (:max-age cookie)}})
                  response/set-cookie (fn [resp name value opts]
                                        (reset! cookie-holder {:name name :value value :opts opts})
                                        (assoc resp ::cookie {:name name :value value :opts opts}))]
      (let [response (auth.handlers/login-response {:parameters {:body {:email "user@example.com"
                                                                        :password "Password123"}}})
            body (:body response)]
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
        (is (= refresh-tokens/cookie-name (:name (::cookie response))))
        (is (= "refresh-token" (:value (::cookie response))))
        (is (= "refresh-token" (:value @cookie-holder)))
        (is (= refresh-tokens/cookie-name (:name @cookie-holder)))))))

(deftest register-response-derives-name
  (let [captured (atom nil)]
    (with-redefs [user-handlers/add-user-response (fn [request]
                                                    (reset! captured request)
                                                    {:status 201 :body {:user "created"}})]
      (let [response (auth.handlers/register-response {:parameters {:body {:name "   "
                                                                           :email "new.user@example.com"
                                                                           :password "SecretPass"}}})]
        (is (= 201 (:status response)))
        (is (= {:user "created"} (:body response)))
        (is (= {:parameters {:body {:name "new.user"
                                    :age 0
                                    :email "new.user@example.com"
                                    :password "SecretPass"}}
                :body-params {:name "new.user"
                              :age 0
                              :email "new.user@example.com"
                              :password "SecretPass"}}
               @captured))))))

(deftest refresh-response-requires-token
  (let [response (auth.handlers/refresh-response {:cookies {refresh-tokens/cookie-name {:value "   "}}})]
    (is (= 401 (:status response)))
    (is (= {:error "Refresh token is missing"} (:body response)))))

(deftest refresh-response-rotates-token
  (let [cookie-holder (atom nil)
        user-id (str (UUID/randomUUID))
        user {:uuid user-id
              :name "Sample"
              :email "sample@example.com"
              :role "user"}
        token-payload {:token "jwt-token"
                       :token-type "Bearer"
                       :expires-at "2024-01-01T00:00:00Z"
                       :expires-in 3600}]
    (with-redefs [refresh-tokens/rotate-token! (fn [token]
                                                 (is (= "old-refresh" token))
                                                 {:user-uuid user-id
                                                  :refresh {:token "new-refresh"}})
                  db/query-one (fn [[_ uuid-value]]
                                 (is (= (UUID/fromString user-id) uuid-value))
                                 user)
                  auth/issue-token (fn [_] token-payload)
                  refresh-tokens/build-refresh-cookie (fn [_]
                                                        {:value "new-refresh"
                                                         :path "/"
                                                         :max-age 20})
                  refresh-tokens/cookie->opts (fn [cookie]
                                                {:value (:value cookie)
                                                 :opts {:path (:path cookie)
                                                        :max-age (:max-age cookie)}})
                  response/set-cookie (fn [resp name value opts]
                                        (reset! cookie-holder {:name name :value value :opts opts})
                                        resp)]
      (let [response (auth.handlers/refresh-response {:cookies {refresh-tokens/cookie-name {:value " old-refresh "}}})
            body (:body response)]
        (is (= 200 (:status response)))
        (is (= "jwt-token" (:access_token body)))
        (is (= "Bearer" (:token_type body)))
        (is (= "2024-01-01T00:00:00Z" (:expires_at body)))
        (is (= 3600 (:expires_in body)))
        (is (= "new-refresh" (:value @cookie-holder)))
        (is (= refresh-tokens/cookie-name (:name @cookie-holder)))))))

(deftest refresh-response-clears-cookie-when-invalid
  (let [cookie-holder (atom nil)]
    (with-redefs [refresh-tokens/rotate-token! (constantly nil)
                  refresh-tokens/clear-refresh-cookie (fn []
                                                        {:value ""
                                                         :path "/"
                                                         :max-age 0})
                  refresh-tokens/cookie->opts (fn [cookie]
                                                {:value (:value cookie)
                                                 :opts {:path (:path cookie)}})
                  response/set-cookie (fn [resp name value opts]
                                        (reset! cookie-holder {:name name :value value :opts opts})
                                        resp)]
      (let [response (auth.handlers/refresh-response {:cookies {refresh-tokens/cookie-name {:value "expired"}}})]
        (is (= 401 (:status response)))
        (is (= {:error "Refresh token is invalid or expired"} (:body response)))
        (is (= refresh-tokens/cookie-name (:name @cookie-holder)))
        (is (= "" (:value @cookie-holder)))))))

(deftest list-refresh-tokens-response-validations
  (testing "missing uuid"
    (let [response (auth.handlers/list-refresh-tokens-response {:parameters {:path {:uuid "   "}}})]
      (is (= 400 (:status response)))
      (is (= {:error "User uuid is required"} (:body response)))))
  (testing "invalid uuid"
    (let [response (auth.handlers/list-refresh-tokens-response {:parameters {:path {:uuid "not-a-uuid"}}})]
      (is (= 400 (:status response)))
      (is (= {:error "Invalid uuid format"} (:body response)))))
  (testing "user not found"
    (with-redefs [db/query-one (constantly nil)]
      (let [response (auth.handlers/list-refresh-tokens-response {:parameters {:path {:uuid (str (UUID/randomUUID))}}})]
        (is (= 404 (:status response)))
        (is (= {:error "Not found"} (:body response)))))))

(deftest list-refresh-tokens-response-success
  (let [uuid (str (UUID/randomUUID))
        tokens [{:id "t1"} {:id "t2"}]]
    (with-redefs [db/query-one (fn [[_ value]]
                                 (is (= (UUID/fromString uuid) value))
                                 {:uuid uuid})
                  refresh-tokens/list-tokens-for-user (fn [value]
                                                        (is (= uuid value))
                                                        tokens)]
      (let [response (auth.handlers/list-refresh-tokens-response {:parameters {:path {:uuid uuid}}})]
        (is (= 200 (:status response)))
        (is (= tokens (:body response)))))))

(deftest revoke-refresh-token-response-validations
  (testing "blank inputs"
    (let [response (auth.handlers/revoke-refresh-token-response {:parameters {:path {:uuid "   "
                                                                                     :token-id ""}}})]
      (is (= 400 (:status response)))
      (is (= {:error "User uuid and token id are required"} (:body response)))))
  (testing "invalid uuid format"
    (let [response (auth.handlers/revoke-refresh-token-response {:parameters {:path {:uuid "bad"
                                                                                     :token-id "also-bad"}}})]
      (is (= 400 (:status response)))
      (is (= {:error "Invalid uuid format"} (:body response)))))
  (testing "token missing"
    (with-redefs [refresh-tokens/revoke-token! (constantly nil)]
      (let [uuid (str (UUID/randomUUID))
            token (str (UUID/randomUUID))
            response (auth.handlers/revoke-refresh-token-response {:parameters {:path {:uuid uuid
                                                                                       :token-id token}}})]
        (is (= 404 (:status response)))
        (is (= {:error "Refresh token not found"} (:body response)))))))

(deftest revoke-refresh-token-response-success
  (let [uuid (str (UUID/randomUUID))
        token (str (UUID/randomUUID))]
    (with-redefs [refresh-tokens/revoke-token! (fn [token-id user-uuid]
                                                 (is (= token token-id))
                                                 (is (= uuid user-uuid))
                                                 true)]
      (let [response (auth.handlers/revoke-refresh-token-response {:parameters {:path {:uuid uuid
                                                                                       :token-id token}}})]
        (is (= 200 (:status response)))
        (is (= {:status "revoked"} (:body response)))))))

(deftest logout-response-clears-cookie
  (let [revoked (atom nil)
        cookie-holder (atom nil)]
    (with-redefs [refresh-tokens/parse-token (fn [value]
                                               (is (= "token-value" value))
                                               {:id "token-id"})
                  refresh-tokens/revoke-token! (fn [token-id]
                                                 (reset! revoked token-id))
                  refresh-tokens/clear-refresh-cookie (fn []
                                                        {:value ""
                                                         :path "/"
                                                         :max-age 0})
                  refresh-tokens/cookie->opts (fn [cookie]
                                                {:value (:value cookie)
                                                 :opts {:path (:path cookie)}})
                  response/set-cookie (fn [resp name value opts]
                                        (reset! cookie-holder {:name name :value value :opts opts})
                                        resp)]
      (let [response (auth.handlers/logout-response {:cookies {refresh-tokens/cookie-name {:value "token-value"}}})]
        (is (= "token-id" @revoked))
        (is (= refresh-tokens/cookie-name (:name @cookie-holder)))
        (is (= "" (:value @cookie-holder)))
        (is (= {:status "signed-out"} (:body response)))))))
