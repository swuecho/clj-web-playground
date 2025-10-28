(ns dev.build-hooks
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]))

(def sass-input "public/css/users-table-rc.scss")
(def sass-output "public/css/users-table-rc.css")

(defn- file->mtime [path]
  (when-let [file (io/file path)]
    (when (.exists file)
      (.lastModified file))))

(def ^:private last-build (atom nil))

(defn run-sass
  {:shadow.build/stage :flush
   :shadow.build/mode #{:dev :release}}
  [build-state & _]
  (let [in-mtime (file->mtime sass-input)
        out-mtime (file->mtime sass-output)
        last-run @last-build
        should-run? (or (nil? out-mtime)
                        (nil? last-run)
                        (> in-mtime out-mtime)
                        (> in-mtime last-run))]
    (when should-run?
      (let [{:keys [exit err]} (shell/sh "npx" "sass" "--no-source-map" sass-input sass-output)]
        (reset! last-build (System/currentTimeMillis))
        (if (zero? exit)
          (println "[sass] compiled" sass-input "->" sass-output)
          (throw (ex-info "Sass compilation failed" {:error err})))))
    build-state))
