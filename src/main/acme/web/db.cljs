(ns acme.web.db)

(def default-todo-filters
  {:completed :all
   :created {:after "" :before ""}
   :updated {:after "" :before ""}})

(def default-db
  {:isLoggedIn? false
   :user nil
   :auth {:token nil
          :expires-at nil
          :has-refresh? false
          :logging-in? false
          :refreshing? false
          :error nil
          :mode :login
          :register {:submitting? false
                     :error nil}}
   :users []
   :loading? false
   :error nil
   :add-user {:visible? false
              :name ""
              :age "0"
              :email ""
              :password ""
              :submitting? false
              :errors {}}
   :edit-user {:visible? false
               :uuid nil
               :name ""
               :age "0"
               :email ""
               :password ""
               :submitting? false
               :errors {}
               :initial {:name ""
                         :age 0
                         :email ""}}
   :pending-deletes #{}
   :user-sessions {:visible? false
                   :user nil
                   :tokens []
                   :loading? false
                   :error nil
                   :revoking #{} }
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
