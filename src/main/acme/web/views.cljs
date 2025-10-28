(ns acme.web.views
  (:require
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
         container-style {:max-width "960px"
                          :margin "0 auto"
                          :padding "1.5rem"}
         table-style {:width "100%"
                      :border-collapse "separate"
                      :border-spacing 0
                      :table-layout "auto"}
         header-style {:text-align "left"
                       :border-bottom "1px solid #d1d5db"
                       :padding "0.75rem 0.5rem"
                       :font-weight 600
                       :white-space "nowrap"}
         cell-style {:border-bottom "1px solid #e5e7eb"
                     :padding "0.75rem 0.5rem"
                     :vertical-align "middle"
                     :line-height 1.4}
         uuid-cell-style {:font-family "monospace"
                          :word-break "break-all"}
         action-cell-style {:padding-right 0}
         empty-state-style {:margin "2rem 0"
                            :color "#64748b"}]
     (fn []
       (let [table-section (cond
                             @loading? [:p "Loading users..."]
                             @error [:p {:style {:color "#b91c1c"}} @error]
                             (empty? @users) [:p {:style empty-state-style} "No users found."]
                             :else
                             [:table {:style table-style}
                              [:thead
                               [:tr
                                [:th {:style header-style} "UUID"]
                                [:th {:style header-style} "Name"]
                                [:th {:style header-style} "Age"]
                                [:th {:style (merge header-style {:text-align "right"})} "Actions"]]]
                              [:tbody
                               (for [{:keys [uuid name age]} @users]
                                 ^{:key uuid}
                                 [:tr
                                  [:td {:style (merge cell-style uuid-cell-style)} uuid]
                                  [:td {:style cell-style} name]
                                  [:td {:style cell-style} age]
                                  [:td {:style (merge cell-style action-cell-style {:text-align "right"})}
                                   [:div {:style {:display "flex"
                                                  :justify-content "flex-end"
                                                  :flex-wrap "wrap"
                                                  :gap "0.5rem"}}
                                    [components/user-row-actions uuid]]]])]])
             actions [:div {:style {:margin-top "1.5rem"}}
                      [:button {:on-click #(rf/dispatch [::events/fetch-users])
                                :style {:background "#2563eb"
                                        :color "white"
                                        :border "none"
                                        :padding "0.5rem 1rem"
                                        :border-radius "0.375rem"
                                        :cursor "pointer"}}
                       "Reload"]
                      [:button {:on-click #(rf/dispatch [::events/open-add-user-dialog])
                                :style {:margin-left "0.75rem"
                                        :background "#16a34a"
                                        :color "white"
                                        :border "none"
                                        :padding "0.5rem 1rem"
                                        :border-radius "0.375rem"
                                        :cursor "pointer"}}
                       "Add User"]]
             content (cond-> []
                        include-aux? (conj [components/toast-banner])
                        include-aux? (conj [components/add-user-dialog])
                        title (conj [:h1 {:style {:margin-bottom "1rem"}} title])
                        true (conj table-section)
                        true (conj actions))
             wrapped (into (if wrap?
                              [:div {:style container-style}]
                              [:<>])
                            content)]
         wrapped)))))

