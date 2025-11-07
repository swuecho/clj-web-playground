(ns acme.web.feature.todos.subs
  (:require
   [re-frame.core :as rf]))

(defn- parse-datetime [value]
  (when (and value (seq (str value)))
    (let [date (js/Date. value)
          ts (.getTime date)]
      (when-not (js/isNaN ts)
        ts))))

(rf/reg-sub
 ::todos
 (fn [db]
   (:todos db)))

(rf/reg-sub
 ::todos-items
 (fn [db]
   (get-in db [:todos :items])))

(rf/reg-sub
 ::todos-loading?
 (fn [db]
   (get-in db [:todos :loading?])))

(rf/reg-sub
 ::todos-error
 (fn [db]
   (get-in db [:todos :error])))

(rf/reg-sub
 ::todo-sort
 (fn [db]
   (get-in db [:todos :sort])))

(rf/reg-sub
 ::todo-filters
 (fn [db]
   (get-in db [:todos :filters])))

(rf/reg-sub
 ::todo-pagination-state
 (fn [db]
   (get-in db [:todos :pagination])))

(rf/reg-sub
 ::sorted-todos
 :<- [::todos-items]
 :<- [::todo-sort]
 :<- [::todo-filters]
 (fn [[items {:keys [field direction]} filters]]
   (let [completed-filter (or (:completed filters) :all)
         created-after (parse-datetime (get-in filters [:created :after]))
         created-before (parse-datetime (get-in filters [:created :before]))
         updated-after (parse-datetime (get-in filters [:updated :after]))
         updated-before (parse-datetime (get-in filters [:updated :before]))
         filtered-items (->> (or items [])
                             (filter
                              (fn [{:keys [completed created_at updated_at]}]
                                (let [completed-val (boolean completed)
                                      created-ts (parse-datetime created_at)
                                      updated-ts (parse-datetime updated_at)
                                      completed-ok (case completed-filter
                                                     :completed completed-val
                                                     :pending (not completed-val)
                                                     true)
                                      created-after-ok (if created-after
                                                         (and created-ts
                                                              (>= created-ts created-after))
                                                         true)
                                      created-before-ok (if created-before
                                                          (and created-ts
                                                               (<= created-ts created-before))
                                                          true)
                                      updated-after-ok (if updated-after
                                                         (and updated-ts
                                                              (>= updated-ts updated-after))
                                                         true)
                                      updated-before-ok (if updated-before
                                                          (and updated-ts
                                                               (<= updated-ts updated-before))
                                                          true)]
                                  (and completed-ok
                                       created-after-ok
                                       created-before-ok
                                       updated-after-ok
                                       updated-before-ok)))))]
     (let [comparator (case field
                        :completed (fn [{ca :completed} {cb :completed}]
                                     (compare ca cb))
                        :created_at (fn [{a :created_at} {b :created_at}]
                                      (compare a b))
                        :updated_at (fn [{a :updated_at} {b :updated_at}]
                                      (compare a b))
                        (fn [a b]
                          (compare (:id a) (:id b))))
           sorted-items (sort comparator filtered-items)
           ordered (if (= direction :desc)
                     (reverse sorted-items)
                     sorted-items)]
       (vec ordered)))))

(rf/reg-sub
 ::todo-pagination
 :<- [::sorted-todos]
 :<- [::todo-pagination-state]
 (fn [[items {:keys [page per-page]}]]
   (let [per (max 1 (or per-page 1))
         total (count items)
         last-page (max 1 (int (js/Math.ceil (/ total per))))
         page (-> (or page 1)
                  (max 1)
                  (min last-page))
         start-index (* (dec page) per)
         page-items (->> items
                         (drop start-index)
                         (take per)
                         (vec))
         end-index (if (seq page-items)
                     (min total (+ start-index (count page-items)))
                     start-index)]
     {:items page-items
      :page page
      :per-page per
      :total total
      :total-pages last-page
      :start (if (zero? total)
               0
               (inc start-index))
      :end end-index})))

(rf/reg-sub
 ::todo-add
 (fn [db]
   (get-in db [:todos :add])))

(rf/reg-sub
 ::todo-add-visible?
 (fn [db]
   (get-in db [:todos :add :visible?])))

(rf/reg-sub
 ::todo-add-title
 (fn [db]
   (get-in db [:todos :add :title])))

(rf/reg-sub
 ::todo-add-completed?
 (fn [db]
   (get-in db [:todos :add :completed?])))

(rf/reg-sub
 ::todo-add-submitting?
 (fn [db]
   (get-in db [:todos :add :submitting?])))

(rf/reg-sub
 ::todo-add-errors
 (fn [db]
   (get-in db [:todos :add :errors])))

(rf/reg-sub
 ::todo-edit
 (fn [db]
   (get-in db [:todos :edit])))

(rf/reg-sub
 ::todo-edit-visible?
 (fn [db]
   (get-in db [:todos :edit :visible?])))

(rf/reg-sub
 ::todo-edit-id
 (fn [db]
   (get-in db [:todos :edit :id])))

(rf/reg-sub
 ::todo-edit-title
 (fn [db]
   (get-in db [:todos :edit :title])))

(rf/reg-sub
 ::todo-edit-completed?
 (fn [db]
   (get-in db [:todos :edit :completed?])))

(rf/reg-sub
 ::todo-edit-submitting?
 (fn [db]
   (get-in db [:todos :edit :submitting?])))

(rf/reg-sub
 ::todo-edit-errors
 (fn [db]
   (get-in db [:todos :edit :errors])))

(rf/reg-sub
 ::todo-pending
 (fn [db]
   (get-in db [:todos :pending])))

(rf/reg-sub
 ::todo-delete-pending?
 :<- [::todo-pending]
 (fn [pending [_ id]]
   (contains? pending id)))
