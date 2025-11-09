(ns acme.server.schemas.validation.users
  (:require
   [clojure.string :as str]))

(def email-regex
  #"^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$")

(def password-min-length 8)

(defn- trim-or-nil [value]
  (let [trimmed (some-> value str str/trim)]
    (when (seq trimmed)
      trimmed)))

(defn normalize-email [email]
  (some-> email trim-or-nil str/lower-case))

(defn valid-email? [email]
  (boolean
   (when-let [value (normalize-email email)]
     (re-matches email-regex value))))

(defn normalize-password [password]
  (trim-or-nil password))

(defn password-valid? [password]
  (and (string? password)
       (>= (count password) password-min-length)))

(def password-too-short-message
  (format "Password must be at least %d characters" password-min-length))

(defn ensure-password
  "Normalize PASSWORD and ensure it meets the minimum requirements.
  Returns {:value normalized} when valid or {:error <message>} otherwise."
  [password]
  (let [normalized (normalize-password password)]
    (cond
      (nil? normalized)
      {:error "Password is required"}

      (not (password-valid? normalized))
      {:error password-too-short-message}

      :else
      {:value normalized})))
