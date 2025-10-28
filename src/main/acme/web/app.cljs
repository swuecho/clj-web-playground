(ns acme.web.app
  (:require
   [ajax.core :as ajax]
   [day8.re-frame.http-fx]
   [re-frame.core :as rf]
   [reagent.dom :as rdom]))

(def default-db
  {:users []
   :loading? false
   :error nil
   :add-user {:visible? false
              :name ""
              :age "0"
              :submitting? false}})

(rf/reg-event-db
 ::initialize
 (fn [_ _]
   default-db))

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

(rf/reg-event-db
 ::fetch-failed
 (fn [db [_ {:keys [status status-text]}]]
   (-> db
       (assoc :loading? false)
       (assoc :error (str "Request failed" (when status (str " (" status ")"))
                          (when status-text (str ": " status-text)))))))

(rf/reg-event-db
 ::open-add-user-dialog
 (fn [db _]
   (-> db
       (assoc-in [:add-user :visible?] true)
       (assoc-in [:add-user :name] "")
       (assoc-in [:add-user :age] "0")
       (assoc-in [:add-user :submitting?] false)
       (assoc :error nil))))

(rf/reg-event-db
 ::close-add-user-dialog
 (fn [db _]
   (-> db
       (assoc-in [:add-user :visible?] false)
       (assoc-in [:add-user :submitting?] false))))

(rf/reg-event-db
 ::update-add-user-field
 (fn [db [_ field value]]
   (assoc-in db [:add-user field] value)))

(rf/reg-event-fx
 ::add-user
 (fn [{:keys [db]} _]
   (let [{:keys [name age]} (:add-user db)
         parsed-age (let [n (js/parseInt age 10)]
                      (if (js/isNaN n) 0 n))
         user {:uuid (str (random-uuid))
               :name name
               :age parsed-age}]
     {:db (-> db
              (assoc :error nil)
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
                   :on-failure [::add-user-failed]}})))

(rf/reg-event-fx
 ::user-added
 (fn [{:keys [db]} [_ _response]]
   {:db (-> db
            (assoc :loading? false)
            (assoc-in [:add-user :visible?] false)
            (assoc-in [:add-user :submitting?] false)
            (assoc-in [:add-user :name] "")
            (assoc-in [:add-user :age] "0"))
    :dispatch [::fetch-users]}))

(rf/reg-event-db
 ::add-user-failed
 (fn [db [_ {:keys [status status-text]}]]
   (-> db
       (assoc :loading? false)
       (assoc-in [:add-user :submitting?] false)
       (assoc :error (str "Add user failed" (when status (str " (" status ")"))
                          (when status-text (str ": " status-text)))))))

(rf/reg-sub
 ::users
 (fn [db]
   (:users db)))

(rf/reg-sub
 ::loading?
 (fn [db]
   (:loading? db)))

(rf/reg-sub
 ::error
 (fn [db]
   (:error db)))

(rf/reg-sub
 ::add-user
 (fn [db]
   (:add-user db)))

(rf/reg-sub
 ::add-user-visible?
 (fn [db]
   (get-in db [:add-user :visible?])))

(rf/reg-sub
 ::add-user-name
 (fn [db]
   (get-in db [:add-user :name])))

(rf/reg-sub
 ::add-user-age
 (fn [db]
   (get-in db [:add-user :age])))

(rf/reg-sub
 ::add-user-submitting?
 (fn [db]
   (get-in db [:add-user :submitting?])))

