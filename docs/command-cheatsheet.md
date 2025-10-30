
clojure -X:deps find-versions :lib io.github.camsaul/toucan2 :sort :version

clojure -Sdeps '{:deps {io.github.camsaul/toucan2 {:mvn/version "1.0.568"}}}' -e "(println (slurp
  │ (clojure.java.io/resource \"toucan2/jdbc/options.clj\")))"

clojure -M -e "(require 'acme.server.db 'acme.server.handlers.users 'acme.server.handlers.health)
  │ (println :ok)"