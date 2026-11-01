(ns ttg.chronicle.subscriptions
  (:require [re-frame.core :as rf]))

(rf/reg-sub ::chronicle (fn [db _] (:chronicle db)))
