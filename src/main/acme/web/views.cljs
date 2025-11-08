(ns acme.web.views
  (:require
   [acme.web.components.overview-panel :refer [overview-panel]]
   [acme.web.components.sidebar :as sidebar]
   [acme.web.feature.auth.events :as auth-events]
   [acme.web.feature.auth.login :refer [login-panel]]
   [acme.web.feature.daisy.demo :refer [daisy-ui-showcase]]
   [acme.web.feature.toast.components.banner :refer [toast-banner]]
   [acme.web.feature.todos.components.todo-add-dialog :refer [todo-add-dialog]]
   [acme.web.feature.todos.components.todo-edit-dialog :refer [todo-edit-dialog]]
   [acme.web.feature.todos.components.todo-table :refer [todo-table]]
   [acme.web.feature.users.components.add-user-dialog :refer [add-user-dialog]]
   [acme.web.feature.users.components.edit-user-dialog :refer [edit-user-dialog]]
   [acme.web.feature.users.components.users-table :refer [users-table]]
   [acme.web.feature.users.events :as user-events]
   [acme.web.feature.auth.subs :as auth-subs]
   [re-frame.core :as rf]
   [reagent.core :as r]))

(defn users-panel []
  [:<>
   [add-user-dialog]
   [edit-user-dialog]
   [:div {:class "space-y-6"}
    [:div {:class "flex flex-wrap items-center justify-between gap-4"}
     [:div
      [:h2 {:class "text-2xl font-semibold text-base-content"} "User Directory"]
      [:p {:class "text-sm text-base-content/70"}
       "Manage the people who can access your workspace."]]
     [:div {:class "flex flex-wrap gap-2"}
      [:button {:type "button"
                :class "btn btn-outline btn-sm"
                :on-click #(rf/dispatch [::user-events/fetch-users])}
       "Reload"]
      [:button {:type "button"
                :class "btn btn-primary btn-sm"
                :on-click #(rf/dispatch [::user-events/open-add-user-dialog])}
       "Add User"]]]
    [users-table {:wrap? false
                  :include-aux? false
                  :title nil
                  :actions? false}]]])

(defn todos-panel []
  [:<>
   [todo-add-dialog]
   [todo-edit-dialog]
   [:div {:class "space-y-6"}
    [:div
     [:h2 {:class "text-2xl font-semibold text-base-content"} "Todo Board"]
     [:p {:class "text-sm text-base-content/70"}
      "Track work in progress, filter by status, and keep momentum up."]]
    [todo-table]]])

(def placeholder-card-classes
  "rounded-xl border border-dashed border-base-300 bg-base-100/60 text-center text-base-content/60")

(defn placeholder-card [{:keys [message padding-class]}]
  [:div {:class (str placeholder-card-classes " " (or padding-class "p-16"))}
   message])

(defn users-section [can-manage-users?]
  (if can-manage-users?
    [users-panel]
    [placeholder-card {:message "Admin access required"
                       :padding-class "p-10"}]))

(defn workspace-header [{:keys [label description]}]
  [:header {:class "flex flex-wrap items-center justify-between gap-4"}
   [:div {:class "space-y-1"}
    [:p {:class "text-xs font-semibold uppercase tracking-wide text-base-content/60"}
     "Workspace"]
    [:h1 {:class "text-3xl font-semibold text-base-content"} (or label "Overview")]
    (when description
      [:p {:class "text-sm text-base-content/70"} description])]])

(defn workspace-section [{:keys [active-id can-manage-users? set-active]}]
  (case active-id
    :overview [overview-panel {:on-view-users (when can-manage-users?
                                                #(set-active :users))
                               :on-view-todos #(set-active :todos)
                               :can-manage-users? can-manage-users?}]
    :users [users-section can-manage-users?]
    :todos [todos-panel]
    :demo [daisy-ui-showcase]
    [placeholder-card {:message "Section coming soon."}]))

(defn workspace-main [{:keys [label description active-id can-manage-users? set-active]}]
  [:main {:class "flex-1 min-h-0"}
   [:div {:class "mx-auto flex h-full max-h-screen flex-col gap-10 overflow-y-auto px-6 py-10"}
    [workspace-header {:label label :description description}]
    [:div {:class "space-y-10"}
     [workspace-section {:active-id active-id
                         :can-manage-users? can-manage-users?
                         :set-active set-active}]]]])

(defn workspace-layout [{:keys [sidebar-props main-props]}]
  [:div {:class "min-h-screen bg-base-200/60 text-base-content"}
   [:div {:class "flex min-h-screen flex-col md:flex-row"}
    [sidebar/sidebar sidebar-props]
    [workspace-main main-props]]])

(defn workspace-shell []
  (let [current-user (rf/subscribe [::auth-subs/user])]
    (r/with-let [active (r/atom :overview)]
      (fn []
        (let [user @current-user
              role (:role user)
              can-manage-users? (= "admin" role)
              nav-items (sidebar/nav-items-for-role role)
              nav-map (sidebar/nav-map nav-items)
              logout! #(rf/dispatch [::auth-events/logout {:reason "Signed out"
                                                           :silent? false}])
              set-active #(reset! active %)
              active-id (let [candidate @active]
                          (if (and (not can-manage-users?) (= candidate :users))
                            :overview
                            candidate))
              {:keys [label description]} (get nav-map active-id)
              user-email (or (:email user)
                             (:name user)
                             (:uuid user))]
          [workspace-layout {:sidebar-props {:active-id active-id
                                             :on-select set-active
                                             :user-email user-email
                                             :nav-items nav-items
                                             :on-logout logout!}
                              :main-props {:label label
                                           :description description
                                           :active-id active-id
                                           :can-manage-users? can-manage-users?
                                           :set-active set-active}}])))))

(defn main-panel2 []
  [:<>
   [toast-banner]
   [login-panel]])

(defn main-panel3 []
  [:<>
   [toast-banner]
   (let [isLoggedIn? (rf/subscribe [::auth-subs/is-logged-in?])]
     (fn []
       (if @isLoggedIn? [login-panel]  [workspace-shell])))])
;; - Reagent components must return something renderable (hiccup data, string, React element). In your original version the outer [:<> …] has
;;  three children: [toast-banner], the let form, and the anonymous (fn [] …) that the let evaluates to. That final child is just a function
;;  object; React/Reagent can’t render a bare function, so it short‑circuits before ever executing it.
;;  - Form‑2 components solve this by returning the function itself (not by nesting it inside already-rendered hiccup). Reagent calls that
;;  function during render, so whatever hiccup you produce inside it becomes the component output. The subscription lives in the outer scope so
;;  it’s created once per instance, while the inner function handles rerenders.
(defn main-panel []
  (let [is-logged-in? (rf/subscribe [::auth-subs/is-logged-in?])]
    (fn []
      [:<>
       [toast-banner]
       (if @is-logged-in? [workspace-shell]  [login-panel])])))
