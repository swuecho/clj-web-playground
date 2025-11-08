(ns acme.tools.create-user
  (:gen-class)
  (:require
   [clojure.string :as str]
   [acme.server.auth :as auth]
   [acme.server.db :as db]
   [acme.server.users.validation :as validation])
  (:import
   (java.util UUID)))

(defn- usage []
  (println "Usage: clj -M:create-user --name NAME --email EMAIL --password PASSWORD [--age AGE] [--uuid UUID] [--role ROLE]")
  (println)
  (println "Options:")
  (println "  --name       Required. Full name for the user.")
  (println "  --email      Required. Unique email address.")
  (println "  --password   Required. Plain text password to hash before storing.")
  (println "  --age        Optional. Non-negative integer (default 0).")
  (println "  --uuid       Optional. Supply to control the uuid; otherwise generated.")
  (println "  --role       Optional. Either 'admin' or 'user' (default 'user')."))

(defn- parse-int [value]
  (try
    (Integer/parseInt (str/trim (str value)))
    (catch Exception _ nil)))

(defn- parse-args [args]
  (loop [result {}
         remaining args]
    (if (seq remaining)
      (let [[flag value & more] remaining]
        (cond
          (nil? flag) result
          (and (#{"--name" "--email" "--password" "--age" "--uuid" "--role"} flag)
               (nil? value))
          (do
            (println "Missing value for" flag)
            (System/exit 1))

          :else
          (case flag
            "--name" (recur (assoc result :name value) more)
            "--email" (recur (assoc result :email value) more)
            "--password" (recur (assoc result :password value) more)
            "--age" (recur (assoc result :age value) more)
            "--uuid" (recur (assoc result :uuid value) more)
            "--role" (recur (assoc result :role value) more)
            (do
              (println "Unknown option" flag)
              (usage)
              (System/exit 1)))))
      result)))

(defn- exit! [code message]
  (binding [*out* *err*]
    (when (seq message)
      (println message)))
  (System/exit code))

(defn- ensure-email! [email]
  (let [normalized (validation/normalize-email email)]
    (cond
      (str/blank? (or email "")) (exit! 1 "--email is required")
      (nil? normalized) (exit! 1 "Email is invalid")
      (not (validation/valid-email? normalized)) (exit! 1 "Email is invalid")
      (db/query-one ["select 1 from \"UserTable\" where lower(email) = ? limit 1" normalized])
      (exit! 1 "Email already exists in UserTable")
      :else normalized)))

(defn- ensure-name! [name]
  (let [trimmed (some-> name str str/trim)]
    (if (seq trimmed)
      trimmed
      (exit! 1 "--name is required"))))

(defn- ensure-password! [password]
  (let [trimmed (some-> password str str/trim)]
    (cond
      (str/blank? trimmed) (exit! 1 "--password is required")
      (< (count trimmed) 8) (exit! 1 "Password must be at least 8 characters")
      :else trimmed)))

(defn- ensure-age! [value]
  (if (nil? value)
    0
    (let [parsed (parse-int value)]
      (cond
        (nil? parsed) (exit! 1 "--age must be a non-negative integer")
        (neg? parsed) (exit! 1 "--age must be a non-negative integer")
        :else parsed))))

(defn- ensure-uuid! [value]
  (if (str/blank? (or value ""))
    (UUID/randomUUID)
    (try
      (UUID/fromString (str/trim value))
      (catch Exception _
        (exit! 1 "--uuid must be a valid UUID")))))

(defn- ensure-role! [value]
  (let [normalized (some-> value str str/lower-case str/trim)]
    (cond
      (or (nil? normalized) (str/blank? normalized)) "user"
      (# {"admin" "user"} normalized) normalized
      :else (exit! 1 "--role must be 'admin' or 'user'"))))

(defn- insert-user! [{:keys [uuid name age email password role]}]
  (let [password-hash (auth/hash-password password)
        row (db/query-one ["insert into \"UserTable\" (uuid, name, age, email, password_hash, role)
                            values (?, ?, ?, ?, ?, ?)
                            returning uuid::text as uuid, \"name\" as name, age as age, email, role"
                           uuid name age email password-hash role])]
    (println "Created user:" row)
    row))

(defn -main [& args]
  (let [{:keys [name email password age uuid role]} (parse-args args)
        name (ensure-name! name)
        email (ensure-email! email)
        password (ensure-password! password)
        age (ensure-age! age)
        uuid (ensure-uuid! uuid)
        role (ensure-role! role)]
    (insert-user! {:uuid uuid
                   :name name
                   :age age
                   :email email
                   :password password
                   :role role})
    (System/exit 0)))
