(ns acme.web.feature.todos.events
  (:require
   [acme.web.db :as db]
   [acme.web.feature.toast.events :as toast]
   [acme.web.http :as http]
   [ajax.core :as ajax]
   [clojure.string :as str]
   [day8.re-frame.http-fx]
   [re-frame.core :as rf]))

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

(defn- unauthorized? [status]
  (= status 401))

(rf/reg-event-fx
 ::fetch-todos
 (fn [{:keys [db]} _]
   (let [headers (http/authorized-headers db)]
     {:db (-> db
              (assoc-in [:todos :loading?] true)
              (assoc-in [:todos :error] nil))
      :http-xhrio {:method :get
                   :uri "/api/todo"
                   :timeout 8000
                   :headers headers
                   :response-format (ajax/json-response-format {:keywords? true})
                   :on-success [::todos-loaded]
                   :on-failure [::fetch-todos-failed]}})))

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
      (cond-> {:db (-> db
                       (assoc-in [:todos :loading?] false)
                       (assoc-in [:todos :error] msg))}
        (unauthorized? status) (assoc :dispatch [:acme.web.feature.auth.events/session-expired nil])
        (not (unauthorized? status))
        (assoc :dispatch [::toast/enqueue-toast {:message msg :variant :error}])))))

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
       (let [headers (http/authorized-headers db)]
         {:db (-> db
                  (assoc-in [:todos :add :submitting?] true)
                  (assoc-in [:todos :add :errors] {})
                  (assoc-in [:todos :error] nil))
          :http-xhrio {:method :post
                       :uri "/api/todo"
                       :timeout 8000
                       :headers headers
                       :params {:title trimmed-title
                                :completed completed?}
                       :format (ajax/json-request-format)
                       :response-format (ajax/json-response-format {:keywords? true})
                       :on-success [::todo-created]
                       :on-failure [::add-todo-failed]}})))))

(rf/reg-event-fx
 ::todo-created
 (fn [{:keys [db]} [_ _todo]]
   {:db (-> db
            (assoc-in [:todos :add :visible?] false)
            (assoc-in [:todos :add :submitting?] false)
            (assoc-in [:todos :add :title] "")
            (assoc-in [:todos :add :completed?] false))
    :dispatch-n [[::fetch-todos]
                 [::toast/enqueue-toast {:message "Todo created"
                                         :variant :success}]]}))

(rf/reg-event-fx
 ::add-todo-failed
 (fn [{:keys [db]} [_ {:keys [status status-text]}]]
   (let [msg (str "Failed to create todo"
                  (when status (str " (" status ")"))
                  (when status-text (str ": " status-text)))]
      (cond-> {:db (-> db
                       (assoc-in [:todos :add :submitting?] false)
                       (assoc-in [:todos :error] msg))}
        (unauthorized? status) (assoc :dispatch [:acme.web.feature.auth.events/session-expired nil])
        (not (unauthorized? status))
        (assoc :dispatch [::toast/enqueue-toast {:message msg :variant :error}])))))

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
      :dispatch [::toast/enqueue-toast {:message "Todo not found"
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
       {:dispatch [::toast/enqueue-toast {:message "Todo id missing"
                                          :variant :error}]}

       (seq errors)
       {:db (assoc-in db [:todos :edit :errors] errors)}

       (not (or title-changed? completed-changed?))
       {:dispatch [::toast/enqueue-toast {:message "No changes to save"
                                          :variant :info}]}

       :else
       (let [payload (cond-> {}
                       (and title-changed? (not (str/blank? trimmed-title))) (assoc :title trimmed-title)
                       completed-changed? (assoc :completed (boolean completed?)))
             headers (http/authorized-headers db)]
         {:db (-> db
                  (assoc-in [:todos :edit :submitting?] true)
                  (assoc-in [:todos :edit :errors] {}))
          :http-xhrio {:method :patch
                       :uri (str "/api/todo/" id-str)
                       :timeout 8000
                       :headers headers
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
                 [::toast/enqueue-toast {:message "Todo updated"
                                         :variant :success}]]}))

(rf/reg-event-fx
 ::update-todo-failed
 (fn [{:keys [db]} [_ id _payload {:keys [status status-text]}]]
   (let [msg (str "Failed to update todo"
                  (when status (str " (" status ")"))
                  (when status-text (str ": " status-text)))]
      (cond-> {:db (-> db
                       (assoc-in [:todos :edit :submitting?] false)
                       (assoc-in [:todos :error] msg))}
        (unauthorized? status) (assoc :dispatch [:acme.web.feature.auth.events/session-expired nil])
        (not (unauthorized? status))
        (assoc :dispatch [::toast/enqueue-toast {:message msg :variant :error}])))))

(rf/reg-event-fx
 ::delete-todo
  (fn [{:keys [db]} [_ id]]
    (let [id-str (some-> id str str/trim)]
      (if (str/blank? id-str)
        {:dispatch [::toast/enqueue-toast {:message "Todo id missing"
                                           :variant :error}]}
        (let [headers (http/authorized-headers db)]
          {:db (update-in db [:todos :pending] conj id-str)
           :http-xhrio {:method :delete
                        :uri (str "/api/todo/" id-str)
                        :timeout 8000
                        :headers headers
                        :format (ajax/url-request-format)
                        :response-format (ajax/json-response-format {:keywords? true})
                        :on-success [::todo-deleted id-str]
                        :on-failure [::delete-todo-failed id-str]}})))))

(rf/reg-event-fx
 ::todo-deleted
 (fn [{:keys [db]} [_ id _response]]
   {:db (update-in db [:todos :pending] disj id)
    :dispatch-n [[::fetch-todos]
                 [::toast/enqueue-toast {:message "Todo deleted"
                                         :variant :success}]]}))

(rf/reg-event-fx
 ::delete-todo-failed
 (fn [{:keys [db]} [_ id {:keys [status status-text]}]]
   (let [msg (str "Failed to delete todo"
                  (when status (str " (" status ")"))
                  (when status-text (str ": " status-text)))]
      (cond-> {:db (-> db
                       (update-in [:todos :pending] disj id)
                       (assoc-in [:todos :error] msg))}
        (unauthorized? status) (assoc :dispatch [:acme.web.feature.auth.events/session-expired nil])
        (not (unauthorized? status))
        (assoc :dispatch [::toast/enqueue-toast {:message msg :variant :error}])))))
