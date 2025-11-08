> Here’s a complete, realistic reg-event-fx setup that shows all the pieces:

  (ns acme.web.feature.report.events
    (:require
      [re-frame.core :as rf]
      [day8.re-frame.http-fx]                ;; ensures :http-xhrio effect is registered
      [ajax.core :as ajax]))

  ;; Optional interceptor that logs every request
  (def log-request
    (rf/->interceptor
      :id :log-request
      :before (fn [context]
                (let [[_ event-id & more] (get-in context [:coeffects :event])]
                  (js/console.info "Dispatching" event-id "with" (pr-str more)))
                context)))

  (rf/reg-event-fx
    ::fetch-report
    [log-request]                           ;; interceptor chain
    (fn [{:keys [db]} [_ report-id]]        ;; handler signature
      {:db (-> db
               (assoc :report/loading? true)
               (assoc :report/error nil))
       :http-xhrio {:method          :get
                    :uri             (str "/api/reports/" report-id)
                    :timeout         10000
                    :response-format (ajax/json-response-format {:keywords? true})
                    :headers         {"Accept" "application/json"}
                    :on-success      [::fetch-report-success report-id]
                    :on-failure      [::fetch-report-failure report-id]}}))

  (rf/reg-event-fx
    ::fetch-report-success
    (fn [{:keys [db]} [_ report-id payload]]
      {:db (-> db
               (assoc-in [:reports report-id] payload)
               (assoc :report/loading? false))}))

  (rf/reg-event-fx
    ::fetch-report-failure
    (fn [{:keys [db]} [_ report-id error]]
      {:db (-> db
               (assoc :report/loading? false)
               (assoc :report/error {:id report-id :detail error}))}))

  Key points demonstrated:

  - Full (reg-event-fx id interceptors handler) signature.
  - Handler receives the coeffects map and event vector (we destructure report-id out of the latter).
  - Returned effects map includes both :db and :http-xhrio.
  - Follow-up success/failure handlers finish the state transition.

▌ in     (fn [{:keys [db]} [_ report-id]]        ;; handler signature, why the _

> Re-frame delivers the entire event vector to your handler, e.g. [::fetch-report report-id]. When you destructure [_ report-id], the first slot
  corresponds to the event id (::fetch-report). We already know which handler we’re in, so we don’t need that value; using _ is the idiomatic
  way to say “ignore this position.” The second slot (report-id) is the actual payload we care about. Without the _, you’d either have to bind a
  name you don’t use (which triggers lint warnings) or inspect the vector manually.

▌ why {:keys [db]}?

> Handlers registered via reg-event-fx receive a “coeffects” map as their first argument. Re-frame populates that map with useful inputs; by
  default it always includes the current app-db under the :db key. Writing {:keys [db]} simply destructures the :db entry so we can refer to the
  database value directly (instead of repeatedly doing (get coeffects :db) or similar). If you needed other coeffects—say, the event vector, the
  current time, injected dependencies—you could destructure them alongside db.

▌ explain coeffects map

> In re-frame’s effect system, every event handler is passed a “coeffects” map so it can see the inputs it needs. Think of coeffects as a bundle
  of read-only data assembled by interceptors before your handler runs. By default that map contains:

  - :db – the current app-db value.
  - :event – the full event vector that was dispatched.

  Interceptors can inject more entries (e.g., :now for current time, :local-store for cached data, :route for the current URL). When you write
  (fn [{:keys [db event]} …), you’re destructuring that coeffects map to grab whatever pieces were provided. This separation lets handlers stay
  pure and testable: they just consume the coeffects map and return an effects map describing what should happen next.