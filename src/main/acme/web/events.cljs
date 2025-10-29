(ns acme.web.events
  (:require
   [ajax.core :as ajax]
   [clojure.string :as str]
   [day8.re-frame.http-fx]
   [re-frame.core :as rf]
   [acme.web.db :as db]))

(rf/reg-event-db
 ::initialize
 (fn [_ _]
   db/default-db))

(rf/reg-event-fx
 ::fetch-users
 (fn [{:keys [db]} _]
   {:db (-> db
            (assoc :loading? true)
            (assoc :error nil))
    :http-xhrio {:method :get
                 :uri "/api/users"
                 :timeout 8000
                 :headers {"Accept" "application/json"}
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [::users-loaded]
                 :on-failure [::fetch-failed]}}))

(rf/reg-event-db
 ::users-loaded
 (fn [db [_ users]]
   (-> db
       (assoc :users users)
       (assoc :loading? false))))

(rf/reg-event-fx
 ::fetch-failed
 (fn [{:keys [db]} [_ {:keys [status status-text]}]]
   (let [msg (str "Request failed"
                  (when status (str " (" status ")"))
                  (when status-text (str ": " status-text)))]
     {:db (-> db
              (assoc :loading? false)
              (assoc :error msg))
      :dispatch [::enqueue-toast {:message msg :variant :error}]})))

