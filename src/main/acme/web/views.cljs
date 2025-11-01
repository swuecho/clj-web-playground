(ns acme.web.views
  (:require
   [acme.web.components.add-user-dialog :refer [add-user-dialog]]
   [acme.web.components.edit-user-dialog :refer [edit-user-dialog]]
   [acme.web.components.overview-panel :refer [overview-panel]]
   [acme.web.components.sidebar :as sidebar]
   [acme.web.components.toast-banner :refer [toast-banner]]
   [acme.web.components.todo-add-dialog :refer [todo-add-dialog]]
   [acme.web.components.todo-edit-dialog :refer [todo-edit-dialog]]
   [acme.web.components.todo-table :refer [todo-table]]
   [acme.web.components.users-table :refer [users-table]]
   [acme.web.events :as events]
   [acme.web.feature.daisy.demo :refer [daisy-ui-showcase]]
   [re-frame.core :as rf]
   [reagent.core :as r]))

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
      (let [{:keys [label description]} (get sidebar/sidebar-nav-map @active)]
        [:div {:class "min-h-screen bg-base-200/60 text-base-content"}
         [:div {:class "flex min-h-screen flex-col md:flex-row"}
          [sidebar/sidebar {:active-id @active
                             :on-select #(reset! active %)}]
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
