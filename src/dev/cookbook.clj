(ns cookbook)
(Integer/parseInt "42")

(Double/parseDouble "3.14")

(int 2.2)
(Math/ceil 2.2)

(Math/toRadians 180)

(mapv #(Math/toRadians %) [0 90 180 270 360])
(map #(Math/toRadians %) [0 90 180 270 360])

(comment
  ;; = map - Example 1 = 
  
  (map inc [1 2 3 4 5])
  ;;=> (2 3 4 5 6)
  
  
  ;; map can be used with multiple collections. Collections will be consumed
  ;; and passed to the mapping function in parallel:
  (map + [1 2 3] [4 5 6])
  ;;=> (5 7 9)
  
  
  ;; When map is passed more than one collection, the mapping function will
  ;; be applied until one of the collections runs out:
  (map + [1 2 3] (iterate inc 1))
  ;;=> (2 4 6)
  
  
  
  ;; map is often used in conjunction with the # reader macro:
  (map #(str "Hello " % "!" ) ["Ford" "Arthur" "Tricia"])
  ;;=> ("Hello Ford!" "Hello Arthur!" "Hello Tricia!")
  
  ;; A useful idiom to pull "columns" out of a collection of collections. 
  ;; Note, it is equivalent to:
  ;; user=> (map vector [:a :b :c] [:d :e :f] [:g :h :i])
  
  (apply map vector [[:a :b :c]
                     [:d :e :f]
                     [:g :h :i]])
  
  ;;=> ([:a :d :g] [:b :e :h] [:c :f :i])
  
  ;; From http://clojure-examples.appspot.com/clojure.core/map
  
  ;; See also:
  clojure.core/map-indexed
  clojure.core/pmap
  clojure.core/amap
  clojure.core/mapcat
  clojure.core/keep
  clojure.core/juxt
  clojure.core/mapv
  clojure.core/reduce
  clojure.core/run!
  clojure.core/filter
  :rcf)


; namespaced keywords example
(defn my-function [data]
  (println (::my-key data)))    ; Auto-resolved to :my-project.core/my-key

(my-function {::my-key "Hello from my-project"}) ; 