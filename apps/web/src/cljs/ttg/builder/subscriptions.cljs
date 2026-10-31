(ns ttg.builder.subscriptions
  (:require [re-frame.core :as rf]
            [ttg.content :as content]
            [ttg.genesys.rules :as rules]))

(rf/reg-sub ::builder (fn [db _] (:builder db)))
(rf/reg-sub ::character :<- [::builder] (fn [b _] (:character b)))
(rf/reg-sub ::issues :<- [::character] :<- [::content/ix]
  (fn [[c ix] _] (when ix (rules/issues c ix))))
