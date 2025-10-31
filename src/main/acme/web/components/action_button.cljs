(ns acme.web.components.action-button)

(defn action-button
  [{:keys [label variant on-click disabled?]}]
  (let [variant-class (case variant
                        :primary "btn-primary"
                        :success "btn-success"
                        :danger "btn-error"
                        :ghost "btn-ghost"
                        :info "btn-info"
                        :warning "btn-warning"
                        "btn-neutral")]
    [:button {:type "button"
              :on-click on-click
              :disabled disabled?
              :class (str "btn btn-sm " variant-class)}
     label]))
