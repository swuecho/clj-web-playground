(ns acme.web.components.base.metric-card)

(defn metric-card [{:keys [title value subtext badge badge-variant]}]
  [:div {:class "rounded-xl border border-base-200 bg-base-100/95 p-5 shadow-sm"}
   [:div {:class "flex items-center justify-between gap-3"}
    [:p {:class "text-xs font-semibold uppercase tracking-wide text-base-content/60"} title]
    (when badge
      [:span {:class (str "badge badge-sm " (or badge-variant "badge-ghost"))} badge])]
   [:p {:class "mt-3 text-3xl font-semibold text-base-content"} value]
   (when subtext
     [:p {:class "mt-3 text-sm text-base-content/70"} subtext])])