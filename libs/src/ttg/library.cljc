(ns ttg.library
  "A GM's private content library: {type {id entry}}. Validation, new entries and import planning,
   shared by the editor (browser) and the library API (Lambda), so both reject the same things."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [ttg.genesys.model :as model]
            [ttg.library.fields :as fields]
            [ttg.library.io :as io]))

(def types (set (keys model/entry-specs)))

(s/def ::type types)
(s/def ::entry (s/and map? #(keyword? (:id %))))

(defn- missing-key
  "The key a failed contains? predicate was looking for, from the spec's pred form."
  [pred]
  (when (seq? pred) (some #(when (keyword? %) %) (flatten pred))))

(defn- problem->field [type {:keys [in pred]}]
  (cond
    (seq in) [[(first in)] "Not a valid value"]
    (and (= :weapons type) (some #{:damage-bonus} (flatten pred))) [[:damage] "Needs damage or a damage bonus"]
    (missing-key pred) [[(missing-key pred)] "Required"]
    :else [[] (str "Doesn't fit the " (name type) " shape")]))

(defn problems
  "nil when entry is a valid type entry, otherwise {path message} for the form to show beside each field."
  [type entry]
  (if-let [spec (model/entry-specs type)]
    (when-let [ps (::s/problems (s/explain-data spec entry))]
      (into {} (map #(problem->field type %)) ps))
    {[] (str "Unknown library type " type)}))

(defn valid? [type entry] (nil? (problems type entry)))

(defn new-entry
  "A blank entry of type, named so the GM sees it in the list straight away."
  [type entry-name]
  (merge (fields/defaults type {}) {:id (io/slug entry-name) :name entry-name :source {:book "custom"}}))

(defn unique-id
  "id, or id-2, id-3, ... so a new custom entry never overwrites one already in the library."
  [taken id]
  (if-not (taken id)
    id
    (first (remove taken (map #(keyword (str (name id) "-" %)) (iterate inc 2))))))

(defn clean
  "What gets stored: no merge history (:sources), no server stamps."
  [entry]
  (dissoc entry :sources :updated-at))

(defn plan-import
  "Compares incoming {type [entry]} with the library {type {id entry}}.
   Returns rows {:type :id :name :status (:add :replace :same :invalid) :problems} in file order."
  [library incoming]
  (vec (for [[type entries] incoming, e entries
             :let [e (clean e) ps (problems type e) prior (get-in library [type (:id e)])]]
         {:type type :id (:id e) :name (:name e) :entry e :problems ps
          :status (cond ps :invalid
                        (nil? prior) :add
                        (= e (clean prior)) :same
                        :else :replace)})))

(defn import-summary [rows] (frequencies (map :status rows)))

(def ^:private book-names {"crb" "CRB" "epg" "EPG" "sotb" "SotB" "sotb-guns" "Gun Locker" "woa" "WoA"})

(defn cite
  "\"SotB p.91\", \"House rules\" or \"Custom\"."
  [{:keys [book page]}]
  (cond (= "custom" book) "Custom"
        (= "house" book) "House rules"
        page (str (book-names book (str/upper-case (str book))) " p." page)
        :else (str book)))
