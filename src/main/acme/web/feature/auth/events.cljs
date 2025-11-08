(ns acme.web.feature.auth.events
  (:require
   [acme.web.feature.toast.events :as toast]
   [acme.web.feature.todos.events :as todo-events]
   [acme.web.feature.users.events :as user-events]
   [ajax.core :as ajax]
   [clojure.string :as str]
   [day8.re-frame.http-fx]
   [re-frame.core :as rf]))

(defn- normalize-email [value]
  (let [trimmed (some-> value str str/trim)]
    (when (seq trimmed)
      (str/lower-case trimmed))))

(defn- reset-auth [db]
  (-> db
      (assoc :isLoggedIn? false)
      (assoc :user nil)
      (assoc-in [:auth :token] nil)
      (assoc-in [:auth :expires-at] nil)
      (assoc-in [:auth :logging-in?] false)
      (assoc-in [:auth :error] nil)))

(rf/reg-event-fx
 ::login
 (fn [{:keys [db]} [_ {:keys [email password]}]]
   (let [normalized-email (normalize-email email)
         trimmed-password (some-> password str str/trim)]
     (cond
       (str/blank? (or email ""))
       {:db (assoc-in db [:auth :error] "Email is required")}

       (nil? normalized-email)
       {:db (assoc-in db [:auth :error] "Email is invalid")}

       (str/blank? trimmed-password)
       {:db (assoc-in db [:auth :error] "Password is required")}

       :else
       {:db (-> db
                (assoc-in [:auth :logging-in?] true)
                (assoc-in [:auth :error] nil))
        :http-xhrio {:method :post
                     :uri "/api/auth/login"
                     :timeout 8000
                     :headers {"Accept" "application/json"}
                     :params {:email normalized-email
                              :password trimmed-password}
                     :format (ajax/json-request-format)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [::login-success]
                     :on-failure [::login-failed]}})))

(rf/reg-event-fx
 ::login-success
 (fn [{:keys [db]} [_ {:keys [access_token expires_at user]}]]
   {:db (-> db
            (assoc :isLoggedIn? true)
            (assoc :user user)
            (assoc-in [:auth :token] access_token)
            (assoc-in [:auth :expires-at] expires_at)
            (assoc-in [:auth :logging-in?] false)
            (assoc-in [:auth :error] nil))
    :dispatch-n [[::user-events/fetch-users]
                 [::todo-events/fetch-todos]
                 [::toast/enqueue-toast {:message (str "Welcome back, " (or (:name user) (:email user)))
                                         :variant :success}]]}))

(rf/reg-event-fx
 ::login-failed
 (fn [{:keys [db]} [_ {:keys [status response status-text]}]]
   (let [message (or (:error response)
                     (cond
                       (= status 0) "Network error"
                       status (str "Login failed (" status ")" (when status-text (str ": " status-text)))
                       :else "Login failed"))]
     {:db (-> db
              (assoc-in [:auth :logging-in?] false)
              (assoc-in [:auth :error] message))
      :dispatch [::toast/enqueue-toast {:message message :variant :error}]})))

(rf/reg-event-fx
 ::logout
 (fn [{:keys [db]} [_ {:keys [reason silent?]}]]
   (let [next-db (reset-auth db)]
     (cond-> {:db next-db}
       (and reason (not silent?))
       (assoc :dispatch [::toast/enqueue-toast {:message reason :variant :info}]))))

(rf/reg-event-fx
 ::session-expired
 (fn [_ [_ message]]
   {:dispatch [::logout {:reason (or message "Session expired. Please sign in again")
                         :silent? false}]}))
