(ns ttg.roller.subscriptions
  (:require [re-frame.core :as rf]
            [ttg.dice.odds :as odds]
            [ttg.roller.spend :as spend]))

(rf/reg-sub ::pool (fn [db _] (:pool db)))
(rf/reg-sub ::ladder (fn [db _] (:ladder db)))
(rf/reg-sub ::reset-note? (fn [db _] (:reset-note? db)))
(rf/reg-sub ::rolling (fn [db _] (:rolling db)))

;; The log leaves out a roll until its dice have landed, so it can't give the result away early.
(rf/reg-sub ::rolls (fn [db _] (vec (remove #(= (:id %) (:rolling db)) (:rolls db)))))

(rf/reg-sub ::pool-size :<- [::pool] (fn [pool _] (reduce + (vals pool))))

;; The exact chance-of-success preview; recomputed only when the pool changes.
(rf/reg-sub ::odds :<- [::pool] (fn [pool _] (odds/odds pool)))

(rf/reg-sub ::shown
  (fn [db _] (some #(when (= (:id %) (:shown db)) %) (:rolls db))))

(rf/reg-sub ::remaining :<- [::shown]
  (fn [roll [_ side]]
    (when roll (spend/remaining (:result roll) (get-in roll [:spent side]) side))))
