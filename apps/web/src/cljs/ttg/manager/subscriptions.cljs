(ns ttg.manager.subscriptions
  (:require [re-frame.core :as rf]
            [ttg.manager.party :as party]))

(rf/reg-sub ::manager (fn [db _] (:manager db)))
(rf/reg-sub ::doc :<- [::manager] (fn [m _] (:doc m)))
(rf/reg-sub ::content :<- [::manager] (fn [m _] (:content m)))
(rf/reg-sub ::ui :<- [::manager] (fn [m _] (:ui m)))
(rf/reg-sub ::at :<- [::doc] (fn [doc [_ path]] (get-in doc path)))

(rf/reg-sub ::party :<- [::manager]
  (fn [{:keys [members doc content]} _] (party/party members (:corps content) (:seats doc))))

(rf/reg-sub ::save-state :<- [::manager]
  (fn [{:keys [dirty sent problem]} _]
    (cond problem problem (> dirty sent) :saving :else :saved)))
