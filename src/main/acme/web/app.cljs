(ns acme.web.app
  (:require
   [ajax.core :as ajax]
   [day8.re-frame.http-fx]
   [re-frame.core :as rf]
   [reagent.dom :as rdom]))

(def default-db
  {:users []
   :loading? false
   :error nil})

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

(defn users-table []
  (let [users (rf/subscribe [::users])
        loading? (rf/subscribe [::loading?])
        error (rf/subscribe [::error])]
    (fn []
      [:div {:style {:max-width "960px"
                     :margin "0 auto"
                     :padding "1.5rem"}}
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
         "Reload"]]])))

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
