(ns acme.web.feature.users.events
  (:require
   [acme.web.db :as db]
   [acme.web.feature.auth.events :as auth-events]
   [acme.web.feature.toast.events :as toast]
   [acme.web.http :as http]
   [ajax.core :as ajax]
   [clojure.string :as str]
   [day8.re-frame.http-fx]
   [re-frame.core :as rf]))

(def email-regex
  #"^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$")

(def password-min-length 8)

(defn- normalize-email [value]
  (let [trimmed (some-> value str str/trim)]
    (when (seq trimmed)
      (str/lower-case trimmed))))

(defn- valid-email? [value]
  (boolean
   (when-let [normalized (normalize-email value)]
     (re-matches email-regex normalized))))

(defn- unauthorized? [status]
  (= status 401))

(rf/reg-event-fx
 ::fetch-users
 (fn [{:keys [db]} _]
   (let [headers (http/authorized-headers db)]
     {:db (-> db
              (assoc :loading? true)
              (assoc :error nil))
      :http-xhrio {:method :get
                   :uri "/api/users"
                   :timeout 8000
                   :headers headers
                   :response-format (ajax/json-response-format {:keywords? true})
                   :on-success [::users-loaded]
                   :on-failure [::fetch-failed]}})))

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
                  (when status-text (str ": " status-text)))
         unauthorized (unauthorized? status)]
     (cond-> {:db (-> db
                      (assoc :loading? false)
                      (assoc :error msg))}
       unauthorized (assoc :dispatch [::auth-events/session-expired nil])
       (not unauthorized) (assoc :dispatch [::toast/enqueue-toast {:message msg :variant :error}])))))

(rf/reg-event-db
 ::open-add-user-dialog
 (fn [db _]
   (-> db
       (assoc-in [:add-user :visible?] true)
       (assoc-in [:add-user :name] "")
       (assoc-in [:add-user :age] "0")
       (assoc-in [:add-user :email] "")
       (assoc-in [:add-user :password] "")
       (assoc-in [:add-user :submitting?] false)
       (assoc-in [:add-user :errors] {})
       (assoc :error nil))))

