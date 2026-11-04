(ns ttg.sheet.subscriptions
  (:require [re-frame.core :as rf]
            [ttg.sheet.db :as db]))

(rf/reg-sub ::sheet (fn [db _] (:sheet db)))
(rf/reg-sub ::character :<- [::sheet] (fn [s _] (:character s)))

(rf/reg-sub ::access (fn [db _] (db/access db)))

(rf/reg-sub ::read-only? :<- [::access] (fn [access _] (not (#{"owner" "gm"} access))))
(rf/reg-sub ::gm? :<- [::access] (fn [access _] (= "gm" access)))

(rf/reg-sub ::save-state
  :<- [::sheet]
  (fn [{:keys [saving? dirty sent error conflict?]} _]
    (cond conflict? :conflict
          error :error
          (or saving? (> dirty sent)) :saving
          :else :saved)))
