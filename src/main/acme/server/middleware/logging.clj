(ns acme.server.middleware.logging
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [acme.server.http :as http])
  (:import
   (java.io ByteArrayInputStream InputStream)
   (java.nio.charset StandardCharsets)))

(def byte-array-class (Class/forName "[B"))

(def utf-8 StandardCharsets/UTF_8)

(def max-json-log-length 2048)

(defn- elapsed-ms [started]
  (long (/ (- (System/nanoTime) started) 1000000)))

(defn- present-map [value]
  (when (and (map? value) (seq value))
    value))

(defn- byte-array? [value]
  (instance? byte-array-class value))

(defn- bytes->string [^bytes bytes]
  (String. bytes utf-8))

(defn- coerce-json-body [body]
  (cond
    (nil? body)
    [nil nil]

    (string? body)
    [body body]

    (instance? ByteArrayInputStream body)
    (let [stream ^ByteArrayInputStream body
          bytes (.readAllBytes stream)]
      (.reset stream)
      [bytes stream])

    (instance? InputStream body)
    (let [stream ^InputStream body
          bytes (.readAllBytes stream)]
      (.close stream)
      [bytes (ByteArrayInputStream. bytes)])

    (byte-array? body)
    [body body]

    :else
    [(pr-str body) body]))

(defn- summarize-json-body [json-body]
  (when (and json-body (not (str/blank? json-body)))
    (let [length (count json-body)]
      (if (<= length max-json-log-length)
        {:value json-body
         :length length
         :truncated? false}
        {:value (str (subs json-body 0 max-json-log-length)
                     "... (truncated, total length " length " chars)")
         :length length
         :truncated? true}))))

(defn- summarize-request [request json-body]
  (let [method (some-> request :request-method name str/upper-case)
        uri (:uri request)
        json-summary (summarize-json-body json-body)
        base-details (cond-> {}
                       (:query-string request)
                       (assoc :query-string (:query-string request))
                       (present-map (:path-params request))
                       (assoc :path-params (:path-params request))
                       (present-map (:query-params request))
                       (assoc :query-params (:query-params request))
                       (present-map (:body-params request))
                       (assoc :body-params (:body-params request)))
        details (cond-> base-details
                  json-summary (assoc :json-body (:value json-summary))
                  json-summary (assoc :json-body-length (:length json-summary))
                  (and json-summary (:truncated? json-summary))
                  (assoc :json-body-truncated? true))]
    {:method method
     :uri uri
     :details details}))

(defn- summarize-response [response elapsed json-body]
  (let [content-type (get-in response [:headers "Content-Type"])
        json-summary (summarize-json-body json-body)]
    (cond-> {:status (:status response)
             :elapsed-ms elapsed}
      content-type (assoc :content-type content-type)
      json-summary (assoc :json-body (:value json-summary))
      json-summary (assoc :json-body-length (:length json-summary))
      (and json-summary (:truncated? json-summary))
      (assoc :json-body-truncated? true))))

(defn- capture-json-request [request]
  (let [content-type (get-in request [:headers "content-type"])]
    (if (http/json-content-type? content-type)
      (let [[body-value replay-body] (coerce-json-body (:body request))
            json-body (cond
                        (string? body-value) body-value
                        (byte-array? body-value) (bytes->string body-value)
                        :else nil)
            updated-request (if replay-body
                              (assoc request :body replay-body)
                              request)]
        [json-body updated-request])
      [nil request])))

(defn- capture-json-response [response]
  (let [content-type (get-in response [:headers "Content-Type"])]
    (if (http/json-content-type? content-type)
      (let [[body-value replay-body] (coerce-json-body (:body response))
            json-body (cond
                        (string? body-value) body-value
                        (byte-array? body-value) (bytes->string body-value)
                        :else nil)
            updated-response (if replay-body
                               (assoc response :body replay-body)
                               response)]
        [json-body updated-response])
      [nil response])))

(defn wrap-request-logging [handler]
  (fn [request]
    (let [[json-request-body request*] (capture-json-request request)
          {:keys [method uri details]} (summarize-request request* json-request-body)
          started (System/nanoTime)]
      (if (seq details)
        (log/infof "-> %s %s %s" method uri (pr-str details))
        (log/infof "-> %s %s" method uri))
      (try
        (let [response (handler request*)
              elapsed (elapsed-ms started)
              [json-response-body response*] (capture-json-response response)
              summary (summarize-response response* elapsed json-response-body)]
          (log/infof "<- %s %s %s" method uri (pr-str summary))
          response*)
        (catch Exception ex
          (let [elapsed (elapsed-ms started)]
            (log/errorf ex "X %s %s failed after %dms" method uri elapsed)
            (throw ex)))))))
