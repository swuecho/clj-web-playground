(ns acme.web.feature.auth.events
  (:require
   [acme.web.feature.toast.events :as toast]
   [acme.web.feature.todos.events :as todo-events]
   [acme.web.feature.users.events :as user-events]
   [acme.web.storage :as storage]
   [ajax.core :as ajax]
   [clojure.string :as str]
   [day8.re-frame.http-fx]
   [re-frame.core :as rf]))

(defn- normalize-email [value]
  (let [trimmed (some-> value str str/trim)]
    (when (seq trimmed)
      (str/lower-case trimmed))))

(def password-min-length 8)

(def default-register-state
  {:submitting? false
   :error nil})

(defn- expired? [iso]
  (try
    (if (seq iso)
      (let [expires (.getTime (js/Date. iso))
            now (.now js/Date.)]
        (<= expires now))
      false)
    (catch :default _
      true)))

(defn- refresh-available? [db]
  (let [token (get-in db [:auth :refresh-token])
        expires (get-in db [:auth :refresh-expires-at])]
    (and (string? token)
         (not (str/blank? token))
         (not (expired? expires))
         (not (get-in db [:auth :refreshing?])))))

(defn- reset-auth [db]
  (-> db
      (assoc :isLoggedIn? false)
      (assoc :user nil)
      (assoc-in [:auth :token] nil)
      (assoc-in [:auth :expires-at] nil)
      (assoc-in [:auth :refresh-token] nil)
      (assoc-in [:auth :refresh-expires-at] nil)
      (assoc-in [:auth :logging-in?] false)
      (assoc-in [:auth :refreshing?] false)
      (assoc-in [:auth :error] nil)
      (assoc-in [:auth :mode] :login)
      (assoc-in [:auth :register] default-register-state)))

(defn- hydrate-auth [db {:keys [token expires-at refresh-token refresh-expires-at user]}]
  (-> db
      (assoc :isLoggedIn? (boolean token))
      (assoc :user user)
      (assoc-in [:auth :token] token)
      (assoc-in [:auth :expires-at] expires-at)
      (assoc-in [:auth :refresh-token] refresh-token)
      (assoc-in [:auth :refresh-expires-at] refresh-expires-at)
      (assoc-in [:auth :logging-in?] false)
      (assoc-in [:auth :refreshing?] false)
      (assoc-in [:auth :error] nil)
      (assoc-in [:auth :mode] :login)
      (assoc-in [:auth :register] default-register-state)))

(defn- admin-user? [user]
  (= "admin" (:role user)))

(rf/reg-fx
 ::persist-auth
 (fn [snapshot]
   (if snapshot
     (storage/save-auth! snapshot)
     (storage/clear-auth!))))

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

      (< (count trimmed-password) password-min-length)
      {:db (assoc-in db [:auth :error]
                     (str "Password must be at least " password-min-length " characters"))}

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
                     :on-failure [::login-failed]}}))))

(rf/reg-event-fx
 ::login-success
 (fn [{:keys [db]} [_ {:keys [access_token expires_at refresh_token refresh_expires_at user]}]]
   (let [admin? (admin-user? user)
         welcome (str "Welcome back, " (or (:name user) (:email user)))
         dispatches (cond-> []
                       admin? (conj [::user-events/fetch-users])
                       true (conj [::todo-events/fetch-todos])
                       true (conj [::toast/enqueue-toast {:message welcome
                                                           :variant :success}]))]
     {:db (-> db
              (assoc :isLoggedIn? true)
              (assoc :user user)
              (assoc-in [:auth :token] access_token)
              (assoc-in [:auth :expires-at] expires_at)
              (assoc-in [:auth :refresh-token] refresh_token)
              (assoc-in [:auth :refresh-expires-at] refresh_expires_at)
              (assoc-in [:auth :logging-in?] false)
              (assoc-in [:auth :refreshing?] false)
              (assoc-in [:auth :error] nil))
      :dispatch-n dispatches
      ::persist-auth {:token access_token
                      :expires-at expires_at
                      :refresh-token refresh_token
                      :refresh-expires-at refresh_expires_at
                      :user user}})))

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

