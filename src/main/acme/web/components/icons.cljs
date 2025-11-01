(ns acme.web.components.icons)

(def ^:private icon-attrs
  {:class "h-5 w-5"
   :alt ""
   :aria-hidden "true"})

(defn- icon [filename]
  [:img (assoc icon-attrs :src (str "/assets/icons/" filename))])

(defn overview-icon []
  (icon "overview.svg"))

(defn users-icon []
  (icon "users.svg"))

(defn todo-icon []
  (icon "todo.svg"))

(defn demo-icon []
  (icon "demo.svg"))

(defn delete-2-icon []
  (icon "delete_2.svg"))

(defn edit-2-icon []
  (icon "edit_2.svg"))

(defn eye-icon []
  (icon "eye.svg"))
