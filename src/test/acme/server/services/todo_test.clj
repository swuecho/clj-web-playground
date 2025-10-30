(ns acme.server.services.todo-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [acme.server.services.todo :as sut]))

(deftest list-todos-normalizes-completed-flag
  (with-redefs [acme.server.models.todo/all
                (fn []
                  [{:id 1 :title "Make coffee" :completed nil}
                   {:id 2 :title "Write tests" :completed true}])]
    (is (= [{:id 1 :title "Make coffee" :completed false}
            {:id 2 :title "Write tests" :completed true}]
           (sut/list-todos)))))

(deftest create-todo-normalizes-returned-instance
  (with-redefs [acme.server.models.todo/create!
                (fn [_]
                  {:id 42
                   :title "Ship documentation"
                   :completed nil})]
    (is (= {:id 42
            :title "Ship documentation"
            :completed false}
           (sut/create-todo! {:title "Ship documentation"})))))

(deftest update-todo-coerces-completed-value
  (let [captured (atom nil)]
    (with-redefs [acme.server.models.todo/update!
                  (fn [_ changes]
                    (reset! captured changes)
                    {:id 5
                     :title (:title changes)
                     :completed (:completed changes)})]
      (is (= {:id 5
              :title "Review PR"
              :completed true}
             (sut/update-todo! 5 {:title "Review PR" :completed 1})))
      (is (= {:title "Review PR" :completed 1}
             @captured)))))
