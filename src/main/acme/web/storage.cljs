(ns acme.web.storage)

(def auth-key "acme.auth")

(defn- local-storage []
  (try
    (some-> js/window .-localStorage)
    (catch :default _
      nil)))

(defn- parse-json [value]
  (when (and value (not (empty? value)))
    (try
      (js->clj (js/JSON.parse value) :keywordize-keys true)
      (catch :default _
        nil))))

(defn- expired? [iso-string]
  (try
    (let [expires-ms (.getTime (js/Date. iso-string))
          now-ms (.now js/Date.)]
      (<= expires-ms now-ms))
    (catch :default _
      true)))

(defn save-auth! [auth]
  (when-let [store (local-storage)]
    (if auth
      (.setItem store auth-key (js/JSON.stringify (clj->js auth)))
      (.removeItem store auth-key))))

(defn clear-auth! []
  (save-auth! nil))

(defn load-auth []
  (when-let [store (local-storage)]
    (when-let [raw (.getItem store auth-key)]
      (when-let [data (parse-json raw)]
        (if (and (:expires-at data)
                 (expired? (:expires-at data)))
          (do
            (.removeItem store auth-key)
            nil)
          data)))))
