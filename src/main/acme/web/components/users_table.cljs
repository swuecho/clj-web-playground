(ns acme.web.components.users-table
  (:require
   [acme.web.components.add-user-dialog :refer [add-user-dialog]]
   [acme.web.components.edit-user-dialog :refer [edit-user-dialog]]
   [acme.web.components.toast-banner :refer [toast-banner]]
   [acme.web.components.user-row-actions :refer [user-row-actions]]
   [acme.web.events :as events]
   [acme.web.subs :as subs]
   [re-frame.core :as rf]))

(defn users-table
  ([] (users-table {}))
  ([opts]
   (let [{:keys [title include-aux? wrap? actions?]
          :or {title "Users"
               include-aux? true
               wrap? true
               actions? true}} opts
         users (rf/subscribe [::subs/users])
         loading? (rf/subscribe [::subs/loading?])
         error (rf/subscribe [::subs/error])
         container-classes (str "space-y-6 "
                                (when wrap? "max-w-5xl mx-auto p-6"))]
     (fn []
       (let [table-section (cond
                             @loading? [:div {:class "alert alert-info"}
                                        [:span "Loading users..."]]
                             @error [:div {:class "alert alert-error"}
                                     [:span @error]]
                             (empty? @users) [:div {:class "alert alert-warning"}
                                              [:span "No users found."]]
                             :else
                             [:div {:class "rounded-sm border border-base-200 bg-base-100 shadow-sm"}
                              [:div {:class "overflow-x-auto"}
                               [:table {:class "table table-zebra"}
                                [:thead
                                 [:tr {:class "bg-base-200 text-sm uppercase tracking-wide text-base-content/70"}
                                  [:th {:class "font-semibold"} "UUID"]
                                  [:th {:class "font-semibold"} "Name"]
                                  [:th {:class "font-semibold"} "Age"]
                                  [:th {:class "font-semibold text-right"} "Actions"]]]
                                [:tbody
                                 (for [{:keys [uuid name age]} @users]
                                   ^{:key uuid}
                                   [:tr
                                    [:td {:class "font-mono text-sm align-middle"} uuid]
                                    [:td {:class "align-middle"} name]
                                    [:td {:class "align-middle"} age]
                                    [:td {:class "align-middle text-right"}
                                     [user-row-actions uuid]]])]]]])
             actions [:div {:class "flex flex-wrap gap-3"}
                      [:button {:type "button"
                                :class "btn btn-outline"
                                :on-click #(rf/dispatch [::events/fetch-users])}
                       "Reload"]
                      [:button {:type "button"
                                :class "btn btn-primary"
                                :on-click #(rf/dispatch [::events/open-add-user-dialog])}
                       "Add User"]]
             content (cond-> []
                       include-aux? (conj [toast-banner])
                       include-aux? (conj [add-user-dialog])
                       include-aux? (conj [edit-user-dialog])
                       title (conj [:h1 {:class "text-2xl font-semibold"} title])
                       true (conj table-section)
                       actions? (conj actions))]
         (into (if wrap?
                 [:div {:class container-classes}]
                 [:div {:class "space-y-6"}])
               content))))))