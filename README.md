
## start clj-repl

npx shadow-cljs clj-repl



## proxy

to verify

> The dev server is doing exactly what you asked it to do in shadow-cljs.edn:4: once :proxy-url is present under :dev-http {8080 …}, every
  request that doesn’t resolve to a concrete file in public/ is forwarded to http://192.168.0.135:8085. A bare GET / doesn’t map to an actual
  file name, so the proxy fires first and your backend replies with a 404, which is what the browser shows.

  Two easy ways to get the SPA root back while still proxying your API calls:

  - Make the dev server fall back to public/index.html before it tries the proxy by adding :push-state/index "index.html":

    :dev-http {8080 {:root "public"
                     :push-state/index "index.html"
                     :proxy-url "http://192.168.0.135:8085"}}

    After that, requests for / (and any other unknown client-side route) will serve index.html; only genuine API calls will continue to go
  through the proxy.
  - If you only want specific paths to hit the proxy, switch from :proxy-url to :proxy and scope it to your API prefix, e.g.

    :dev-http {8080 {:root "public"
                     :push-state/index "index.html"
                     :proxy {"^/users" {:target "http://192.168.0.135:8085"}
                             "^/api"   {:target "http://192.168.0.135:8085"}}}}

    (Use whatever regex/prefix matches your API routes.)

  After updating the config, restart npx shadow-cljs watch web and re-check http://localhost:8080/. If you still see a 404, curl http://
  localhost:8080/index.html to confirm the static file is reachable, and double-check the proxy target really serves the paths you expect.