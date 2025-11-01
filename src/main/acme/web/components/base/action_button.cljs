(ns acme.web.components.base.action-button)

(defn action-button
  [{:keys [label variant on-click disabled? aria-label title]}]
  (let [variant-class (case variant
                        :default "btn-default"
                        :primary "btn-primary"
                        :success "btn-success"
                        :danger "btn-error"
                        :ghost "btn-ghost"
                        :info "btn-info"
                        :warning "btn-warning"
                        "btn-neutral")]
    [:button (cond-> {:type "button"
                      :on-click on-click
                      :disabled disabled?
                      :class (str "btn btn-sm " variant-class)}
               aria-label (assoc :aria-label aria-label)
               title (assoc :title title))
     label]))
