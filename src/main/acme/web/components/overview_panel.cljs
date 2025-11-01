(ns acme.web.components.overview-panel
  (:require
   [acme.web.components.base.metric-card :refer [metric-card]]
   [acme.web.subs :as subs]
   [clojure.string :as str]
   [re-frame.core :as rf]))

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