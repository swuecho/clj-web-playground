(ns acme.web.feature.users.components.user-sessions-dialog
  (:require
   [acme.web.feature.users.events :as users-events]
   [acme.web.feature.users.subs :as users-subs]
   [re-frame.core :as rf]))

(defn- status-label [{:keys [revoked?]}]
  (if revoked?
    [:span {:class "badge badge-error badge-outline"} "Revoked"]
    [:span {:class "badge badge-success badge-outline"} "Active"]))

(defn- token-row [{:keys [id created_at last_used_at expires_at revoked?] :as token}]
  (let [revoking? (rf/subscribe [::users-subs/revoking-token? id])]
    (fn [{:keys [id created_at last_used_at expires_at revoked?]}]
      [:tr
       [:td {:class "font-mono text-xs"} (or id "—")]
       [:td {:class "text-sm"} (or created_at "—")]
       [:td {:class "text-sm"} (or last_used_at "—")]
       [:td {:class "text-sm"} (or expires_at "—")]
       [:td [:div {:class "flex justify-center"}
             [status-label {:revoked? revoked?}]]]
       [:td {:class "text-right"}
        [:button {:type "button"
                  :class "btn btn-xs"
                  :disabled (or revoked? @revoking?)
                  :on-click #(rf/dispatch [::users-events/revoke-refresh-token id])}
         (cond
           revoked? "Revoked"
           @revoking? "Revoking"
           :else "Revoke")]]])))

(defn user-sessions-dialog []
  (let [visible? (rf/subscribe [::users-subs/user-sessions-visible?])
        user (rf/subscribe [::users-subs/user-sessions-user])
        tokens (rf/subscribe [::users-subs/user-sessions-tokens])
        loading? (rf/subscribe [::users-subs/user-sessions-loading?])
        error (rf/subscribe [::users-subs/user-sessions-error])]
    (fn []
      (when @visible?
        [:div {:class "modal modal-open"}
         [:div {:class "modal-box max-w-4xl space-y-5"}
          [:div {:class "flex items-start justify-between"}
           [:div
            [:h2 {:class "text-xl font-semibold"} "Active Sessions"]
            (when-let [u @user]
              [:p {:class "text-sm text-base-content/70"}
               (str (:name u) " • " (:email u))])]
           [:button {:type "button"
                     :class "btn btn-sm btn-ghost"
                     :on-click #(rf/dispatch [::users-events/close-user-sessions])}
            "✕"]]
          (cond
            @loading? [:div {:class "alert alert-info"}
                       [:span "Loading refresh tokens..."]]
            @error [:div {:class "alert alert-error"}
                    [:span @error]]
            (empty? @tokens) [:div {:class "alert alert-warning"}
                              [:span "No refresh tokens found for this user."]]
            :else
            [:div {:class "overflow-x-auto"}
             [:table {:class "table table-zebra"}
              [:thead
               [:tr {:class "text-sm uppercase tracking-wide text-base-content/70"}
                [:th "Token"]
                [:th "Created"]
                [:th "Last Used"]
                [:th "Expires"]
                [:th {:class "text-center"} "Status"]
                [:th {:class "text-right"} "Actions"]]]
              [:tbody
               (for [token @tokens]
                 ^{:key (:id token)}
                 [token-row token])]]])
          [:div {:class "modal-action"}
           [:button {:type "button"
                     :class "btn"
                     :on-click #(rf/dispatch [::users-events/close-user-sessions])}
            "Close"]]]
         [:div {:class "modal-backdrop"}
          [:button {:type "button"
                    :class "btn"
                    :on-click #(rf/dispatch [::users-events/close-user-sessions])}
           "Close"]]]))))
