(ns acme.web.components.todo-table.table
  (:require
   [reagent.core :as r]
   [acme.web.components.todo-row-actions :refer [todo-row-actions]]))

(def header-base-class
  "bg-base-200/85 text-[11px] font-semibold uppercase tracking-wide text-base-content/70 px-4 py-3 border-b border-base-200/80 first:rounded-tl-3xl last:rounded-tr-3xl")

(def cell-base-class "px-4 py-3 align-middle text-sm text-base-content/90 border-b border-base-200")

(defn status-pill [completed?]
  (if (boolean completed?)
    [:span {:class "inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700"}
     [:span {:class "h-2 w-2 rounded-full bg-emerald-500"}]
     "Completed"]
    [:span {:class "inline-flex items-center gap-1 rounded-full border border-amber-200 bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-600"}
     [:span {:class "h-2 w-2 rounded-full bg-amber-400"}]
     "Pending"]))

(defn- sortable-title [{:keys [field direction dispatch-sort]} label target-field]
  (let [active? (= target-field field)
        next-direction (if active?
                         (if (= direction :asc) :desc :asc)
                         :asc)
        indicator (when active?
                    (if (= direction :asc) "↑" "↓"))]
    (r/as-element
     [:button {:type "button"
               :class (str "group flex items-center gap-2 text-[11px] font-semibold uppercase tracking-wide transition-colors "
                           (if active?
                             "text-secondary"
                             "text-base-content/70 hover:text-secondary"))
               :on-click #(dispatch-sort target-field next-direction)}
      [:span label]
      (when indicator
        [:span {:aria-hidden "true"
                :class "text-sm leading-none"}
         indicator])
      [:span {:class "sr-only"}
       (if (= next-direction :asc)
         (str "Sort by " label " ascending")
         (str "Sort by " label " descending"))]])))

(defn- header-props [additional-class]
  (clj->js {:className (str header-base-class " " additional-class)}))

(defn build-columns [{:keys [field direction dispatch-sort]}]
  (let [config {:field field
                :direction direction
                :dispatch-sort dispatch-sort}
        cell-class #(str cell-base-class " " %)]
    (clj->js
     [{:title (r/as-element
               [:div {:class "flex items-center"}
                [:span {:class "text-[11px] font-semibold uppercase tracking-wide text-base-content/70"}
                 "ID"]])
       :dataIndex "id"
       :key "id"
       :align "left"
       :width 90
       :className (cell-class "font-medium text-base-content/80 font-mono text-xs")
       :onHeaderCell (fn [_ _] (header-props "text-left"))
       :render (fn [id _record _index]
                 (r/as-element [:span {:class "font-mono text-xs text-base-content/80"} id]))}
      {:title (r/as-element
               [:div {:class "flex items-center"}
                [:span {:class "text-[11px] font-semibold uppercase tracking-wide text-base-content/70"}
                 "Title"]])
       :dataIndex "title"
       :key "title"
       :align "left"
       :className (cell-class "text-base-content")
       :onHeaderCell (fn [_ _] (header-props "text-left"))
       :render (fn [title _record _index]
                 (r/as-element [:span {:class "text-base-content"} (or title "-")]))}
      {:title (sortable-title config "Completed" :completed)
       :dataIndex "completed"
       :key "completed"
       :align "center"
       :width 150
       :className (cell-class "text-center")
       :onHeaderCell (fn [_ _] (header-props "text-center"))
       :render (fn [completed _record _index]
                 (r/as-element [status-pill completed]))}
      {:title (sortable-title config "Created" :created_at)
       :dataIndex "created_at"
       :key "created_at"
       :align "left"
       :width 210
       :className (cell-class "font-mono text-xs text-base-content/80")
       :onHeaderCell (fn [_ _] (header-props "text-left"))
       :render (fn [created-at _record _index]
                 (r/as-element [:span {:class "font-mono text-xs text-base-content/80"}
                                (or created-at "-")]))}
      {:title (sortable-title config "Updated" :updated_at)
       :dataIndex "updated_at"
       :key "updated_at"
       :align "left"
       :width 210
       :className (cell-class "font-mono text-xs text-base-content/80")
       :onHeaderCell (fn [_ _] (header-props "text-left"))
       :render (fn [updated-at _record _index]
                 (r/as-element [:span {:class "font-mono text-xs text-base-content/80"}
                                (or updated-at "-")]))}
      {:title (r/as-element
               [:div {:class "flex justify-end"}
                [:span {:class "text-[11px] font-semibold uppercase tracking-wide text-base-content/70"}
                 "Actions"]])
       :key "actions"
       :align "right"
       :width 180
       :className (cell-class "text-right")
       :onHeaderCell (fn [_ _] (header-props "text-right"))
       :render (fn [_ record _index]
                 (let [todo (js->clj record :keywordize-keys true)]
                   (r/as-element [:div {:class "flex justify-end"}
                                   [todo-row-actions (:id todo)]])))}])))

(defn format-rows [todos]
  (->> todos
       (map (fn [todo]
              (-> todo
                  (assoc :key (str (:id todo)))
                  (update :completed boolean))))
       (clj->js)))
