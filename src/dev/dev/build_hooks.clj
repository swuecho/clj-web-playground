(ns dev.build-hooks
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]))

(def sass-input "public/css/users-table-rc.scss")
(def sass-output "public/css/users-table-rc.css")

(def tailwind-input "src/styles/main-tailwind.css")
(def tailwind-output "public/css/main-tailwind.css")
(def tailwind-config "tailwind.config.js")

(defn- file->mtime [path]
  (let [file (io/file path)]
    (when (.exists file)
      (.lastModified file))))

(defonce last-build (atom nil))
(defonce watcher-state (atom nil))

(defn- compile-sass! []
  (let [{:keys [exit err out]} (shell/sh "npx" "sass" "--no-source-map" sass-input sass-output)]
    (if (zero? exit)
      (do
        (reset! last-build (file->mtime sass-input))
        (println "[sass] compiled" sass-input "->" sass-output))
      (do
        (binding [*out* *err*]
          (println "[sass] compile failed:" err))
        (throw (ex-info "Sass compilation failed" {:error err :stdout out :exit exit}))))))

(defn- compile-tailwind! [mode]
  (let [args (cond-> ["npx" "@tailwindcss/cli"
                      "-i" tailwind-input
                      "-o" tailwind-output
                      "-c" tailwind-config]
               (= mode :release) (conj "--minify"))
        {:keys [exit err out]} (apply shell/sh args)]
    (if (zero? exit)
      (if (= mode :release)
        (println "[tailwind] compiled" tailwind-input "->" tailwind-output "(minified)")
        (println "[tailwind] compiled" tailwind-input "->" tailwind-output))
      (do
        (binding [*out* *err*]
          (println "[tailwind] compile failed:" err))
        (throw (ex-info "Tailwind compilation failed" {:error err :stdout out :exit exit}))))))

(defn- stop-watcher! []
  (when-let [{:keys [running]} @watcher-state]
    (reset! running false)
    (reset! watcher-state nil)
    (println "[sass] watcher stopped")))

(defn- start-watcher! [{:keys [poll-interval-ms]
                        :or {poll-interval-ms 300}}]
  (when-not @watcher-state
    (println "[sass] watcher started (poll" poll-interval-ms "ms)")
    (let [running? (atom true)
          thread (future
                   (loop []
                     (when @running?
                       (Thread/sleep poll-interval-ms)
                       (when-let [current (file->mtime sass-input)]
                         (let [compiled @last-build]
                           (when (or (nil? compiled)
                                     (> current (or compiled 0)))
                             (try
                               (compile-sass!)
                               (catch Exception e
                                 (binding [*out* *err*]
                                   (println "[sass] watcher error" (.getMessage e))))))))
                       (recur))))]
      (reset! watcher-state {:thread thread :running running?})
      (.addShutdownHook (Runtime/getRuntime)
                        (Thread. stop-watcher!)))))

(defn run-sass
  {:shadow.build/stage :flush
   :shadow.build/mode #{:dev :release}}
  [build-state & [opts]]
  (let [mode (or (:shadow.build/mode build-state) :dev)
        in-mtime (file->mtime sass-input)
        out-mtime (file->mtime sass-output)
        last-run @last-build
        should-run? (or (nil? out-mtime)
                        (nil? last-run)
                        (and in-mtime (> in-mtime (or out-mtime 0)))
                        (and in-mtime (> in-mtime (or last-run 0))))]
    (when should-run?
      (compile-sass!))
    (compile-tailwind! mode)
    (let [watch-config (get-in build-state [:shadow.build/config :watch?])
          watch-mode? (if (some? watch-config)
                        (true? watch-config)
                        (:watch? opts true))]
      (when watch-mode?
        (start-watcher! opts)))
    build-state))
