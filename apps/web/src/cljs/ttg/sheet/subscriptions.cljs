(ns ttg.sheet.subscriptions
  (:require [re-frame.core :as rf]
            [ttg.config :as config]))

(rf/reg-sub ::sheet (fn [db _] (:sheet db)))
(rf/reg-sub ::character :<- [::sheet] (fn [s _] (:character s)))

;; A GM reads their players' characters; only the owner writes.
(rf/reg-sub ::read-only?
  (fn [db _]
    (let [me (or (get-in db [:session :id]) config/dev-owner)]
      (not= me (get-in db [:sheet :character :owner])))))

(rf/reg-sub ::save-state
  :<- [::sheet]
  (fn [{:keys [saving? dirty sent error conflict?]} _]
    (cond conflict? :conflict
          error :error
          (or saving? (> dirty sent)) :saving
          :else :saved)))
