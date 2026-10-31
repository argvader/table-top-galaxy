(ns ttg.library.events-test
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [ttg.library.db :as db]
            [ttg.library.events :as ev]
            [ttg.library.io :as io]))

(def knife {:id :knife :name "Knife" :skill "Melee" :damage-bonus 1 :adds-brawn true :crit 3 :range "Engaged"
            :qualities [] :price 25 :rarity 1 :setting "android" :source {:book "sotb" :page 90}})

(defn- base-db [route]
  {:route route :library (assoc db/default-library :status :ready :entries {:weapons {:knife knife}})})

(defn- valid! [db] (is (s/valid? ::db/app-db db) (s/explain-str ::db/app-db db)) db)

(defn- fx-of [result id] (some #(when (= id (first %)) (second %)) (:fx result)))

(deftest the-editor-follows-the-route
  (testing "an entry"
    (let [d (get-in (valid! (ev/open-from-route (base-db {:page :library :type :weapons :id :knife}))) [:library :draft])]
      (is (= knife (:base d)))
      (is (= "Knife" (get-in d [:strings [:name]])))
      (is (false? (:new? d)))))
  (testing "a new entry"
    (let [d (get-in (valid! (ev/open-from-route (base-db {:page :library :type :weapons :id :new}))) [:library :draft])]
      (is (:new? d))
      (is (= "custom" (get-in d [:base :source :book])))))
  (testing "a list, or an entry that isn't there"
    (is (nil? (get-in (ev/open-from-route (base-db {:page :library :type :weapons})) [:library :draft])))
    (is (nil? (get-in (ev/open-from-route (base-db {:page :library :type :weapons :id :spork})) [:library :draft])))))

(defn- editing [route & edits]
  (reduce (fn [db [path v]] (ev/edit-field db [::ev/edit-field path v]))
          (ev/open-from-route (base-db route)) (partition 2 edits)))

(deftest a-bad-form-never-reaches-the-server
  (let [db (editing {:page :library :type :weapons :id :knife} [:crit] "lots" [:name] "")
        result (ev/save {:db db} [::ev/save])]
    (is (nil? (fx-of result :ttg.http/request)))
    (is (= #{[:crit] [:name]} (set (keys (get-in (valid! (:db result)) [:library :draft :errors])))))))

(deftest a-new-entry-gets-a-free-id
  (let [db (editing {:page :library :type :weapons :id :new} [:name] "Knife" [:skill] "Melee")
        req (fx-of (ev/save {:db db} [::ev/save]) :ttg.http/request)]
    (is (= "/library/weapons/knife-2" (:path req)) "knife is taken")
    (is (= "Knife" (get-in req [:body :entry :name])))))

(deftest saving-a-new-entry-moves-to-its-address
  (let [db (editing {:page :library :type :weapons :id :new} [:name] "Spear")
        saved (assoc knife :id :spear :name "Spear")
        result (ev/saved {:db db} [::ev/saved :weapons {:entry saved}])]
    (is (= saved (get-in (valid! (:db result)) [:library :entries :weapons :spear])))
    (is (= {:page :library :type :weapons :id :spear} (fx-of result :ttg.routes/navigate)))
    (is (= "Saved Spear" (get-in result [:db :library :toast])))))

(deftest server-problems-land-on-fields
  (let [db (ev/save-failed (editing {:page :library :type :weapons :id :knife}) [::ev/save-failed {:problems {[:range] "Required"}}])]
    (is (= {[:range] "Required"} (get-in (valid! db) [:library :draft :errors])))
    (is (false? (get-in db [:library :draft :saving?])))))

(deftest csv-and-json-previews
  (let [db (ev/open-import (base-db {:page :library :type :weapons}) [::ev/open-import])]
    (testing "CSV of the current type"
      (let [csv (io/to-csv :weapons [knife (assoc knife :id :axe :name "Axe" :price 60) (assoc knife :id :bad :range "Far")])
            imp (get-in (valid! (ev/parse-file db [::ev/parse-file "weapons.csv" csv])) [:library :import])]
        (is (= :preview (:step imp)))
        (is (= {:same 1 :add 1 :invalid 1} (frequencies (map :status (:rows imp)))))
        (is (= 4 (:line (first (filter #(= :invalid (:status %)) (:rows imp))))))))
    (testing "JSON of any types"
      (let [json (js/JSON.stringify (clj->js {:format "ttg-library" :entries {:weapons [(assoc knife :price 30)] :spells []}}))
            imp (get-in (ev/parse-file db [::ev/parse-file "lib.JSON" json]) [:library :import])]
        (is (= [:replace] (map :status (:rows imp))))
        (is (= :knife (:id (:entry (first (:rows imp))))) "ids come back as keywords")
        (is (some #(str/includes? % "spells") (:notes imp)))))
    (testing "a file that isn't JSON"
      (let [imp (get-in (ev/parse-file db [::ev/parse-file "lib.json" "{nope"]) [:library :import])]
        (is (= :pick (:step imp)))
        (is (str/includes? (:error imp) "lib.json"))))))

(deftest imports-go-in-chunks
  (let [many (for [i (range 250)] (assoc knife :id (keyword (str "k" i)) :name (str "K" i)))
        db (-> (base-db {:page :library :type :weapons})
               (ev/open-import [::ev/open-import])
               (ev/parse-file [::ev/parse-file "w.json" (js/JSON.stringify (clj->js {:weapons many}))]))
        r1 (ev/run-import {:db db} [::ev/run-import])
        sent (fn [r] (count (get-in (fx-of r :ttg.http/request) [:body :entries :weapons])))]
    (is (= db/import-chunk (sent r1)))
    (let [chunk1 (get-in (fx-of r1 :ttg.http/request) [:on-ok 1])
          r2 (ev/chunk-done {:db (:db r1)} [::ev/chunk-done chunk1 {:saved 100}])
          r3 (ev/chunk-done {:db (:db r2)} [::ev/chunk-done (get-in (fx-of r2 :ttg.http/request) [:on-ok 1]) {:saved 100}])
          r4 (ev/chunk-done {:db (:db r3)} [::ev/chunk-done (get-in (fx-of r3 :ttg.http/request) [:on-ok 1]) {:saved 50}])]
      (is (= [100 50] [(sent r2) (sent r3)]))
      (is (nil? (fx-of r4 :ttg.http/request)))
      (is (= :done (get-in (valid! (:db r4)) [:library :import :step])))
      (is (= 250 (get-in r4 [:db :library :import :saved])))
      (is (= 251 (count (get-in r4 [:db :library :entries :weapons])))))))
