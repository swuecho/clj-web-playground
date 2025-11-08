(ns acme.server.services.todo-test
  (:require
   [clojure.test :refer [deftest is]]
   [acme.server.services.todo :as sut])
  (:import
   (java.util UUID)))

(deftest list-todos-normalizes-completed-flag
  (with-redefs [acme.server.models.todo/all
                (fn []
                  [{:id 1 :title "Make coffee" :completed nil}
                   {:id 2 :title "Write tests" :completed true}])]
    (is (= [{:id 1 :title "Make coffee" :completed false :user_id nil}
            {:id 2 :title "Write tests" :completed true :user_id nil}]
           (sut/list-todos {:role "admin"})))))

(deftest create-todo-normalizes-returned-instance
  (let [user-id (str (UUID/randomUUID))]
    (with-redefs [acme.server.models.todo/create!
                  (fn [_]
                    {:id 42
                     :title "Ship documentation"
                     :completed nil
                     :user_id (UUID/fromString user-id)})]
      (is (= {:id 42
              :title "Ship documentation"
              :completed false
              :user_id user-id}
             (sut/create-todo! {:sub user-id}
                               {:title "Ship documentation"}))))))

(deftest update-todo-coerces-completed-value
  (let [captured (atom nil)
        user-id (UUID/randomUUID)
        identity {:sub (str user-id)}]
    (with-redefs [acme.server.models.todo/fetch (fn [_] {:id 5 :user_id user-id})
                  acme.server.models.todo/update!
                  (fn [_ changes]
                    (reset! captured changes)
                    {:id 5
                     :title (:title changes)
                     :completed (:completed changes)
                     :user_id user-id})]
      (is (= {:id 5
              :title "Review PR"
              :completed true
              :user_id (str user-id)}
             (sut/update-todo! identity 5 {:title "Review PR" :completed 1})))
      (is (= {:title "Review PR" :completed 1}
             @captured)))))
