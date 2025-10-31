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
                       :headers {"Accept" "application/json"}
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

(defn- parse-int-value [value default]
  (cond
    (number? value) (int value)
    (string? value) (let [parsed (js/parseInt value 10)]
                      (if (js/isNaN parsed)
                        default
                        parsed))
    :else default))

(defn- clamp-page [total per-page page]
  (let [per (max 1 (or per-page 1))
        total (max 0 total)
        last-page (max 1 (int (js/Math.ceil (/ total per))))
        target (parse-int-value page 1)]
    (-> target
        (max 1)
        (min last-page))))

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
                     :headers {"Accept" "application/json"}
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
                     :format (ajax/url-request-format)
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

(rf/reg-event-fx
 ::fetch-todos
 (fn [{:keys [db]} _]
   {:db (-> db
            (assoc-in [:todos :loading?] true)
            (assoc-in [:todos :error] nil))
    :http-xhrio {:method :get
                 :uri "/api/todo"
                 :timeout 8000
                 :headers {"Accept" "application/json"}
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [::todos-loaded]
                 :on-failure [::fetch-todos-failed]}}))

(rf/reg-event-db
 ::todos-loaded
 (fn [db [_ todos]]
   (let [items (vec todos)
         per-page (get-in db [:todos :pagination :per-page])
         current-page (get-in db [:todos :pagination :page])]
     (-> db
         (assoc-in [:todos :items] items)
         (assoc-in [:todos :loading?] false)
         (assoc-in [:todos :pagination :page]
                   (clamp-page (count items) per-page current-page))))))

(rf/reg-event-fx
 ::fetch-todos-failed
 (fn [{:keys [db]} [_ {:keys [status status-text]}]]
   (let [msg (str "Failed to load todos"
                  (when status (str " (" status ")"))
                  (when status-text (str ": " status-text)))]
     {:db (-> db
              (assoc-in [:todos :loading?] false)
              (assoc-in [:todos :error] msg))
      :dispatch [::enqueue-toast {:message msg :variant :error}]})))

