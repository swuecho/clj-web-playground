(ns acme.web.db)

(def default-todo-filters
  {:completed :all
   :created {:after "" :before ""}
   :updated {:after "" :before ""}})

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
   :todos {:items []
           :loading? false
           :error nil
           :pending #{}
           :sort {:field :created_at
                  :direction :desc}
           :filters default-todo-filters
           :pagination {:page 1
                        :per-page 25}
           :add {:visible? false
                 :title ""
                 :completed? false
                 :submitting? false
                 :errors {}}
           :edit {:visible? false
                  :id nil
                  :title ""
                  :completed? false
                  :submitting? false
                  :errors {}
                  :initial {:title ""
                            :completed? false}}}
   :toast {:current nil
           :queue []}})