(defn users-table-rc
  ([] (users-table-rc {}))
  ([{:keys [title include-aux? wrap?]
     :or {title "Users (rc-table)"
          include-aux? true
          wrap? true}}]
   (let [users (rf/subscribe [::subs/users])
         loading? (rf/subscribe [::subs/loading?])
         error (rf/subscribe [::subs/error])
         container-style {:max-width "960px"
                          :margin "0 auto"
                          :padding "1.5rem"}
         table-style {:width "100%"
                      :box-shadow "0 1px 2px rgba(15, 23, 42, 0.08)"
                      :border-radius "0.75rem"
                      :overflow "hidden"
                      :border "1px solid #e5e7eb"}
         uuid-cell-style {:font-family "monospace"
                          :word-break "break-all"
                          :line-height 1.4}
         header-style {:background "#f8fafc"
                       :padding "0.75rem 0.5rem"
                       :font-weight 600
                       :border-bottom "1px solid #e2e8f0"
                       :text-align "left"}
         cell-style {:padding "0.75rem 0.5rem"
                     :border-bottom "1px solid #e5e7eb"
                     :vertical-align "middle"}
         header-style-js (clj->js header-style)
         cell-style-js (clj->js cell-style)
         header-style-right-js (clj->js (assoc header-style :text-align "right"))
         cell-style-right-js (clj->js (assoc cell-style :text-align "right"))
         actions-container-style {:display "flex"
                                  :justify-content "flex-end"
                                  :flex-wrap "wrap"
                                  :gap "0.5rem"}
         columns (clj->js
                  [{:title "UUID"
                    :dataIndex "uuid"
                    :key "uuid"
                    :align "left"
                    :onHeaderCell (fn [_]
                                    #js {:style header-style-js})
                    :onCell (fn [_]
                              #js {:style cell-style-js})
                    :render (fn [uuid _record _index]
                              (r/as-element [:span {:style uuid-cell-style}
                                             (or uuid "-")]))}
                   {:title "Name"
                    :dataIndex "name"
                    :key "name"
                    :align "left"
                    :onHeaderCell (fn [_]
                                    #js {:style header-style-js})
                    :onCell (fn [_]
                              #js {:style cell-style-js})
                    :render (fn [name _record _index]
                              (r/as-element [:span {:style {:line-height 1.4}}
                                             (or name "-")]))}
                   {:title "Age"
                    :dataIndex "age"
                    :key "age"
                    :width 80
                    :align "left"
                    :onHeaderCell (fn [_]
                                    #js {:style header-style-js})
                    :onCell (fn [_]
                              #js {:style cell-style-js})
                    :render (fn [age _record _index]
                              (r/as-element [:span {:style {:line-height 1.4}}
                                             (if (some? age) age "-")]))}
                   {:title "Actions"
                    :dataIndex "uuid"
                    :key "actions"
                    :align "right"
                    :onHeaderCell (fn [_]
                                    #js {:style header-style-right-js})
                    :onCell (fn [_]
                              #js {:style cell-style-right-js})
                    :render (fn [uuid _record _index]
                              (r/as-element [:div {:style actions-container-style}
                                             (when uuid
                                               [components/user-row-actions uuid])]))}])
         empty-state-style {:margin "2rem 0"
                            :color "#64748b"}]
     (fn []
       (let [table-data (->> @users
                             (mapv (fn [user]
                                     (-> user
                                         (assoc :key (:uuid user)))))
                             (clj->js))
             table-section (cond
                             @loading? [:p "Loading users..."]
                             @error [:p {:style {:color "#b91c1c"}} @error]
                             (empty? @users) [:p {:style empty-state-style} "No users found."]
                             :else
                             [:> rc-table {:columns columns
                                           :data table-data
                                           :rowKey "uuid"
                                           :style table-style
                                           :tableLayout "auto"
                                           :scroll #js {:x "max-content"}}])
             actions [:div {:style {:margin-top "1.5rem"}}
                      [:button {:on-click #(rf/dispatch [::events/fetch-users])
                                :style {:background "#2563eb"
                                        :color "white"
                                        :border "none"
                                        :padding "0.5rem 1rem"
                                        :border-radius "0.375rem"
                                        :cursor "pointer"}}
                       "Reload"]
                      [:button {:on-click #(rf/dispatch [::events/open-add-user-dialog])
                                :style {:margin-left "0.75rem"
                                        :background "#16a34a"
                                        :color "white"
                                        :border "none"
                                        :padding "0.5rem 1rem"
                                        :border-radius "0.375rem"
                                        :cursor "pointer"}}
                       "Add User"]]
             content (cond-> []
                        include-aux? (conj [components/toast-banner])
                        include-aux? (conj [components/add-user-dialog])
                        title (conj [:h1 {:style {:margin-bottom "1rem"}} title])
                        true (conj table-section)
                        true (conj actions))]
         (if wrap?
           (into [:div {:style container-style}] content)
           (into [:<>] content)))))))

(defn users-tables-tabs []
  (r/with-let [active-tab (r/atom :standard)]
    (let [container-style {:max-width "960px"
                           :margin "0 auto"
                           :padding "1.5rem"}
          tab-bar-style {:display "flex"
                         :gap "0.75rem"
                         :border-bottom "1px solid #e5e7eb"
                         :padding-bottom "0.5rem"}
          tab-base-style {:background "transparent"
                          :border "none"
                          :border-bottom "2px solid transparent"
                          :padding "0.5rem 0.75rem"
                          :font-size "0.95rem"
                          :font-weight 600
                          :cursor "pointer"
                          :color "#64748b"
                          :outline "none"}
          tab-active-style {:color "#1d4ed8"
                             :border-bottom "2px solid #2563eb"}
          tab-inactive-style {:color "#64748b"}
          tabs [{:id :standard :label "Standard Table"}
                 {:id :rc :label "RC Table"}]
          active @active-tab]
      [:div {:style container-style}
       [components/toast-banner]
       [components/add-user-dialog]
       [:h1 {:style {:margin-bottom "1rem"}} "Users"]
       [:div {:style tab-bar-style}
        (for [{:keys [id label]} tabs]
          ^{:key (name id)}
          [:button {:type "button"
                    :on-click #(reset! active-tab id)
                    :style (merge tab-base-style (if (= id active)
                                                   tab-active-style
                                                   tab-inactive-style))}
           label])]
       [:div {:style {:margin-top "1.5rem"}}
        (case active
          :rc [users-table-rc {:wrap? false
                               :include-aux? false
                               :title nil}]
          [users-table {:wrap? false
                        :include-aux? false
                        :title nil}])]])))

(defn main-panel []
  [users-tables-tabs])
