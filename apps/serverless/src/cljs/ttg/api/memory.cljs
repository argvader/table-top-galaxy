(ns ttg.api.memory
  "An in-memory Table for tests: {[pk sk] item} in an atom, with the same prefix and index semantics."
  (:require [cljs.core.async :refer [go]]
            [clojure.string :as str]
            [ttg.api.table :as table]))

(defn- sorted-by [k items] (vec (sort-by k items)))

(defrecord MemoryTable [state]
  table/Table
  (get-item [_ pk sk] (go (get @state [pk sk])))
  (query [_ pk prefix]
    (go (sorted-by :sk (filter #(and (= pk (:pk %)) (str/starts-with? (:sk %) prefix)) (vals @state)))))
  (query-index [_ gpk prefix]
    (go (sorted-by :gsi1sk (filter #(and (= gpk (:gsi1pk %)) (str/starts-with? (str (:gsi1sk %)) prefix)) (vals @state)))))
  (put-item! [_ item {:keys [new?]}]
    (go (if (and new? (contains? @state [(:pk item) (:sk item)]))
          (ex-info "That already exists" {:status 409})
          (do (swap! state assoc [(:pk item) (:sk item)] item) true))))
  (put-items! [_ items]
    (go (swap! state into (map (fn [i] [[(:pk i) (:sk i)] i])) items) (count items)))
  (delete-items! [_ ks]
    (go (swap! state #(apply dissoc % (map (juxt :pk :sk) ks))) (count ks))))

(defn memory-table [] (->MemoryTable (atom {})))
