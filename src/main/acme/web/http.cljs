(ns acme.web.http)

(defn authorized-headers [db]
  (let [token (get-in db [:auth :token])]
    (cond-> {"Accept" "application/json"}
      token (assoc "Authorization" (str "Bearer " token)))))
