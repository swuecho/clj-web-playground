(ns acme.test-runner
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :as t]))

(def ^:private test-root "src/test")

(defn- test-files []
  (->> (file-seq (io/file test-root))
       (filter #(.isFile ^java.io.File %))
       (filter #(str/ends-with? (.getName ^java.io.File %) "_test.clj"))))

(defn- file->ns [^java.io.File file]
  (-> (.getPath file)
      (str/replace "\\" "/")
      (str/replace (re-pattern (str "^" (java.util.regex.Pattern/quote (str test-root "/")))) "")
      (str/replace #"\.clj$" "")
      (str/replace "_" "-")
      (str/replace "/" ".")
      (symbol)))

(defn -main [& _]
  (let [namespaces (map file->ns (test-files))]
    (doseq [ns namespaces]
      (require ns))
    (let [{:keys [fail error]} (apply t/run-tests namespaces)]
      (when (pos? (+ fail error))
        (System/exit 1)))))