(rf/reg-event-db
 ::set-todo-sort
 (fn [db [_ {:keys [field direction]}]]
   (let [field (keyword field)
         field (if (#{:completed :created_at :updated_at} field)
                 field
                 :created_at)
         direction (-> direction keyword (or :desc))
         direction (if (#{:asc :desc} direction) direction :desc)]
     (-> db
         (assoc-in [:todos :sort :field] field)
         (assoc-in [:todos :sort :direction] direction)
         (assoc-in [:todos :pagination :page] 1)))))

(rf/reg-event-db
 ::update-todo-filter
 (fn [db [_ path value]]
   (let [normalized (if (= path [:completed])
                      (let [kw (cond
                                 (keyword? value) value
                                 (string? value) (keyword value)
                                 :else :all)]
                        (if (#{:all :completed :pending} kw) kw :all))
                      (or value ""))]
     (-> db
         (assoc-in (into [:todos :filters] path) normalized)
         (assoc-in [:todos :pagination :page] 1)))))

(rf/reg-event-db
 ::clear-todo-filters
 (fn [db _]
   (-> db
       (assoc-in [:todos :filters] db/default-todo-filters)
       (assoc-in [:todos :pagination :page] 1))))

(rf/reg-event-db
 ::set-todo-page
 (fn [db [_ page]]
   (let [items (get-in db [:todos :items])
         per-page (get-in db [:todos :pagination :per-page])]
     (assoc-in db [:todos :pagination :page]
               (clamp-page (count items) per-page page)))))

(rf/reg-event-db
 ::set-todo-per-page
 (fn [db [_ per-page]]
   (let [current (get-in db [:todos :pagination :per-page])
         items (get-in db [:todos :items])
         new-per (max 1 (parse-int-value per-page current))]
     (-> db
         (assoc-in [:todos :pagination :per-page] new-per)
         (assoc-in [:todos :pagination :page]
                   (clamp-page (count items) new-per 1))))))

(rf/reg-event-db
 ::open-add-todo-dialog
 (fn [db _]
   (-> db
       (assoc-in [:todos :add :visible?] true)
       (assoc-in [:todos :add :title] "")
       (assoc-in [:todos :add :completed?] false)
       (assoc-in [:todos :add :errors] {})
       (assoc-in [:todos :add :submitting?] false)
       (assoc-in [:todos :error] nil))))

(rf/reg-event-db
 ::close-add-todo-dialog
 (fn [db _]
   (-> db
       (assoc-in [:todos :add :visible?] false)
       (assoc-in [:todos :add :submitting?] false)
       (assoc-in [:todos :add :errors] {}))))

(rf/reg-event-db
 ::update-add-todo-field
 (fn [db [_ field value]]
   (-> db
       (assoc-in [:todos :add field] value)
       (update-in [:todos :add :errors] dissoc field))))

(rf/reg-event-fx
 ::add-todo
 (fn [{:keys [db]} _]
   (let [{:keys [title completed?]} (get-in db [:todos :add])
         trimmed-title (str/trim title)
         errors (cond-> {}
                  (str/blank? trimmed-title) (assoc :title "Title is required"))]
     (if (seq errors)
       {:db (assoc-in db [:todos :add :errors] errors)}
       {:db (-> db
                (assoc-in [:todos :add :submitting?] true)
                (assoc-in [:todos :add :errors] {})
                (assoc-in [:todos :error] nil))
        :http-xhrio {:method :post
                     :uri "/api/todo"
                     :timeout 8000
                     :headers {"Accept" "application/json"}
                     :params {:title trimmed-title
                              :completed completed?}
                     :format (ajax/json-request-format)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [::todo-created]
                     :on-failure [::add-todo-failed]}}))))

(rf/reg-event-fx
 ::todo-created
 (fn [{:keys [db]} [_ _todo]]
   {:db (-> db
            (assoc-in [:todos :add :visible?] false)
            (assoc-in [:todos :add :submitting?] false)
            (assoc-in [:todos :add :title] "")
            (assoc-in [:todos :add :completed?] false))
    :dispatch-n [[::fetch-todos]
                 [::enqueue-toast {:message "Todo created" :variant :success}]]}))

(rf/reg-event-fx
 ::add-todo-failed
 (fn [{:keys [db]} [_ {:keys [status status-text]}]]
   (let [msg (str "Failed to create todo"
                  (when status (str " (" status ")"))
                  (when status-text (str ": " status-text)))]
     {:db (-> db
              (assoc-in [:todos :add :submitting?] false)
              (assoc-in [:todos :error] msg))
      :dispatch [::enqueue-toast {:message msg :variant :error}]})))

(rf/reg-event-fx
 ::open-edit-todo-dialog
 (fn [{:keys [db]} [_ id]]
   (if-let [todo (some #(when (= id (:id %)) %) (get-in db [:todos :items]))]
     {:db (-> db
              (assoc-in [:todos :edit :visible?] true)
              (assoc-in [:todos :edit :id] (:id todo))
              (assoc-in [:todos :edit :title] (or (:title todo) ""))
              (assoc-in [:todos :edit :completed?] (boolean (:completed todo)))
              (assoc-in [:todos :edit :errors] {})
              (assoc-in [:todos :edit :submitting?] false)
              (assoc-in [:todos :edit :initial] {:title (:title todo)
                                                 :completed? (boolean (:completed todo))}))}
     {:db db
      :dispatch [::enqueue-toast {:message "Todo not found"
                                  :variant :error}]})))

(rf/reg-event-db
 ::close-edit-todo-dialog
 (fn [db _]
   (-> db
       (assoc-in [:todos :edit :visible?] false)
       (assoc-in [:todos :edit :id] nil)
       (assoc-in [:todos :edit :title] "")
       (assoc-in [:todos :edit :completed?] false)
       (assoc-in [:todos :edit :errors] {})
       (assoc-in [:todos :edit :submitting?] false)
       (assoc-in [:todos :edit :initial] {:title ""
                                          :completed? false}))))

(rf/reg-event-db
 ::update-edit-todo-field
 (fn [db [_ field value]]
   (-> db
       (assoc-in [:todos :edit field] value)
       (update-in [:todos :edit :errors] dissoc field))))

(rf/reg-event-fx
 ::update-todo
 (fn [{:keys [db]} _]
   (let [{:keys [id title completed? initial]} (get-in db [:todos :edit])
         id-str (some-> id str str/trim)
         trimmed-title (str/trim (or title ""))
         title-changed? (not= trimmed-title (:title initial))
         completed-changed? (not= (boolean completed?) (boolean (:completed? initial)))
         errors (cond-> {}
                  (and title-changed? (str/blank? trimmed-title)) (assoc :title "Title is required"))]
     (cond
       (str/blank? id-str)
       {:dispatch [::enqueue-toast {:message "Todo id missing" :variant :error}]}

       (seq errors)
       {:db (assoc-in db [:todos :edit :errors] errors)}

       (not (or title-changed? completed-changed?))
       {:dispatch [::enqueue-toast {:message "No changes to save" :variant :info}]}

       :else
       (let [payload (cond-> {}
                       (and title-changed? (not (str/blank? trimmed-title))) (assoc :title trimmed-title)
                       completed-changed? (assoc :completed (boolean completed?)))]
         {:db (-> db
                  (assoc-in [:todos :edit :submitting?] true)
                  (assoc-in [:todos :edit :errors] {}))
          :http-xhrio {:method :patch
                       :uri (str "/api/todo/" id-str)
                       :timeout 8000
                       :headers {"Accept" "application/json"}
                       :params payload
                       :format (ajax/json-request-format)
                       :response-format (ajax/json-response-format {:keywords? true})
                       :on-success [::todo-updated]
                       :on-failure [::update-todo-failed id-str payload]}})))))

(rf/reg-event-fx
 ::todo-updated
 (fn [{:keys [db]} [_ _todo]]
   {:db (-> db
            (assoc-in [:todos :edit :visible?] false)
            (assoc-in [:todos :edit :id] nil)
            (assoc-in [:todos :edit :title] "")
            (assoc-in [:todos :edit :completed?] false)
            (assoc-in [:todos :edit :errors] {})
            (assoc-in [:todos :edit :submitting?] false)
            (assoc-in [:todos :edit :initial] {:title ""
                                               :completed? false}))
    :dispatch-n [[::fetch-todos]
                 [::enqueue-toast {:message "Todo updated" :variant :success}]]}))

(rf/reg-event-fx
 ::update-todo-failed
 (fn [{:keys [db]} [_ id _payload {:keys [status status-text]}]]
   (let [msg (str "Failed to update todo"
                  (when status (str " (" status ")"))
                  (when status-text (str ": " status-text)))]
     {:db (-> db
              (assoc-in [:todos :edit :submitting?] false)
              (assoc-in [:todos :error] msg))
      :dispatch [::enqueue-toast {:message msg :variant :error}]})))

(rf/reg-event-fx
 ::delete-todo
 (fn [{:keys [db]} [_ id]]
   (let [id-str (some-> id str str/trim)]
     (if (str/blank? id-str)
       {:dispatch [::enqueue-toast {:message "Todo id missing" :variant :error}]}
       {:db (update-in db [:todos :pending] conj id-str)
        :http-xhrio {:method :delete
                     :uri (str "/api/todo/" id-str)
                     :timeout 8000
                     :headers {"Accept" "application/json"}
                     :format (ajax/url-request-format)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [::todo-deleted id-str]
                     :on-failure [::delete-todo-failed id-str]}}))))

(rf/reg-event-fx
 ::todo-deleted
 (fn [{:keys [db]} [_ id _response]]
   {:db (update-in db [:todos :pending] disj id)
    :dispatch-n [[::fetch-todos]
                 [::enqueue-toast {:message "Todo deleted" :variant :success}]]}))

(rf/reg-event-fx
 ::delete-todo-failed
 (fn [{:keys [db]} [_ id {:keys [status status-text]}]]
   (let [msg (str "Failed to delete todo"
                  (when status (str " (" status ")"))
                  (when status-text (str ": " status-text)))]
     {:db (-> db
              (update-in [:todos :pending] disj id)
              (assoc-in [:todos :error] msg))
      :dispatch [::enqueue-toast {:message msg :variant :error}]})))
