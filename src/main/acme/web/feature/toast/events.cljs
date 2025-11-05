(ns acme.web.feature.toast.events
  (:require
   [re-frame.core :as rf]))

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