(rf/reg-event-db
 ::close-add-user-dialog
 (fn [db _]
   (-> db
       (assoc-in [:add-user :visible?] false)
       (assoc-in [:add-user :submitting?] false)
       (assoc-in [:add-user :email] "")
       (assoc-in [:add-user :password] "")
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
   (let [{:keys [name age email password]} (:add-user db)
         trimmed-name (str/trim name)
         age-str (str/trim age)
         parsed-age (js/parseInt age-str 10)
         invalid-age? (or (str/blank? age-str)
                          (js/isNaN parsed-age)
                          (neg? parsed-age))
         normalized-email (normalize-email email)
         password-str (str/trim (or password ""))
         email-blank? (str/blank? (or email ""))
         invalid-email? (and (not email-blank?) (not (valid-email? email)))
         short-password? (< (count password-str) password-min-length)
         errors (cond-> {}
                 (str/blank? trimmed-name) (assoc :name "Name is required")
                 invalid-age? (assoc :age "Age must be a non-negative number")
                 email-blank? (assoc :email "Email is required")
                 (and (not email-blank?) invalid-email?) (assoc :email "Email is invalid")
                 (str/blank? password-str) (assoc :password "Password is required")
                 (and (not (str/blank? password-str)) short-password?)
                 (assoc :password (str "Password must be at least " password-min-length " characters")))]
     (if (seq errors)
       {:db (assoc-in db [:add-user :errors] errors)}
       (let [headers (http/authorized-headers db)
             user {:uuid (str (random-uuid))
                   :name trimmed-name
                   :age parsed-age
                   :email normalized-email
                   :password password-str}]
         {:db (-> db
                  (assoc :error nil)
                  (assoc-in [:add-user :errors] {})
                  (assoc-in [:add-user :submitting?] true))
          :http-xhrio {:method :post
                       :uri "/api/users"
                       :timeout 8000
                       :headers headers
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
            (assoc-in [:add-user :email] "")
            (assoc-in [:add-user :password] "")
            (assoc-in [:add-user :errors] {}))
    :dispatch-n [[::fetch-users]
                 [::toast/enqueue-toast {:message "User added successfully"
                                         :variant :success}]]}))

(rf/reg-event-fx
 ::add-user-failed
 (fn [{:keys [db]} [_ {:keys [status status-text]}]]
   (let [msg (str "Add user failed"
                  (when status (str " (" status ")"))
                  (when status-text (str ": " status-text)))]
      (cond-> {:db (-> db
                       (assoc :loading? false)
                       (assoc-in [:add-user :submitting?] false)
                       (assoc :error msg))}
        (unauthorized? status) (assoc :dispatch [::auth-events/session-expired nil])
        (not (unauthorized? status))
        (assoc :dispatch [::toast/enqueue-toast {:message msg :variant :error}])))))

(rf/reg-event-fx
 ::open-edit-user-dialog
 (fn [{:keys [db]} [_ uuid]]
  (if-let [user (some #(when (= uuid (:uuid %)) %) (:users db))]
    {:db (-> db
             (assoc-in [:edit-user :visible?] true)
             (assoc-in [:edit-user :uuid] (:uuid user))
              (assoc-in [:edit-user :name] (or (:name user) ""))
              (assoc-in [:edit-user :age] (if (some? (:age user)) (str (:age user)) ""))
              (assoc-in [:edit-user :email] (or (:email user) ""))
              (assoc-in [:edit-user :password] "")
              (assoc-in [:edit-user :errors] {})
              (assoc-in [:edit-user :submitting?] false)
              (assoc-in [:edit-user :initial] {:name (:name user)
                                               :age (:age user)
                                               :email (:email user)}))}
     {:db db
      :dispatch [::toast/enqueue-toast {:message "User not found"
                                        :variant :error}]})))

(rf/reg-event-db
 ::close-edit-user-dialog
 (fn [db _]
   (-> db
       (assoc-in [:edit-user :visible?] false)
       (assoc-in [:edit-user :uuid] nil)
       (assoc-in [:edit-user :name] "")
       (assoc-in [:edit-user :age] "0")
       (assoc-in [:edit-user :email] "")
       (assoc-in [:edit-user :password] "")
       (assoc-in [:edit-user :errors] {})
       (assoc-in [:edit-user :submitting?] false)
       (assoc-in [:edit-user :initial] {:name ""
                                         :age 0
                                         :email ""}))))

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
   (let [{:keys [uuid name age email password initial]} (:edit-user db)
         uuid (some-> uuid str/trim)
         raw-name (or name "")
         trimmed-name (str/trim raw-name)
         age-str (str/trim (or age ""))
         parsed-age (parse-age age-str)
         initial-name (or (:name initial) "")
         initial-age (:age initial)
         initial-email (normalize-email (:email initial))
         normalized-email (normalize-email email)
         email-empty? (str/blank? (or email ""))
         email-invalid? (and (not email-empty?) (not (valid-email? email)))
         email-changed? (and normalized-email
                             (not= normalized-email initial-email))
         password-str (str/trim (or password ""))
         password-present? (seq password-str)
         password-invalid? (and password-present?
                             (< (count password-str) password-min-length))
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
                   age-changed? (assoc :age parsed-age)
                   email-changed? (assoc :email normalized-email)
                   (and password-present? (not password-invalid?)) (assoc :password password-str))
         errors (cond-> {}
                  name-invalid? (assoc :name "Name is required")
                  age-invalid? (assoc :age "Age must be a non-negative number")
                  (or email-empty? email-invalid?) (assoc :email (if email-empty?
                                                                   "Email is required"
                                                                   "Email is invalid"))
                  password-invalid? (assoc :password (str "Password must be at least " password-min-length " characters")))]
     (cond
       (str/blank? uuid)
       {:dispatch [::toast/enqueue-toast {:message "User id missing"
                                          :variant :error}]}

       (seq errors)
       {:db (assoc-in db [:edit-user :errors] errors)}

       (empty? updates)
       {:dispatch [::toast/enqueue-toast {:message "No changes to save"
                                          :variant :info}]}

       :else
       (let [headers (http/authorized-headers db)]
         {:db (-> db
                  (assoc :error nil)
                  (assoc-in [:edit-user :errors] {})
                  (assoc-in [:edit-user :submitting?] true))
          :http-xhrio {:method :patch
                       :uri (str "/api/users/" uuid)
                       :timeout 8000
                       :headers headers
                       :params updates
                       :format (ajax/json-request-format)
                       :response-format (ajax/json-response-format {:keywords? true})
                       :on-success [::user-updated uuid]
                       :on-failure [::update-user-failed uuid updates]}})))))

(rf/reg-event-fx
 ::user-updated
 (fn [{:keys [db]} [_ uuid _response]]
  {:db (-> db
           (assoc-in [:edit-user :visible?] false)
           (assoc-in [:edit-user :uuid] nil)
           (assoc-in [:edit-user :name] "")
           (assoc-in [:edit-user :age] "0")
           (assoc-in [:edit-user :email] "")
           (assoc-in [:edit-user :password] "")
           (assoc-in [:edit-user :errors] {})
           (assoc-in [:edit-user :submitting?] false)
           (assoc-in [:edit-user :initial] {:name ""
                                             :age 0
                                             :email ""}))
    :dispatch-n [[::fetch-users]
                 [::toast/enqueue-toast {:message "User updated"
                                         :variant :success}]]}))

(rf/reg-event-fx
 ::update-user-failed
 (fn [{:keys [db]} [_ uuid _updates {:keys [status status-text]}]]
   (let [msg (str "Update failed"
                  (when status (str " (" status ")"))
                  (when status-text (str ": " status-text)))]
      (cond-> {:db (-> db
                       (assoc :error msg)
                       (assoc-in [:edit-user :submitting?] false))}
        (unauthorized? status) (assoc :dispatch [::auth-events/session-expired nil])
        (not (unauthorized? status))
        (assoc :dispatch [::toast/enqueue-toast {:message msg :variant :error}])))))

(rf/reg-event-fx
 ::delete-user
 (fn [{:keys [db]} [_ uuid]]
   (let [uuid (some-> uuid str/trim)]
     (if (str/blank? uuid)
       {:dispatch [::toast/enqueue-toast {:message "User id missing"
                                          :variant :error}]}
       (let [headers (http/authorized-headers db)]
         {:db (update db :pending-deletes conj uuid)
          :http-xhrio {:method :delete
                       :uri (str "/api/users/" uuid)
                       :timeout 8000
                       :headers headers
                       :format (ajax/url-request-format)
                       :response-format (ajax/json-response-format {:keywords? true})
                       :on-success [::user-deleted uuid]
                       :on-failure [::delete-user-failed uuid]}})))))

(rf/reg-event-fx
 ::user-deleted
 (fn [{:keys [db]} [_ uuid _response]]
   {:db (update db :pending-deletes disj uuid)
    :dispatch-n [[::fetch-users]
                 [::toast/enqueue-toast {:message "User deleted"
                                         :variant :success}]]}))

(rf/reg-event-fx
 ::delete-user-failed
 (fn [{:keys [db]} [_ uuid {:keys [status status-text]}]]
   (let [msg (str "Delete failed"
                  (when status (str " (" status ")"))
                  (when status-text (str ": " status-text)))]
      (cond-> {:db (-> db
                       (assoc :error msg)
                       (update :pending-deletes disj uuid))}
        (unauthorized? status) (assoc :dispatch [::auth-events/session-expired nil])
        (not (unauthorized? status))
        (assoc :dispatch [::toast/enqueue-toast {:message msg :variant :error}])))))
