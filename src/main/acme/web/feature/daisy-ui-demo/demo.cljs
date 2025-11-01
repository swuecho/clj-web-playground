(ns acme.web.feature.daisy-ui-demo.demo)



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
