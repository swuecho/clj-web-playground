(ns acme.web.views
  (:require
   [acme.web.components.add-user-dialog :refer [add-user-dialog]]
   [acme.web.components.edit-user-dialog :refer [edit-user-dialog]]
   [acme.web.components.icons :as icons]
   [acme.web.components.overview-panel :refer [overview-panel]]
   [acme.web.components.toast-banner :refer [toast-banner]]
   [acme.web.components.todo-add-dialog :refer [todo-add-dialog]]
   [acme.web.components.todo-edit-dialog :refer [todo-edit-dialog]]
   [acme.web.components.todo-table :refer [todo-table]]
   [acme.web.components.users_table :refer [users-table]]
   [acme.web.events :as events]
   [acme.web.feature.daisy.demo :refer [daisy-ui-showcase]]
   [clojure.string :as str]
   [re-frame.core :as rf]
   [reagent.core :as r]))

(def sidebar-nav-items
  [{:id :overview
    :label "Overview"
    :description "Workspace snapshot and quick actions"
    :icon [icons/overview-icon]
    :icon-text "OV"
    :accent "bg-primary/10 text-primary"}
   {:id :users
    :label "Users"
    :description "Manage the people in your workspace"
    :icon [icons/users-icon]
    :icon-text "US"
    :accent "bg-secondary/10 text-secondary"}
   {:id :todos
    :label "Todos"
    :description "Track tasks and completion progress"
    :icon [icons/todo-icon]
    :icon-text "TD"
    :accent "bg-accent/10 text-accent"}
   {:id :demo
    :label "Components Demo"
    :description "Explore sample tables and DaisyUI patterns"
    :icon [icons/demo-icon]
    :icon-text "UI"
    :accent "bg-info/10 text-info"}])

(def sidebar-nav-map
  (into {} (map (juxt :id identity) sidebar-nav-items)))

(defn sidebar-nav-button
  [{:keys [id label icon icon-text accent disabled?]} active-id on-select]
  (let [active? (= id active-id)
        base-classes (cond
                       disabled? "border-transparent text-base-content/40 cursor-not-allowed opacity-60"
                       active? "border-primary/40 bg-base-100 text-base-content shadow-sm"
                       :else "border-transparent text-base-content/70 hover:text-base-content hover:bg-base-200/60")
        icon-classes (if active?
                       "bg-primary text-primary-content shadow-sm"
                       (or accent "bg-base-200 text-base-content/70"))
        icon-label (or icon-text (some-> label (subs 0 1) str/upper-case))]
    [:button {:type "button"
              :class (str "group relative flex w-full items-center gap-3 rounded border px-2 py-2 text-left text-sm font-semibold transition-colors duration-150 "
                          base-classes)
              :on-click #(when-not disabled? (on-select id))
              :disabled disabled?}
     [:span {:class (str "flex h-9 w-9 items-center justify-center rounded-lg text-xs font-semibold tracking-wide transition-colors duration-150 "
                         icon-classes)}
      (or icon icon-label)]
     [:span {:class (str "text-sm font-semibold transition-colors duration-150 "
                         (if active? "text-base-content" "text-base-content"))}
      label]]))


(defn users-panel []
  [:div {:class "space-y-6"}
   [:div {:class "flex flex-wrap items-center justify-between gap-4"}
    [:div
     [:h2 {:class "text-2xl font-semibold text-base-content"} "User Directory"]
     [:p {:class "text-sm text-base-content/70"}
      "Manage the people who can access your workspace."]]
    [:div {:class "flex flex-wrap gap-2"}
     [:button {:type "button"
               :class "btn btn-outline btn-sm"
               :on-click #(rf/dispatch [::events/fetch-users])}
      "Reload"]
     [:button {:type "button"
               :class "btn btn-primary btn-sm"
               :on-click #(rf/dispatch [::events/open-add-user-dialog])}
      "Add User"]]]
   [users-table {:wrap? false
                 :include-aux? false
                 :title nil
                 :actions? false}]])

(defn todos-panel []
  [:div {:class "space-y-6"}
   [:div
    [:h2 {:class "text-2xl font-semibold text-base-content"} "Todo Board"]
    [:p {:class "text-sm text-base-content/70"}
     "Track work in progress, filter by status, and keep momentum up."]]
   [todo-table]])

(defn workspace-shell []
  (r/with-let [active (r/atom :overview)]
    (fn []
      (let [{:keys [label description]} (get sidebar-nav-map @active)]
        [:div {:class "min-h-screen bg-base-200/60 text-base-content"}
         [:div {:class "flex min-h-screen flex-col md:flex-row"}
          [:aside {:class "w-full shrink-0 border-b border-base-200 bg-base-100/95 md:w-72 md:border-b-0 md:border-r"}
           [:div {:class "flex h-full flex-col gap-8 px-5 py-8"}
            [:div {:class "flex items-center gap-3"}
             [:div {:class "flex h-10 w-10 items-center justify-center rounded-2xl bg-primary text-lg font-semibold text-primary-content"}
              "AC"]
             [:div
              [:p {:class "text-xs font-semibold uppercase tracking-wide text-base-content/60"} "Acme"]
              [:p {:class "text-lg font-semibold text-base-content"} "Control Center"]]]
            (let [active-id @active]
              [:nav {:class "flex-1 space-y-1"}
               (for [item sidebar-nav-items]
                 ^{:key (:id item)}
                 [sidebar-nav-button item active-id #(reset! active %)])])
            [:div {:class "rounded-2xl border border-base-200 bg-base-100 px-4 py-3 text-sm text-base-content/60"}
             [:p {:class "font-semibold text-base-content"} "Need help?"]
             [:p {:class "text-sm"}
              "Visit the docs directory for setup guides and API references."]]]]
          [:main {:class "flex-1 overflow-y-auto"}
           [:div {:class "mx-auto w-full max-w-6xl px-6 py-10 space-y-10"}
            [:header {:class "flex flex-wrap items-center justify-between gap-4"}
             [:div {:class "space-y-1"}
              [:p {:class "text-xs font-semibold uppercase tracking-wide text-base-content/60"}
               "Workspace"]
              [:h1 {:class "text-3xl font-semibold text-base-content"} (or label "Overview")]
              (when description
                [:p {:class "text-sm text-base-content/70"} description])]]
            [:div {:class "space-y-10"}
             (case @active
               :overview [overview-panel {:on-view-users #(reset! active :users)
                                          :on-view-todos #(reset! active :todos)}]
               :users [users-panel]
               :todos [todos-panel]
               :demo [daisy-ui-showcase]
               [:div {:class "rounded-xl border border-dashed border-base-300 bg-base-100/60 p-16 text-center text-base-content/60"}
                "Section coming soon."])]]]]]))))

(defn main-panel []
  [:<>
   [toast-banner]
   [add-user-dialog]
   [edit-user-dialog]
   [todo-add-dialog]
   [todo-edit-dialog]
   [workspace-shell]])
