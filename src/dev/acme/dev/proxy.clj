(ns acme.dev.proxy
  "Helper predicates used by the shadow-cljs dev HTTP server to decide which
  requests should be proxied to the Clojure backend."
  (:import [io.undertow.server HttpServerExchange]))

(def ^:private api-prefix "/api")

(defn should-proxy?
  "Return true when the incoming request path should be forwarded to the
  backend. shadow-cljs passes the Undertow exchange plus the HTTP server config
  map."
  [^HttpServerExchange exchange _config]
  (let [path (.getRequestPath exchange)]
    (.startsWith path api-prefix)))
