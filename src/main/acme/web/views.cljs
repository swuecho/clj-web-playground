(ns acme.web.views
  (:require
   [clojure.string :as str]
   [reagent.core :as r]
   [re-frame.core :as rf]
   [acme.web.events :as events]
   [acme.web.subs :as subs]
   [acme.web.components.add-user-dialog :refer [add-user-dialog]]
   [acme.web.components.edit-user-dialog :refer [edit-user-dialog]]
   [acme.web.components.toast-banner :refer [toast-banner]]
   [acme.web.components.user-row-actions :refer [user-row-actions]]
   [acme.web.components.todo-add-dialog :refer [todo-add-dialog]]
   [acme.web.components.todo-edit-dialog :refer [todo-edit-dialog]]
   [acme.web.components.icons :as icons]
   [acme.web.components.todo-table :refer [todo-table]]
   ["@rc-component/table" :default rc-table]))

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

(defn users-table-rc
  ([] (users-table-rc {}))
  ([{:keys [title include-aux? wrap?]
     :or {title "Users (rc-table)"
          include-aux? true
          wrap? true}}]
   (let [users (rf/subscribe [::subs/users])
         loading? (rf/subscribe [::subs/loading?])
         error (rf/subscribe [::subs/error])
         columns (clj->js
                  [{:title "UUID"
                    :dataIndex "uuid"
                    :key "uuid"
                    :align "left"
                    :onHeaderCell (fn [_]
                                    #js {:className "users-table-rc__header-cell"})
                    :onCell (fn [_]
                              #js {:className "users-table-rc__body-cell"})
                    :render (fn [uuid _record _index]
                              (r/as-element [:span {:class "users-table-rc__uuid"}
                                             (or uuid "-")]))}
                   {:title "Name"
                    :dataIndex "name"
                    :key "name"
                    :align "left"
                    :onHeaderCell (fn [_]
                                    #js {:className "users-table-rc__header-cell"})
                    :onCell (fn [_]
                              #js {:className "users-table-rc__body-cell"})
                    :render (fn [name _record _index]
                              (r/as-element [:span {:class "users-table-rc__text"}
                                             (or name "-")]))}
                   {:title "Age"
                    :dataIndex "age"
                    :key "age"
                    :width 80
                    :align "left"
                    :onHeaderCell (fn [_]
                                    #js {:className "users-table-rc__header-cell"})
                    :onCell (fn [_]
                              #js {:className "users-table-rc__body-cell"})
                    :render (fn [age _record _index]
                              (r/as-element [:span {:class "users-table-rc__text"}
                                             (if (some? age) age "-")]))}
                   {:title "Actions"
                    :dataIndex "uuid"
                    :key "actions"
                    :align "right"
                    :onHeaderCell (fn [_]
                                    #js {:className "users-table-rc__header-cell users-table-rc__header-cell--right"})
                    :onCell (fn [_]
                              #js {:className "users-table-rc__body-cell users-table-rc__body-cell--right"})
                    :render (fn [uuid _record _index]
                              (r/as-element [:div {:class "users-table-rc__actions flex justify-end"}
                                             (when uuid
                                               [user-row-actions uuid])]))}])
         wrap-container (fn [content]
                          (if wrap?
                            (into [:div {:class "users-table-rc__container space-y-6"}] content)
                            (into [:div {:class "space-y-6"}] content)))]
     (fn []
       (let [table-data (->> @users
                             (mapv (fn [user]
                                     (-> user
                                         (assoc :key (:uuid user)))))
                             (clj->js))
             table-section (cond
                             @loading? [:div {:class "alert alert-info users-table-rc__status users-table-rc__status--loading"}
                                        [:span "Loading users..."]]
                             @error [:div {:class "alert alert-error users-table-rc__status users-table-rc__status--error"}
                                     [:span @error]]
                             (empty? @users) [:div {:class "alert alert-warning users-table-rc__status users-table-rc__status--empty"}
                                              [:span "No users found."]]
                             :else
                             [:div {:class "rounded-sm border border-base-200 bg-base-100 shadow-sm"}
                              [:> rc-table {:columns columns
                                            :data table-data
                                            :rowKey "uuid"
                                            :className "users-table-rc__table"
                                            :tableLayout "auto"
                                            :scroll #js {:x "max-content"}}]])
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
                       title (conj [:h1 {:class "users-table-rc__heading text-2xl font-semibold"} title])
                       true (conj table-section)
                       true (conj actions))]
         (wrap-container content))))))

(defn daisy-ui-button-showcase []
  [:div {:class "flex flex-wrap gap-3"}
   [:button {:type "button" :class "btn"} "Default"]
   [:button {:type "button" :class "btn btn-primary"} "Primary"]
   [:button {:type "button" :class "btn btn-secondary"} "Secondary"]
   [:button {:type "button" :class "btn btn-accent"} "Accent"]
   [:button {:type "button" :class "btn btn-success"} "Success"]
   [:button {:type "button" :class "btn btn-warning"} "Warning"]
   [:button {:type "button" :class "btn btn-error"} "Error"]])

(defn daisy-ui-badge-showcase []
  [:div {:class "flex flex-wrap items-center gap-3"}
   [:div {:class "badge"} "Default"]
   [:div {:class "badge badge-primary"} "Primary"]
   [:div {:class "badge badge-secondary"} "Secondary"]
   [:div {:class "badge badge-accent"} "Accent"]
   [:div {:class "badge badge-success"} "Success"]
   [:div {:class "badge badge-warning"} "Warning"]
   [:div {:class "badge badge-error"} "Error"]])

(defn daisy-ui-card-showcase []
  [:div {:class "grid gap-4 sm:grid-cols-2"}
   [:div {:class "card bg-base-100 shadow"}
    [:div {:class "card-body"}
     [:h3 {:class "card-title"} "Simple Card"]
     [:p "Use cards to group related information and actions."]
     [:div {:class "card-actions justify-end"}
      [:button {:type "button" :class "btn btn-primary"} "Action"]]]]
   [:div {:class "card bg-primary text-primary-content"}
    [:div {:class "card-body"}
     [:h3 {:class "card-title"} "Accent Card"]
     [:p "Cards support themed backgrounds without extra utilities."]
     [:div {:class "card-actions justify-end"}
      [:button {:type "button" :class "btn"} "Dismiss"]]]]])

(defn daisy-ui-alert-showcase []
  [:div {:class "space-y-3"}
   [:div {:class "alert"}
    [:span "Neutral alert keeps things subtle."]]
   [:div {:class "alert alert-info"}
    [:span "Info alerts highlight helpful tips."]]
   [:div {:class "alert alert-warning"}
    [:span "Warning alerts draw attention to potential issues."]]
   [:div {:class "alert alert-error"}
    [:span "Error alerts surface problems that need fixing."]]])

(defn daisy-ui-form-showcase []
  [:form {:class "grid gap-8 sm:grid-cols-2"}
   [:div {:class "form-control w-full max-w-xs space-y-4"}
    [:label {:class "label"}
     [:span {:class "label-text"} "Email"]]
    [:input {:type "email"
             :placeholder "person@example.com"
             :class "input input-bordered"}]]
   [:div {:class "form-control w-full max-w-xs space-y-4"}
    [:label {:class "label"}
     [:span {:class "label-text"} "Role"]]
    [:select {:class "select select-bordered"}
     [:option "Viewer"]
     [:option "Editor"]
     [:option "Admin"]]]
   [:div {:class "form-control w-full max-w-xs space-y-4"}
    [:label {:class "label"}
     [:span {:class "label-text"} "Notes"]]
    [:textarea {:class "textarea textarea-bordered"
                :rows 3
                :placeholder "Add any extra details here"}]]
   [:div {:class "sm:col-span-2 flex gap-3"}
    [:button {:type "submit" :class "btn btn-primary"} "Submit"]
    [:button {:type "button" :class "btn btn-ghost"} "Cancel"]]])

(defn daisy-ui-showcase []
  [:div {:class "space-y-8"}
   [:section
    [:h3 {:class "text-lg font-semibold"} "Buttons"]
    [daisy-ui-button-showcase]]
   [:section
    [:h3 {:class "text-lg font-semibold"} "Badges"]
    [daisy-ui-badge-showcase]]
   [:section
    [:h3 {:class "text-lg font-semibold"} "Cards"]
    [daisy-ui-card-showcase]]
   [:section
    [:h3 {:class "text-lg font-semibold"} "Alerts"]
    [daisy-ui-alert-showcase]]
   [:section
    [:h3 {:class "text-lg font-semibold"} "Form Elements"]
    [daisy-ui-form-showcase]]])

(defn users-tables-tabs []
  (r/with-let [active-tab (r/atom :standard)]
    (let [tabs [{:id :standard :label "Standard Table"}
                {:id :rc :label "RC Table"}
                {:id :daisy :label "Daisy UI"}]
          active @active-tab]
      [:div {:class "mx-auto max-w-5xl space-y-6 p-6"}
       [:div {:class "tabs tabs-boxed flex gap-3"}
        (for [{:keys [id label]} tabs]
          ^{:key (name id)}
          (let [active? (= id active)
                tab-classes (->> ["tab rounded-sm text-sm font-semibold px-5 py-2 transition-colors duration-150"
                                  (when active? "tab-active bg-primary text-primary-content shadow-sm")
                                  (when-not active? "text-base-content/70 hover:text-base-content hover:bg-base-200")]
                                 (remove nil?)
                                 (str/join " "))]
            [:button {:type "button"
                      :class tab-classes
                      :aria-pressed active?
                      :aria-current (when active? "page")
                      :on-click #(reset! active-tab id)}
             label]))]
       [:div {:class "space-y-6"}
        (case active
          :daisy [daisy-ui-showcase]
          :rc [users-table-rc {:wrap? false
                               :include-aux? false
                               :title nil}]
          [users-table {:wrap? false
                        :include-aux? false
                        :title nil}])]])))

(defn todo-tab-panel []
  [:div {:class "mx-auto max-w-5xl space-y-6 p-6"}
   [:h1 {:class "text-2xl font-semibold"} "Todos"]
   [todo-table]])

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
  [{:keys [id label description icon icon-text accent disabled?]} active-id on-select]
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
              :class (str "group relative flex w-full items-center gap-3 rounded border px-4 py-3 text-left text-sm font-semibold transition-colors duration-150 "
                          base-classes)
              :on-click #(when-not disabled? (on-select id))
              :disabled disabled?}
     [:span {:class (str "flex h-9 w-9 items-center justify-center rounded-lg text-xs font-semibold tracking-wide transition-colors duration-150 "
                         icon-classes)}
      (or icon icon-label)]
     [:span {:class (str "text-sm font-semibold transition-colors duration-150 "
                         (if active? "text-base-content" "text-base-content"))}
      label]]))

(defn metric-card [{:keys [title value subtext badge badge-variant]}]
  [:div {:class "rounded-xl border border-base-200 bg-base-100/95 p-5 shadow-sm"}
   [:div {:class "flex items-center justify-between gap-3"}
    [:p {:class "text-xs font-semibold uppercase tracking-wide text-base-content/60"} title]
    (when badge
      [:span {:class (str "badge badge-sm " (or badge-variant "badge-ghost"))} badge])]
   [:p {:class "mt-3 text-3xl font-semibold text-base-content"} value]
   (when subtext
     [:p {:class "mt-3 text-sm text-base-content/70"} subtext])])

(defn overview-panel [{:keys [on-view-users on-view-todos]}]
  (let [users (rf/subscribe [::subs/users])
        todos (rf/subscribe [::subs/todos-items])]
    (fn []
      (let [user-items (vec (or @users []))
            todo-items (vec (or @todos []))
            total-todos (count todo-items)
            completed (count (filter :completed todo-items))
            pending (- total-todos completed)
            completion-rate (if (pos? total-todos)
                              (js/Math.round (* 100 (/ completed total-todos)))
                              0)
            recent-users (take 5 user-items)
            recent-todos (take 5 todo-items)
            initials (fn [name]
                       (let [trimmed (str/trim (or name ""))]
                         (if (seq trimmed)
                           (-> trimmed (subs 0 1) str/upper-case)
                           "?")))
            short-id (fn [value]
                       (when value
                         (let [s (str value)]
                           (if (> (count s) 8)
                             (str (subs s 0 8) "…")
                             s))))]
        [:div {:class "space-y-8"}
         [:div {:class "grid gap-5 sm:grid-cols-2 xl:grid-cols-4"}
          [metric-card {:title "Active Users"
                        :value (str (count user-items))
                        :subtext "People currently in the directory"}]
          [metric-card {:title "Total Todos"
                        :value (str total-todos)
                        :subtext (if (pos? total-todos)
                                   (str completed " completed • " pending " open")
                                   "No todos created yet")}]
          [metric-card {:title "Completion Rate"
                        :value (str completion-rate "%")
                        :subtext (if (pos? total-todos)
                                   "Based on completed todos"
                                   "Add todos to start tracking progress")}]
          [metric-card {:title "Pending Items"
                        :value (str pending)
                        :subtext "Waiting for completion"}]]
         [:div {:class "grid gap-6 lg:grid-cols-2"}
          [:section {:class "rounded-xl border border-base-200 bg-base-100/95 p-6 shadow-sm"}
           [:div {:class "flex items-center justify-between gap-3"}
            [:div
             [:h3 {:class "text-lg font-semibold text-base-content"} "Recent Users"]
             [:p {:class "text-sm text-base-content/70"}
              (if (seq recent-users)
                "Latest additions to your workspace"
                "Invite your first user to get started.")]]
            [:button {:type "button"
                      :class "btn btn-sm btn-outline"
                      :on-click on-view-users}
             "View all"]]
           [:div {:class "mt-4 space-y-3"}
            (if (seq recent-users)
              (map-indexed
               (fn [idx {:keys [uuid name age]}]
                 (let [initial (initials name)
                       short (short-id uuid)
                       display-age (when age (str age " yrs"))]
                   ^{:key (or uuid idx)}
                   [:div {:class "flex items-center justify-between gap-3 rounded-xl border border-base-200 bg-base-100/90 px-4 py-3"}
                    [:div {:class "flex items-center gap-3"}
                     [:div {:class "flex h-10 w-10 items-center justify-center rounded-full bg-primary/10 text-base font-semibold text-primary"}
                      initial]
                     [:div
                      [:p {:class "font-semibold text-base-content"}
                       (or name "Unnamed user")]
                      (when short
                        [:p {:class "text-xs font-mono text-base-content/60"} short])]]
                    [:span {:class "text-sm font-medium text-base-content/70"}
                     (or display-age "-")]]))
               recent-users)
              [:div {:class "rounded-2xl border border-dashed border-base-200 bg-base-100/60 px-4 py-6 text-center text-sm text-base-content/60"}
               "No users available yet."])]]
          [:section {:class "rounded-xl border border-base-200 bg-base-100/95 p-6 shadow-sm"}
           [:div {:class "flex items-center justify-between gap-3"}
            [:div
             [:h3 {:class "text-lg font-semibold text-base-content"} "Todo Highlights"]
             [:p {:class "text-sm text-base-content/70"}
              (if (seq recent-todos)
                "A quick look at what's on deck"
                "Create a todo to start tracking work")]]
            [:button {:type "button"
                      :class "btn btn-sm btn-outline"
                      :on-click on-view-todos}
             "View all"]]
           [:div {:class "mt-4 space-y-3"}
            (if (seq recent-todos)
              (map-indexed
               (fn [idx {:keys [id title completed updated_at]}]
                 (let [short (short-id id)
                       status-label (if completed "Completed" "Pending")
                       status-class (if completed "badge badge-success" "badge badge-warning")
                       subtitle (or updated_at "Not updated yet")]
                   ^{:key (or id idx)}
                   [:div {:class "flex items-center justify-between gap-3 rounded-2xl border border-base-200 bg-base-100/90 px-4 py-3"}
                    [:div
                     [:p {:class "font-semibold text-base-content"}
                      (or title "Untitled todo")]
                     [:p {:class "text-xs text-base-content/60"}
                      (if short
                        (str "ID " short " • Updated " subtitle)
                        (str "Updated " subtitle))]]
                    [:span {:class status-class} status-label]]))
               recent-todos)
              [:div {:class "rounded-2xl border border-dashed border-base-200 bg-base-100/60 px-4 py-6 text-center text-sm text-base-content/60"}
               "No todos available yet."])]]]]))))

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

(defn demo-panel []
  [:div {:class "space-y-6"}
   [:div
    [:h2 {:class "text-2xl font-semibold text-base-content"} "Component Gallery"]
    [:p {:class "text-sm text-base-content/70"}
     "Reference implementations of our table layouts and DaisyUI widgets."]]
   [users-tables-tabs]])

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
              [:nav {:class "flex-1 space-y-3"}
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
