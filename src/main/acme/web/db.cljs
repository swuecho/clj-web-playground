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
   :edit-user {:visible? false
               :uuid nil
               :name ""
               :age "0"
               :submitting? false
               :errors {}
               :initial {:name ""
                         :age 0}}
   :pending-deletes #{}
   :toast {:current nil
           :queue []}})
