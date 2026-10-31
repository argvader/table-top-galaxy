(ns ttg.library.subscriptions
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [ttg.library.events :as ev]
            [ttg.library.fields :as fields]))

(rf/reg-sub ::library (fn [db _] (:library db)))
(rf/reg-sub ::type (fn [db _] (ev/current-type db)))
(rf/reg-sub ::status :<- [::library] (fn [lib _] (select-keys lib [:status :error])))
(rf/reg-sub ::entries :<- [::library] (fn [lib _] (:entries lib)))
(rf/reg-sub ::query :<- [::library] (fn [lib _] (:query lib)))
(rf/reg-sub ::draft :<- [::library] (fn [lib _] (:draft lib)))
(rf/reg-sub ::import :<- [::library] (fn [lib _] (:import lib)))
(rf/reg-sub ::toast :<- [::library] (fn [lib _] (:toast lib)))

(rf/reg-sub ::counts :<- [::entries]
  (fn [entries _] (into {} (map (fn [{:keys [type]}] [type (count (get entries type))])) fields/editable-types)))

(rf/reg-sub ::other-count :<- [::entries]
  (fn [entries _] (reduce + (map count (vals (apply dissoc entries (keys fields/type-info)))))))

(defn- matches? [q entry]
  (or (str/blank? q) (str/includes? (str/lower-case (:name entry "")) (str/lower-case (str/trim q)))))

(rf/reg-sub ::visible :<- [::entries] :<- [::type] :<- [::query]
  (fn [[entries type q] _]
    (->> (vals (get entries type)) (filter #(matches? q %)) (sort-by (comp str/lower-case :name)) vec)))

;; Career skill suggestions come from the library's own skill list.
(rf/reg-sub ::skill-names :<- [::entries]
  (fn [entries _] (sort (map :name (vals (:skills entries))))))