(rf/reg-event-fx
 ::enqueue-toast
 (fn [{:keys [db]} [_ message]]
   (let [toast-entry (-> (if (map? message) message {:message message})
                         (update :variant #(or % :info)))
         {:keys [current]} (:toast db)]
     (if current
       {:db (update-in db [:toast :queue] conj toast-entry)}
       {:db (-> db
                (assoc-in [:toast :current] toast-entry)
                (assoc-in [:toast :queue] []))
        :dispatch-later [{:ms 3000 :dispatch [::hide-current-toast]}]}))))

(rf/reg-event-fx
 ::hide-current-toast
 (fn [{:keys [db]} _]
   (let [{:keys [queue]} (:toast db)
         next (first queue)
         remaining (vec (rest queue))]
     (if next
       {:db (-> db
                (assoc-in [:toast :current] next)
                (assoc-in [:toast :queue] remaining))
        :dispatch-later [{:ms 3000 :dispatch [::hide-current-toast]}]}
       {:db (-> db
                (assoc-in [:toast :current] nil)
                (assoc-in [:toast :queue] []))}))))

(rf/reg-event-fx
 ::dismiss-toast
 (fn [_ _]
   {:dispatch [::hide-current-toast]}))

(rf/reg-event-db
 ::open-add-user-dialog
 (fn [db _]
   (-> db
       (assoc-in [:add-user :visible?] true)
       (assoc-in [:add-user :name] "")
       (assoc-in [:add-user :age] "0")
       (assoc-in [:add-user :submitting?] false)
       (assoc-in [:add-user :errors] {})
       (assoc :error nil))))

(rf/reg-event-db
 ::close-add-user-dialog
 (fn [db _]
   (-> db
       (assoc-in [:add-user :visible?] false)
       (assoc-in [:add-user :submitting?] false)
       (assoc-in [:add-user :errors] {}))))

(rf/reg-event-db
 ::update-add-user-field
 (fn [db [_ field value]]
   (-> db
       (assoc-in [:add-user field] value)
       (update-in [:add-user :errors] dissoc field))))

(rf/reg-event-fx
 ::add-user
 (fn [{:keys [db]} _]
   (let [{:keys [name age]} (:add-user db)
         trimmed-name (str/trim name)
         age-str (str/trim age)
         parsed-age (js/parseInt age-str 10)
         invalid-age? (or (str/blank? age-str)
                          (js/isNaN parsed-age)
                          (neg? parsed-age))
         errors (cond-> {}
                 (str/blank? trimmed-name) (assoc :name "Name is required")
                 invalid-age? (assoc :age "Age must be a non-negative number"))]
     (if (seq errors)
       {:db (assoc-in db [:add-user :errors] errors)}
       (let [user {:uuid (str (random-uuid))
                   :name trimmed-name
                   :age parsed-age}]
         {:db (-> db
                  (assoc :error nil)
                  (assoc-in [:add-user :errors] {})
                  (assoc-in [:add-user :submitting?] true))
          :http-xhrio {:method :post
                       :uri "/api/users"
                       :timeout 8000
                       :headers {"Content-Type" "application/json"
                                 "Accept" "application/json"}
                       :params user
                       :format (ajax/json-request-format)
                       :response-format (ajax/json-response-format {:keywords? true})
                       :on-success [::user-added]
                       :on-failure [::add-user-failed]}})))))

(rf/reg-event-fx
 ::user-added
 (fn [{:keys [db]} [_ _response]]
   {:db (-> db
            (assoc :loading? false)
            (assoc-in [:add-user :visible?] false)
            (assoc-in [:add-user :submitting?] false)
            (assoc-in [:add-user :name] "")
            (assoc-in [:add-user :age] "0")
            (assoc-in [:add-user :errors] {}))
    :dispatch-n [[::fetch-users]
                 [::enqueue-toast {:message "User added successfully"
                                    :variant :success}]]}))

(rf/reg-event-fx
 ::add-user-failed
 (fn [{:keys [db]} [_ {:keys [status status-text]}]]
   (let [msg (str "Add user failed"
                  (when status (str " (" status ")"))
                  (when status-text (str ": " status-text)))]
     {:db (-> db
              (assoc :loading? false)
              (assoc-in [:add-user :submitting?] false)
              (assoc :error msg))
      :dispatch [::enqueue-toast {:message msg :variant :error}]})))

(rf/reg-event-fx
 ::open-edit-user-dialog
 (fn [{:keys [db]} [_ uuid]]
   (if-let [user (some #(when (= uuid (:uuid %)) %) (:users db))]
     {:db (-> db
              (assoc-in [:edit-user :visible?] true)
              (assoc-in [:edit-user :uuid] (:uuid user))
              (assoc-in [:edit-user :name] (or (:name user) ""))
              (assoc-in [:edit-user :age] (if (some? (:age user)) (str (:age user)) ""))
              (assoc-in [:edit-user :errors] {})
              (assoc-in [:edit-user :submitting?] false)
              (assoc-in [:edit-user :initial] {:name (:name user)
                                               :age (:age user)}))}
     {:db db
      :dispatch [::enqueue-toast {:message "User not found"
                                  :variant :error}]})))

(rf/reg-event-db
 ::close-edit-user-dialog
 (fn [db _]
   (-> db
       (assoc-in [:edit-user :visible?] false)
       (assoc-in [:edit-user :uuid] nil)
       (assoc-in [:edit-user :name] "")
       (assoc-in [:edit-user :age] "0")
       (assoc-in [:edit-user :errors] {})
       (assoc-in [:edit-user :submitting?] false)
       (assoc-in [:edit-user :initial] {:name ""
                                        :age 0}))))

(rf/reg-event-db
 ::update-edit-user-field
 (fn [db [_ field value]]
   (-> db
       (assoc-in [:edit-user field] value)
       (update-in [:edit-user :errors] dissoc field))))

(defn- parse-age [age-str]
  (let [trimmed (str/trim (or age-str ""))]
    (when-not (str/blank? trimmed)
      (let [parsed (js/parseInt trimmed 10)]
        (when-not (js/isNaN parsed)
          parsed)))))

(rf/reg-event-fx
 ::update-user
 (fn [{:keys [db]} _]
   (let [{:keys [uuid name age initial]} (:edit-user db)
         uuid (some-> uuid str/trim)
         raw-name (or name "")
         trimmed-name (str/trim raw-name)
         age-str (str/trim (or age ""))
         parsed-age (parse-age age-str)
         initial-name (or (:name initial) "")
         initial-age (:age initial)
         name-changed? (not= trimmed-name initial-name)
         name-invalid? (and name-changed?
                            (str/blank? trimmed-name))
         age-provided? (not (str/blank? age-str))
         age-invalid? (and age-provided?
                           (or (nil? parsed-age)
                               (neg? parsed-age)))
         age-changed? (and age-provided?
                           (not age-invalid?)
                           (not= parsed-age initial-age))
         updates (cond-> {}
                   (and name-changed? (not name-invalid?)) (assoc :name trimmed-name)
                   age-changed? (assoc :age parsed-age))
         errors (cond-> {}
                  name-invalid? (assoc :name "Name is required")
                  age-invalid? (assoc :age "Age must be a non-negative number"))]
     (cond
       (str/blank? uuid)
       {:dispatch [::enqueue-toast {:message "User id missing"
                                    :variant :error}]}

       (seq errors)
       {:db (assoc-in db [:edit-user :errors] errors)}

       (empty? updates)
       {:dispatch [::enqueue-toast {:message "No changes to save"
                                    :variant :info}]}

       :else
       {:db (-> db
                (assoc :error nil)
                (assoc-in [:edit-user :errors] {})
                (assoc-in [:edit-user :submitting?] true))
        :http-xhrio {:method :patch
                     :uri (str "/api/users/" uuid)
                     :timeout 8000
                     :headers {"Content-Type" "application/json"
                               "Accept" "application/json"}
                     :params updates
                     :format (ajax/json-request-format)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [::user-updated uuid]
                     :on-failure [::update-user-failed uuid updates]}}))))

(rf/reg-event-fx
 ::user-updated
 (fn [{:keys [db]} [_ uuid _response]]
   {:db (-> db
            (assoc-in [:edit-user :visible?] false)
            (assoc-in [:edit-user :uuid] nil)
            (assoc-in [:edit-user :name] "")
            (assoc-in [:edit-user :age] "0")
            (assoc-in [:edit-user :errors] {})
            (assoc-in [:edit-user :submitting?] false)
            (assoc-in [:edit-user :initial] {:name ""
                                             :age 0}))
    :dispatch-n [[::fetch-users]
                 [::enqueue-toast {:message "User updated"
                                    :variant :success}]]}))

(rf/reg-event-fx
 ::update-user-failed
 (fn [{:keys [db]} [_ uuid _updates {:keys [status status-text]}]]
   (let [msg (str "Update failed"
                  (when status (str " (" status ")"))
                  (when status-text (str ": " status-text)))]
     {:db (-> db
              (assoc :error msg)
              (assoc-in [:edit-user :submitting?] false))
      :dispatch [::enqueue-toast {:message msg :variant :error}]})))

(rf/reg-event-fx
 ::delete-user
 (fn [{:keys [db]} [_ uuid]]
   (let [uuid (some-> uuid str/trim)]
     (if (str/blank? uuid)
       {:dispatch [::enqueue-toast {:message "User id missing"
                                    :variant :error}]}
       {:db (update db :pending-deletes conj uuid)
        :http-xhrio {:method :delete
                     :uri (str "/api/users/" uuid)
                     :timeout 8000
                     :headers {"Accept" "application/json"}
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [::user-deleted uuid]
                     :on-failure [::delete-user-failed uuid]}}))))

(rf/reg-event-fx
 ::user-deleted
 (fn [{:keys [db]} [_ uuid _response]]
   {:db (update db :pending-deletes disj uuid)
    :dispatch-n [[::fetch-users]
                 [::enqueue-toast {:message "User deleted"
                                    :variant :success}]]}))

(rf/reg-event-fx
 ::delete-user-failed
 (fn [{:keys [db]} [_ uuid {:keys [status status-text]}]]
   (let [msg (str "Delete failed"
                  (when status (str " (" status ")"))
                  (when status-text (str ": " status-text)))]
     {:db (-> db
              (assoc :error msg)
              (update :pending-deletes disj uuid))
      :dispatch [::enqueue-toast {:message msg :variant :error}]})))
