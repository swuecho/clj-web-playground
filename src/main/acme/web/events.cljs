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
                 :uri "/users"
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
                       :uri "/users"
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