(rf/reg-event-db
 ::set-auth-mode
 (fn [db [_ mode]]
   (let [allowed? (#{:login :register} mode)
         next-mode (if allowed? mode :login)
         next-db (assoc-in db [:auth :mode] next-mode)]
     (if (= next-mode :register)
       (-> next-db
           (assoc-in [:auth :register :error] nil)
           (assoc-in [:auth :register :submitting?] false)
           (assoc-in [:auth :error] nil))
        next-db))))

(rf/reg-event-db
 ::clear-register-state
 (fn [db _]
   (assoc-in db [:auth :register] default-register-state)))

(rf/reg-event-fx
 ::register
 (fn [{:keys [db]} [_ {:keys [email password confirm-password]}]]
   (let [normalized-email (normalize-email email)
         trimmed-password (some-> password str str/trim)
         trimmed-confirm (some-> confirm-password str str/trim)
         error-response (fn [message]
                          {:db (-> db
                                   (assoc-in [:auth :register :submitting?] false)
                                   (assoc-in [:auth :register :error] message))})]
     (cond
       (str/blank? (or email ""))
       (error-response "Email is required")

       (nil? normalized-email)
       (error-response "Email is invalid")

       (str/blank? (or trimmed-password ""))
       (error-response "Password is required")

       (< (count trimmed-password) password-min-length)
       (error-response (str "Password must be at least " password-min-length " characters"))

       (not= trimmed-password trimmed-confirm)
       (error-response "Passwords do not match")

       :else
       {:db (-> db
                (assoc-in [:auth :register :submitting?] true)
                (assoc-in [:auth :register :error] nil))
        :http-xhrio {:method :post
                     :uri "/api/auth/register"
                     :timeout 8000
                     :headers {"Accept" "application/json"}
                     :params {:email normalized-email
                              :password trimmed-password}
                     :format (ajax/json-request-format)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [::register-success normalized-email]
                     :on-failure [::register-failed]}}))))

(rf/reg-event-fx
 ::register-success
 (fn [{:keys [db]} [_ email _response]]
   {:db (-> db
            (assoc-in [:auth :register] default-register-state)
            (assoc-in [:auth :mode] :login))
    :dispatch [::toast/enqueue-toast {:message (str "Account created for " email ". Please log in.")
                                      :variant :success}]}))

(rf/reg-event-fx
 ::register-failed
 (fn [{:keys [db]} [_ {:keys [status response status-text]}]]
   (let [message (or (:error response)
                     (cond
                       (= status 0) "Network error"
                       status (str "Registration failed (" status ")" (when status-text (str ": " status-text)))
                       :else "Registration failed"))]
     {:db (-> db
              (assoc-in [:auth :register :submitting?] false)
              (assoc-in [:auth :register :error] message))
      :dispatch [::toast/enqueue-toast {:message message :variant :error}]})))

(rf/reg-event-fx
 ::attempt-refresh
 (fn [{:keys [db]} _]
   (let [refresh-token (get-in db [:auth :refresh-token])
         expires-at (get-in db [:auth :refresh-expires-at])]
     (if (or (str/blank? refresh-token)
             (expired? expires-at))
       {:dispatch [::logout {:reason "Session expired. Please sign in again"
                             :silent? false}]}
       {:db (-> db
                (assoc-in [:auth :refreshing?] true)
                (assoc-in [:auth :error] nil))
        :http-xhrio {:method :post
                     :uri "/api/auth/refresh"
                     :timeout 8000
                     :headers {"Accept" "application/json"}
                     :params {:refresh_token refresh-token}
                     :format (ajax/json-request-format)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [::refresh-success]
                     :on-failure [::refresh-failed]}}))))

(rf/reg-event-fx
 ::refresh-success
 (fn [{:keys [db]} [_ {:keys [access_token expires_at refresh_token refresh_expires_at user]}]]
   (let [admin? (admin-user? user)
         dispatches (cond-> []
                       true (conj [::todo-events/fetch-todos])
                       admin? (conj [::user-events/fetch-users]))]
     {:db (-> db
              (assoc :isLoggedIn? true)
              (assoc :user user)
              (assoc-in [:auth :token] access_token)
              (assoc-in [:auth :expires-at] expires_at)
              (assoc-in [:auth :refresh-token] refresh_token)
              (assoc-in [:auth :refresh-expires-at] refresh_expires_at)
              (assoc-in [:auth :refreshing?] false)
              (assoc-in [:auth :error] nil))
      :dispatch-n dispatches
      ::persist-auth {:token access_token
                      :expires-at expires_at
                      :refresh-token refresh_token
                      :refresh-expires-at refresh_expires_at
                      :user user}})))

(rf/reg-event-fx
 ::refresh-failed
 (fn [{:keys [db]} [_ {:keys [status response status-text]}]]
   (let [message (or (:error response)
                     (cond
                       (= status 0) "Network error"
                       status (str "Session expired (" status ")" (when status-text (str ": " status-text)))
                       :else "Session expired"))]
     {:db (assoc-in db [:auth :refreshing?] false)
      :dispatch [::logout {:reason message :silent? false}]})))

(rf/reg-event-fx
 ::logout
 (fn [{:keys [db]} [_ {:keys [reason silent?]}]]
   (let [next-db (reset-auth db)]
     (cond-> {:db next-db
              ::persist-auth nil}
       (and reason (not silent?))
       (assoc :dispatch [::toast/enqueue-toast {:message reason :variant :info}])))))

(defn- session-context [payload]
  (cond
    (map? payload) payload
    (string? payload) {:message payload}
    :else {}))

(rf/reg-event-fx
 ::session-expired
 (fn [{:keys [db]} [_ payload]]
   (let [{:keys [message]} (session-context payload)]
     (cond
       (get-in db [:auth :refreshing?])
       {} 

       (refresh-available? db)
       {:dispatch [::attempt-refresh]}

       :else
       {:dispatch [::logout {:reason (or message "Session expired. Please sign in again")
                             :silent? false}]}))))

(defn- valid-token? [token]
  (and (string? token) (not (str/blank? token))))

(rf/reg-event-fx
 ::restore-session
 (fn [{:keys [db]} _]
   (if-let [{:keys [token] :as snapshot} (storage/load-auth)]
     (let [has-token? (valid-token? token)
           next-db (hydrate-auth db snapshot)
           admin? (admin-user? (:user snapshot))
           dispatches (cond-> []
                         true (conj [::todo-events/fetch-todos])
                         admin? (conj [::user-events/fetch-users]))]
       (cond
         has-token?
         {:db next-db
          :dispatch-n dispatches}

         (refresh-available? next-db)
         {:db next-db
          :dispatch [::attempt-refresh]}

         :else
         {:db next-db
          ::persist-auth nil}))
     nil)))