(defn add-user-dialog []
  (let [visible? (rf/subscribe [::add-user-visible?])
        name (rf/subscribe [::add-user-name])
        age (rf/subscribe [::add-user-age])
        submitting? (rf/subscribe [::add-user-submitting?])]
    (fn []
      (when @visible?
        [:div {:style {:position "fixed"
                       :top 0
                       :left 0
                       :width "100vw"
                       :height "100vh"
                       :background "rgba(15, 23, 42, 0.5)"
                       :display "flex"
                       :align-items "center"
                       :justify-content "center"
                       :z-index 1000}}
         [:div {:style {:background "white"
                         :border-radius "0.75rem"
                         :box-shadow "0 10px 40px rgba(15, 23, 42, 0.15)"
                         :padding "1.5rem"
                         :width "min(400px, 90vw)"}}
          [:h2 {:style {:margin-bottom "1rem"
                        :font-size "1.25rem"}} "Add User"]
          [:div {:style {:display "flex"
                         :flex-direction "column"
                         :gap "0.75rem"}}
           [:label {:style {:display "flex"
                             :flex-direction "column"
                             :gap "0.25rem"}}
            [:span "Name"]
            [:input {:type "text"
                     :value @name
                     :on-change #(rf/dispatch [::update-add-user-field :name (.. % -target -value)])
                     :style {:padding "0.5rem"
                             :border "1px solid #d1d5db"
                             :border-radius "0.375rem"}}]]
           [:label {:style {:display "flex"
                             :flex-direction "column"
                             :gap "0.25rem"}}
            [:span "Age"]
            [:input {:type "number"
                     :min 0
                     :value @age
                     :on-change #(rf/dispatch [::update-add-user-field :age (.. % -target -value)])
                     :style {:padding "0.5rem"
                             :border "1px solid #d1d5db"
                             :border-radius "0.375rem"}}]]]
          [:div {:style {:display "flex"
                         :justify-content "flex-end"
                         :gap "0.75rem"
                         :margin-top "1.5rem"}}
           [:button {:on-click #(rf/dispatch [::close-add-user-dialog])
                     :disabled @submitting?
                     :style {:background "transparent"
                             :border "none"
                             :color "#4b5563"
                             :padding "0.5rem 1rem"
                             :cursor (if @submitting? "not-allowed" "pointer")}}
            "Cancel"]
           [:button {:on-click #(rf/dispatch [::add-user])
                     :disabled @submitting?
                     :style {:background (if @submitting? "#86efac" "#16a34a")
                             :color "white"
                             :border "none"
                             :padding "0.5rem 1rem"
                             :border-radius "0.375rem"
                             :cursor (if @submitting? "not-allowed" "pointer")}}
            (if @submitting? "Saving..." "Save")]]]]))))

(defn users-table []
  (let [users (rf/subscribe [::users])
        loading? (rf/subscribe [::loading?])
        error (rf/subscribe [::error])]
    (fn []
      [:div {:style {:max-width "960px"
                     :margin "0 auto"
                     :padding "1.5rem"}}
       [add-user-dialog]
       [:h1 {:style {:margin-bottom "1rem"}} "Users"]
       (cond
         @loading? [:p "Loading users..."]
         @error [:p {:style {:color "#b91c1c"}} @error]
         (empty? @users) [:p "No users found."]
         :else
         [:table {:style {:width "100%"
                          :border-collapse "collapse"}}
          [:thead
           [:tr
            [:th {:style {:text-align "left"
                          :border-bottom "1px solid #d1d5db"
                          :padding "0.5rem"}} "UUID"]
            [:th {:style {:text-align "left"
                          :border-bottom "1px solid #d1d5db"
                          :padding "0.5rem"}} "Name"]
            [:th {:style {:text-align "left"
                          :border-bottom "1px solid #d1d5db"
                          :padding "0.5rem"}} "Age"]]]
          [:tbody
           (for [{:keys [uuid name age]} @users]
             ^{:key uuid}
             [:tr
              [:td {:style {:border-bottom "1px solid #e5e7eb"
                            :padding "0.5rem"
                            :font-family "monospace"}} uuid]
              [:td {:style {:border-bottom "1px solid #e5e7eb"
                            :padding "0.5rem"}} name]
              [:td {:style {:border-bottom "1px solid #e5e7eb"
                            :padding "0.5rem"}} age]])]])
       [:div {:style {:margin-top "1.5rem"}}
        [:button {:on-click #(rf/dispatch [::fetch-users])
                  :style {:background "#2563eb"
                          :color "white"
                          :border "none"
                          :padding "0.5rem 1rem"
                          :border-radius "0.375rem"
                          :cursor "pointer"}}
         "Reload"]
        [:button {:on-click #(rf/dispatch [::open-add-user-dialog])
                  :style {:margin-left "0.75rem"
                          :background "#16a34a"
                          :color "white"
                          :border "none"
                          :padding "0.5rem 1rem"
                          :border-radius "0.375rem"
                          :cursor "pointer"}}
         "Add User"]]])))

(defn mount-root []
  (rf/clear-subscription-cache!)
  (when-let [el (.getElementById js/document "root")]
    (rdom/render [users-table] el)))

(defn init []
  (rf/dispatch-sync [::initialize])
  (rf/dispatch [::fetch-users])
  (mount-root))

(defn ^:dev/after-load reload! []
  (mount-root))
