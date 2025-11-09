(ns acme.server.handlers.users-test
  (:require
   [acme.server.handlers.users :as users]
   [clojure.test :refer [deftest is]])
  (:import
   (java.util UUID)))

(defn- suppress-duplicates [f]
  (with-redefs [users/email-conflict? (constantly false)
                users/user-exists? (constantly false)]
    (f)))

(deftest validate-user-normalizes-input
  (suppress-duplicates
   (fn []
     (let [{:keys [status user]} (#'users/validate-user {:name "  Ada Lovelace  "
                                                         :age "42"
                                                         :email "ADA@Example.com"
                                                         :password "  Th!sIsSecure  "})]
       (is (= 201 status))
       (is (= "Ada Lovelace" (:name user)))
       (is (= 42 (:age user)))
       (is (= "ada@example.com" (:email user)))
       (is (= "Th!sIsSecure" (:password user)))))))

(deftest validate-user-rejects-short-passwords
  (suppress-duplicates
   (fn []
     (let [{:keys [status message]} (#'users/validate-user {:name "Bob"
                                                             :age 20
                                                             :email "bob@example.com"
                                                             :password "short"})]
       (is (= 400 status))
       (is (= "Password must be at least 8 characters" message))))))

(deftest validate-update-requires-fields
  (let [{:keys [status message]} (#'users/validate-update (UUID/randomUUID) {})]
    (is (= 400 status))
    (is (= "Supply at least one field to update" message))))

(deftest validate-update-detects-email-conflicts
  (with-redefs [users/email-conflict? (fn [_ _] true)]
    (let [{:keys [status message]} (#'users/validate-update (UUID/randomUUID)
                                                            {:email "taken@example.com"})]
      (is (= 409 status))
      (is (= "Email is already in use" message)))))

(deftest validate-update-normalizes-fields
  (with-redefs [users/email-conflict? (constantly false)]
    (let [uuid (UUID/randomUUID)
          {:keys [status updates]} (#'users/validate-update uuid
                                                            {:name "  New Name  "
                                                             :age "41"
                                                             :email "NEW@example.com"
                                                             :password "  Password123  "})]
      (is (= 200 status))
      (is (= {:name "New Name"
              :age 41
              :email "new@example.com"
              :password "Password123"}
             updates)))))

(deftest build-update-sql-orders-parameters
  (let [{:keys [sql params]} (#'users/build-update-sql {:name "Jane"
                                                         :age 30
                                                         :email "jane@example.com"
                                                         :password-hash "hash"})]
    (is (re-find #"update \"UserTable\" set" sql))
    (is (re-find #"password_hash = \?" sql))
    (is (= ["Jane" 30 "jane@example.com" "hash"] params)))
  (is (nil? (#'users/build-update-sql {})))
  (is (= ["hash"] (:params (#'users/build-update-sql {:password-hash "hash"})))) )
