(ns acme.web.views
  (:require
   [clojure.string :as str]
   [reagent.core :as r]
   [re-frame.core :as rf]
   [acme.web.events :as events]
   [acme.web.subs :as subs]
   [acme.web.components :as components]
   ["@rc-component/table" :default rc-table]))

(defn users-table
  ([] (users-table {}))
  ([opts]
   (let [{:keys [title include-aux? wrap?]
          :or {title "Users"
               include-aux? true
               wrap? true}} opts
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
                             [:div {:class "rounded-2xl border border-base-200 bg-base-100 shadow-sm"}
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
                                     [components/user-row-actions uuid]]])]]]])
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
                        include-aux? (conj [components/toast-banner])
                        include-aux? (conj [components/add-user-dialog])
                        include-aux? (conj [components/edit-user-dialog])
                        title (conj [:h1 {:class "text-2xl font-semibold"} title])
                        true (conj table-section)
                        true (conj actions))]
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
                                               [components/user-row-actions uuid])]))}])
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
                            [:div {:class "rounded-2xl border border-base-200 bg-base-100 shadow-sm"}
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
                       include-aux? (conj [components/toast-banner])
                       include-aux? (conj [components/add-user-dialog])
                       include-aux? (conj [components/edit-user-dialog])
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
  [:form {:class "grid gap-4 sm:grid-cols-2"}
   [:div {:class "form-control"}
    [:label {:class "label"}
     [:span {:class "label-text"} "Email"]]
    [:input {:type "email"
             :placeholder "person@example.com"
             :class "input input-bordered"}]]
   [:div {:class "form-control"}
    [:label {:class "label"}
     [:span {:class "label-text"} "Role"]]
    [:select {:class "select select-bordered"}
     [:option "Viewer"]
     [:option "Editor"]
     [:option "Admin"]]]
   [:div {:class "form-control sm:col-span-2"}
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
       [components/toast-banner]
       [components/add-user-dialog]
       [components/edit-user-dialog]
       [:div {:class "tabs tabs-boxed flex gap-3"}
        (for [{:keys [id label]} tabs]
          ^{:key (name id)}
          (let [active? (= id active)
                tab-classes (->> ["tab text-sm font-semibold px-5 py-2 transition-colors duration-150"
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

(defn main-panel []
  [users-tables-tabs])
