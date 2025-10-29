(ns acme.server.core
  (:gen-class)
  (:require
   [clojure.string :as str]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.util.response :as response]))

(def default-users
  [{:uuid (str (java.util.UUID/randomUUID))
    :name "Ada Lovelace"
    :age 36}
   {:uuid (str (java.util.UUID/randomUUID))
    :name "Dennis Ritchie"
    :age 70}
   {:uuid (str (java.util.UUID/randomUUID))
    :name "Grace Hopper"
    :age 85}])

(defonce users (atom default-users))

(defn- normalize-age [age]
  (cond
    (int? age) age
    (integer? age) (int age)
    (string? age)
    (let [trimmed (str/trim age)]
      (when-not (str/blank? trimmed)
        (try
          (Integer/parseInt trimmed)
          (catch NumberFormatException _
            nil))))
    :else nil))

(defn- validate-user [{:keys [uuid name age]}]
  (let [trimmed-name (some-> name str/trim)
        parsed-age (normalize-age age)
        supplied-uuid (some-> uuid str not-empty)
        duplicate? (when supplied-uuid
                     (some #(= supplied-uuid (:uuid %)) @users))]
    (cond
      (or (nil? trimmed-name) (str/blank? trimmed-name))
      {:status 400 :message "Name is required"}

      (or (nil? parsed-age) (neg? parsed-age))
      {:status 400 :message "Age must be a non-negative integer"}

      duplicate?
      {:status 409 :message "A user with that uuid already exists"}

      :else
      {:status 201
       :user {:uuid (or supplied-uuid (str (java.util.UUID/randomUUID)))
              :name trimmed-name
              :age parsed-age}})))

(defn- ensure-unique-uuid [{:keys [uuid] :as user}]
  (if uuid
    user
    (loop [candidate (str (java.util.UUID/randomUUID))]
      (if (some #(= candidate (:uuid %)) @users)
        (recur (str (java.util.UUID/randomUUID)))
        (assoc user :uuid candidate)))))

(defn- not-found []
  (-> (response/response {:error "Not found"})
      (response/status 404)))

(defn- users-response []
  (response/response @users))

(defn- add-user-response [{:keys [body-params]}]
  (let [{:keys [status message user]} (validate-user body-params)]
    (if user
      (let [sanitized (ensure-unique-uuid user)]
        (swap! users conj sanitized)
        (-> (response/response sanitized)
            (response/status status)))
      (-> (response/response {:error message})
          (response/status status)))))

(defn- health-response []
  (response/response {:status "ok"}))

(def api-prefix "/api")

(defn handler [{:keys [request-method uri] :as request}]
  (cond
    (and (= request-method :get) (= uri (str api-prefix "/health"))) (health-response)
    (and (= request-method :get) (= uri (str api-prefix "/users"))) (users-response)
    (and (= request-method :post) (= uri (str api-prefix "/users"))) (add-user-response request)
    :else (not-found)))

(def app
  (-> handler
      wrap-json-response
      (wrap-json-body {:keywords? true})
      wrap-params))

(defonce server (atom nil))

(defn stop! []
  (when-let [s @server]
    (.stop s)
    (reset! server nil)))

(defn start!
  ([] (start! {}))
  ([{:keys [port join?]
     :or {port (some-> (System/getenv "PORT") Integer/parseInt)
          join? false}}]
   (let [port (or port 8081)]
     (stop!)
     (let [running (jetty/run-jetty #'app {:port port :join? join?})]
       (reset! server running)
       running))))

(defn -main
  [& [port]]
  (let [port (some-> port Integer/parseInt)
        server (start! {:port port :join? true})]
    (println (format "User API listening on http://localhost:%s" (.getPort server)))))
