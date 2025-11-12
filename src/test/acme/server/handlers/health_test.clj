(ns acme.server.handlers.health-test
  (:require
   [acme.server.db :as db]
   [acme.server.handlers.health :as health]
   [clojure.test :refer [deftest is]]))

(deftest health-response-success
  (with-redefs [db/query-one (fn [sql]
                               (is (= ["select 1 as ok"] sql))
                               {:ok 1})]
    (let [response (health/health-response {})]
      (is (= 200 (:status response)))
      (is (= {:status "ok"} (:body response))))))

(deftest health-response-failure
  (with-redefs [db/query-one (fn [_] (throw (ex-info "boom" {})))]
    (let [response (health/health-response {})]
      (is (= 500 (:status response)))
      (is (= {:status "error"} (:body response))))))
