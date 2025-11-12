(ns acme.server.handlers.todos-test
  (:require
   [acme.server.handlers.todos :as todos]
   [acme.server.services.todo :as todo.service]
   [clojure.test :refer [deftest is testing]]))

(deftest list-response-invokes-service
  (let [identity {:uuid "user-123"}
        called (atom nil)
        todos-list [{:id "t1"}]]
    (with-redefs [todo.service/list-todos (fn [identity-arg]
                                            (reset! called identity-arg)
                                            todos-list)]
      (let [response (todos/list-response {:identity identity})]
        (is (= identity @called))
        (is (= 200 (:status response)))
        (is (= todos-list (:body response)))
        (is (= "application/json"
               (get-in response [:muuntaja/response :format])))))))

(deftest create-response-normalizes-title
  (let [record {:id "new-id" :title "Create tests" :completed true}
        captured (atom nil)]
    (with-redefs [todo.service/create-todo! (fn [identity payload]
                                              (reset! captured {:identity identity
                                                                :payload payload})
                                              record)]
      (let [response (todos/create-response {:identity {:uuid "user-1"}
                                             :parameters {:body {:title "  Create tests  "
                                                                 :completed true}}})]
        (is (= {:identity {:uuid "user-1"}
                :payload {:title "Create tests"
                          :completed true}}
               @captured))
        (is (= 201 (:status response)))
        (is (= record (:body response)))))))

(deftest create-response-errors-when-user-missing
  (with-redefs [todo.service/create-todo! (constantly nil)]
    (let [response (todos/create-response {:identity nil
                                           :parameters {:body {:title "Missing user"}}})]
      (is (= 403 (:status response)))
      (is (= {:error "Unable to determine current user"} (:body response)))))) 

(deftest update-response-validates-input
  (testing "empty body"
    (let [response (todos/update-response {:identity {:uuid "user"}
                                           :parameters {:path {:id "todo-id"}}})]
      (is (= 400 (:status response)))
      (is (= {:error "Supply at least one field to update"} (:body response)))))
  (testing "blank title"
    (let [response (todos/update-response {:identity {:uuid "user"}
                                           :parameters {:path {:id "todo-id"}
                                                        :body {:title "   "}}})]
      (is (= 400 (:status response)))
      (is (= {:error "Title is required"} (:body response))))))

(deftest update-response-trims-fields-and-updates
  (let [updated {:id "todo-id" :title "Updated" :completed false}
        captured (atom nil)]
    (with-redefs [todo.service/update-todo! (fn [identity id updates]
                                              (reset! captured {:identity identity
                                                                :id id
                                                                :updates updates})
                                              updated)]
      (let [response (todos/update-response {:identity {:uuid "user"}
                                             :parameters {:path {:id "todo-id"}
                                                          :body {:title "  Updated  "
                                                                 :completed false}}})]
        (is (= {:identity {:uuid "user"}
                :id "todo-id"
                :updates {:title "Updated"
                          :completed false}}
               @captured))
        (is (= 200 (:status response)))
        (is (= updated (:body response)))))))

(deftest update-response-not-found
  (with-redefs [todo.service/update-todo! (constantly nil)]
    (let [response (todos/update-response {:identity {:uuid "user"}
                                           :parameters {:path {:id "todo-id"}
                                                        :body {:completed true}}})]
      (is (= 404 (:status response)))
      (is (= {:error "Not found"} (:body response))))))

(deftest delete-response-success-and-miss
  (with-redefs [todo.service/delete-todo! (fn [_ _] 1)]
    (let [response (todos/delete-response {:identity {:uuid "user"}
                                           :parameters {:path {:id "todo-id"}}})]
      (is (= 200 (:status response)))
      (is (= {:status "deleted"} (:body response)))))
  (with-redefs [todo.service/delete-todo! (fn [_ _] 0)]
    (let [response (todos/delete-response {:identity {:uuid "user"}
                                           :parameters {:path {:id "todo-id"}}})]
      (is (= 404 (:status response)))
      (is (= {:error "Not found"} (:body response))))))
