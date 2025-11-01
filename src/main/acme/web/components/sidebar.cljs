(ns acme.web.components.sidebar
  (:require
   [acme.web.components.icons :as icons]
   [clojure.string :as str]
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

(defn- nav-button
  [{:keys [id label description icon icon-text accent disabled?]} active-id on-select collapsed?]
  (let [active? (= id active-id)
        base-classes (cond
                       disabled? "border-transparent text-base-content/40 cursor-not-allowed opacity-60"
                       active? "border-primary/40 bg-base-100 text-base-content shadow-sm"
                       :else "border-transparent text-base-content/70 hover:text-base-content hover:bg-base-200/60")
        layout-classes (if collapsed?
                         "flex flex-col items-center gap-2 rounded-xl px-2 py-3"
                         "flex items-start gap-3 rounded-xl px-3 py-3")
        icon-classes (if active?
                       "bg-primary text-primary-content shadow-sm"
                       (or accent "bg-base-200 text-base-content/70"))
        icon-label (or icon-text (some-> label (subs 0 1) str/upper-case))]
    [:button {:type "button"
              :class (str "group relative w-full border text-left text-sm font-semibold transition-all duration-200 "
                          layout-classes " " base-classes)
              :on-click #(when-not disabled? (on-select id))
              :title label
              :disabled disabled?}
     [:span {:class (str "flex h-10 w-10 items-center justify-center rounded-xl text-xs font-semibold tracking-wide transition-colors duration-150 "
                         icon-classes)}
      (or icon icon-label)]
     (when-not collapsed?
       [:span {:class "flex flex-col text-left"}
        [:span {:class (str "text-sm font-semibold transition-colors duration-150 "
                            (if active? "text-base-content" "text-base-content"))}
         label]
        (when (seq description)
          [:span {:class "text-xs font-medium text-base-content/60"}
           description])])]))

(defn- collapse-toggle-button [collapsed? toggle!]
  [:button {:type "button"
            :class "group relative mt-auto flex h-10 w-full items-center justify-between rounded-xl border border-base-200 bg-base-100 px-3 text-sm font-semibold text-base-content/70 transition-colors duration-150 hover:border-base-300 hover:text-base-content"
            :title (if collapsed? "Expand sidebar" "Collapse sidebar")
            :on-click toggle!}
   [:span {:class (str "flex h-9 w-9 items-center justify-center rounded-lg text-xs font-semibold "
                       (if collapsed?
                         "bg-base-200 text-base-content/70"
                         "bg-primary/10 text-primary"))}
    [:svg {:xmlns "http://www.w3.org/2000/svg"
           :viewBox "0 0 24 24"
           :fill "none"
           :stroke "currentColor"
           :stroke-width "2"
           :stroke-linecap "round"
           :stroke-linejoin "round"
           :class "h-4 w-4"}
     [:polyline {:points (if collapsed?
                          "10 18 16 12 10 6"
                          "14 6 8 12 14 18")}]]]
   (when-not collapsed?
     [:span {:class "text-sm font-semibold"} "Collapse"])])

(defn sidebar [{:keys [active-id on-select]}]
  (r/with-let [collapsed? (r/atom false)]
    (fn [{:keys [active-id on-select]}]
      (let [collapsed @collapsed?
            toggle! #(swap! collapsed? not)]
        [:aside {:class (str "w-full shrink-0 border-b border-base-200 bg-base-100/95 transition-all duration-200 ease-in-out "
                             (if collapsed
                               "md:w-24"
                               "md:w-72")
                             " md:border-b-0 md:border-r")}
         [:div {:class (str "flex h-full flex-col gap-8 px-4 py-8 "
                            (when collapsed "items-center px-3"))}
          [:div {:class (str "flex items-center gap-3 "
                              (when collapsed "justify-center"))}
           [:div {:class "flex h-10 w-10 items-center justify-center rounded-2xl bg-primary text-lg font-semibold text-primary-content"}
            "AC"]
           (when-not collapsed
             [:div
              [:p {:class "text-xs font-semibold uppercase tracking-wide text-base-content/60"} "Acme"]
              [:p {:class "text-lg font-semibold text-base-content"} "Control Center"]])]
          [:nav {:class (str "flex-1 space-y-1 "
                              (when collapsed "w-full"))}
           (for [item sidebar-nav-items]
             ^{:key (:id item)}
             [nav-button item active-id on-select collapsed])]
          (if collapsed
            [:div {:class "flex h-10 w-10 items-center justify-center rounded-xl border border-base-200 bg-base-100 text-base-content/60"
                   :title "Need help? Visit the docs directory for setup guides and API references."}
             "?"]
            [:div {:class "rounded-2xl border border-base-200 bg-base-100 px-4 py-3 text-sm text-base-content/60"}
             [:p {:class "font-semibold text-base-content"} "Need help?"]
             [:p {:class "text-sm"}
              "Visit the docs directory for setup guides and API references."]])
          [collapse-toggle-button collapsed toggle!]]]))))
