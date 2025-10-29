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

(defn- summarize-request [request json-body]
  (let [method (some-> request :request-method name str/upper-case)
        uri (:uri request)
        details (cond-> {}
                   (:query-string request)
                   (assoc :query-string (:query-string request))
                   (present-map (:path-params request))
                   (assoc :path-params (:path-params request))
                   (present-map (:query-params request))
                   (assoc :query-params (:query-params request))
                   (present-map (:body-params request))
                   (assoc :body-params (:body-params request))
                   (and json-body (not (str/blank? json-body)))
                   (assoc :json-body json-body))]
    {:method method
     :uri uri
     :details details}))

(defn- summarize-response [response elapsed json-body]
  (let [content-type (get-in response [:headers "Content-Type"])]
    (cond-> {:status (:status response)
             :elapsed-ms elapsed}
      content-type (assoc :content-type content-type)
      (and json-body (not (str/blank? json-body)))
      (assoc :json-body json-body))))

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
