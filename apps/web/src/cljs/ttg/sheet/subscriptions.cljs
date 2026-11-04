(ns ttg.sheet.subscriptions
  (:require [re-frame.core :as rf]
            [ttg.config :as config]))

(rf/reg-sub ::sheet (fn [db _] (:sheet db)))
(rf/reg-sub ::character :<- [::sheet] (fn [s _] (:character s)))

;; The server says who's asking: the owner, or the GM of the character's campaign, who can fix or override it.
;; Without :access (an older API) only the owner writes.
(rf/reg-sub ::access
  (fn [db _]
    (let [me (or (get-in db [:session :id]) config/dev-owner)
          {:keys [owner access]} (get-in db [:sheet :character])]
      (or access (when (= me owner) "owner")))))

(rf/reg-sub ::read-only? :<- [::access] (fn [access _] (not (#{"owner" "gm"} access))))
(rf/reg-sub ::gm? :<- [::access] (fn [access _] (= "gm" access)))

(rf/reg-sub ::save-state
  :<- [::sheet]
  (fn [{:keys [saving? dirty sent error conflict?]} _]
    (cond conflict? :conflict
          error :error
          (or saving? (> dirty sent)) :saving
          :else :saved)))
