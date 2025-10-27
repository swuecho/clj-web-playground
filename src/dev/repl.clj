(ns repl)

(defn start []
  ::started)

(defn stop []
  ::stopped)

(defn go []
  (stop)
  (start))