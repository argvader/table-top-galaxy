(ns ttg.api.router
  "Matches a method and path against [method \"/path/:param\" op] routes."
  (:require [clojure.string :as str]))

(defn- parts [path] (vec (remove str/blank? (str/split (or path "") #"/"))))

(defn- match-parts [pattern actual]
  (when (= (count pattern) (count actual))
    (reduce (fn [params [p a]]
              (cond (str/starts-with? p ":") (assoc params (keyword (subs p 1)) a)
                    (= p a) params
                    :else (reduced nil)))
            {} (map vector pattern actual))))

(defn match
  "{:op op :params {...}} for the first route that fits, or nil."
  [routes method path]
  (let [actual (parts path)]
    (some (fn [[m pattern op]]
            (when (= m method)
              (when-let [params (match-parts (parts pattern) actual)]
                {:op op :params params})))
          routes)))
