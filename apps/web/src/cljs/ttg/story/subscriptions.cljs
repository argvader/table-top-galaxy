(ns ttg.story.subscriptions
  (:require [re-frame.core :as rf]))

(rf/reg-sub ::story (fn [db _] (:story db)))
(rf/reg-sub ::pool :<- [::story] (fn [s _] (:pool s)))

(rf/reg-sub ::player-points
  :<- [::pool]
  (fn [pool _] (:player pool 0)))

(rf/reg-sub ::can-spend?
  :<- [::story]
  (fn [{:keys [pool busy?]} _] (and pool (not busy?) (pos? (:player pool)))))
