(ns ttg.campaign.subscriptions
  (:require [re-frame.core :as rf]))

(defn runs-a-campaign? [campaigns] (boolean (some #(= "gm" (:role %)) campaigns)))

(rf/reg-sub ::table (fn [db _] (:table db)))
(rf/reg-sub ::gm? :<- [::table] (fn [table _] (runs-a-campaign? (:campaigns table))))
