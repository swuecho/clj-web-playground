(ns acme.web.db)

(def default-db
  {:users []
   :loading? false
   :error nil
   :add-user {:visible? false
              :name ""
              :age "0"
              :submitting? false
              :errors {}}
   :toast {:current nil
           :queue []}})
