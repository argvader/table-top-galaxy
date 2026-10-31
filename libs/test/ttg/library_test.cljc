(ns ttg.library-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.walk :as walk]
            [ttg.library :as lib]
            [ttg.library.fields :as fields]
            [ttg.library.io :as io]
            #?@(:clj [[clojure.edn :as edn]
                      [clojure.java.io :as jio]])))

(def brass-knuckles
  {:id :brass-knuckles-android :kind :weapon :name "Brass Knuckles" :skill "Brawl" :group "Brawl Weapons"
   :damage-bonus 1 :adds-brawn true :crit 4 :range "Engaged" :encumbrance 1 :hard-points 1
   :qualities [{:name "Disorient" :rating 3}] :price 40 :rarity 1 :setting "android"
   :source {:book "sotb" :page 91}})

(def bioroid
  {:id :bioroid :name "Bioroid" :wounds 11 :strain 8 :xp 170
   :characteristics {:brawn 3 :agility 1 :intellect 1 :cunning 1 :willpower 1 :presence 1}
   :abilities [{:name "Inorganic" :text "Heals with Mechanics."}] :source {:book "sotb" :page 26}})

(def grunt
  {:id :grunt :name "Grunt" :type "minion" :soak 3 :source {:book "crb" :page 200}
   :characteristics {"brawn" 2 "agility" 2 "intellect" 1 "cunning" 1 "willpower" 1 "presence" 1}})

(defn- jsonish
  "What JSON.parse (or cheshire without key fn) hands back: string keys, keywords as plain strings."
  [x]
  (walk/postwalk #(cond (keyword? %) (name %) :else %) x))

;; ---------- Validation ----------

(deftest problems-name-the-field
  (is (nil? (lib/problems :weapons brass-knuckles)))
  (is (= {[:name] "Required"} (lib/problems :weapons (dissoc brass-knuckles :name))))
  (is (= {[:crit] "Not a valid value"} (lib/problems :weapons (assoc brass-knuckles :crit -1))))
  (is (= {[:damage] "Needs damage or a damage bonus"} (lib/problems :weapons (dissoc brass-knuckles :damage-bonus))))
  (is (contains? (lib/problems :nonsense {}) [])))

(deftest custom-entries-validate-without-a-page
  (doseq [{:keys [type]} fields/editable-types]
    (let [e (lib/new-entry type "Sample")]
      (testing (name type)
        (is (= "custom" (get-in e [:source :book])))
        ;; Talents, qualities and careers still need text or 8 skills before they're valid: that's the GM's job.
        (when-not (#{:talents :item-qualities :careers} type)
          (is (nil? (lib/problems type e))))))))

(deftest unique-ids-never-collide
  (is (= :knife (lib/unique-id #{} :knife)))
  (is (= :knife-3 (lib/unique-id #{:knife :knife-2} :knife))))

(deftest import-plan-statuses
  (let [library {:weapons {:brass-knuckles-android (assoc brass-knuckles :sources [{:book "sotb" :page 91}])}}
        rows (lib/plan-import library {:weapons [brass-knuckles
                                                 (assoc brass-knuckles :id :brass-2 :name "Brass 2")
                                                 (assoc brass-knuckles :id :brass-knuckles-android :price 50)
                                                 (dissoc brass-knuckles :range)]}
                              )]
    (is (= [:same :add :replace :invalid] (mapv :status rows)))
    (is (= {:same 1 :add 1 :replace 1 :invalid 1} (lib/import-summary rows)))))

(deftest cites
  (is (= "SotB p.91" (lib/cite {:book "sotb" :page 91})))
  (is (= "Gun Locker p.12" (lib/cite {:book "sotb-guns" :page 12})))
  (is (= "Custom" (lib/cite {:book "custom"})))
  (is (= "House rules" (lib/cite {:book "house" :doc "obligation"}))))

;; ---------- Fields and CSV ----------

(deftest cells-round-trip
  (doseq [[type e] [[:weapons brass-knuckles] [:archetypes bioroid]]]
    (let [{:keys [entry errors]} (io/apply-strings type {:id (:id e) :source (:source e)} (io/strings type e))]
      (is (empty? errors))
      (is (= (dissoc e :kind) entry)))))

(deftest cell-parsing
  (let [tier (first (filter #(= [:tier] (:path %)) (fields/by-type :talents)))
        skills (first (filter #(= [:skills] (:path %)) (fields/by-type :careers)))
        crit (first (filter #(= [:crit] (:path %)) (fields/by-type :weapons)))]
    (is (= {:ok 3} (io/parse-cell tier " 3 ")))
    (is (:error (io/parse-cell tier "6")))
    (is (= {:error "Required"} (io/parse-cell tier "")))
    (is (= {:ok nil} (io/parse-cell crit "")))
    (is (= {:ok []} (io/parse-cell (first (filter #(= [:qualities] (:path %)) (fields/by-type :weapons))) "")))
    (is (str/includes? (:error (io/parse-cell skills "Melee; Brawl")) "has 2"))))

(deftest csv-reader-handles-quotes
  (is (= [["a" "b,c" "say \"hi\""] ["line\nbreak" "" "z"]]
         (io/read-csv "a,\"b,c\",\"say \"\"hi\"\"\"\r\n\"line\nbreak\",,z\r\n")))
  (is (= [["a"]] (io/read-csv "a"))))

(deftest csv-round-trip
  (let [plain (assoc brass-knuckles :id :club :name "Club" :qualities [])
        {:keys [rows]} (io/from-csv :weapons (io/to-csv :weapons [plain]))]
    (is (= (dissoc plain :kind) (:entry (first rows))) "a weapon with no qualities keeps an empty list"))
  (let [csv (io/to-csv :weapons [brass-knuckles])
        {:keys [rows ignored]} (io/from-csv :weapons csv)]
    (is (empty? ignored))
    (is (= 1 (count rows)))
    (is (empty? (:errors (first rows))))
    (is (= (dissoc brass-knuckles :kind) (:entry (first rows))))))

(deftest csv-import-from-a-hand-made-sheet
  (let [csv "name,tier,activation,ranked,text,Mystery\nQuick Fix,2,active (maneuver),yes,Fix it fast.,?\n,,,,\nBad,9,Passive,no,x,\n"
        {:keys [rows ignored]} (io/from-csv :talents csv)]
    (is (= ["Mystery"] ignored))
    (is (= [2 4] (mapv :line rows)) "blank rows are skipped; lines count from the header")
    (is (= {:id :quick-fix :name "Quick Fix" :tier 2 :activation "Active (Maneuver)" :ranked true :text "Fix it fast."
            :source {:book "custom"}}
           (:entry (first rows))))
    (is (contains? (:errors (second rows)) [:tier]))))

(deftest csv-template-is-headers-only
  (is (= 1 (count (io/read-csv (io/to-csv :careers []))))))

;; ---------- JSON ----------

(deftest json-entries-get-their-keywords-back
  (is (= brass-knuckles (io/from-json-entry :weapons (jsonish brass-knuckles))))
  (is (= bioroid (io/from-json-entry :archetypes (jsonish bioroid))))
  (is (= grunt (io/from-json-entry :adversaries (jsonish grunt))) "adversary characteristics keep string keys"))

(deftest json-file-shapes
  (let [export {"format" "ttg-library" "version" 1 "setting" "android" "entries" (jsonish {:weapons [brass-knuckles]})}]
    (is (= {:entries {:weapons [brass-knuckles]} :unknown []} (io/from-json lib/types export)))
    (is (= {:entries {:archetypes [bioroid]} :unknown ["spells"]}
           (io/from-json lib/types (jsonish {:archetypes [bioroid] :spells []}))))
    (is (thrown? #?(:clj Exception :cljs js/Error) (io/from-json lib/types [1 2])))))

#?(:clj
   (deftest every-book-entry-survives-json
     (let [files (->> (file-seq (jio/file "../content"))
                      (filter #(str/ends-with? (.getName %) ".edn"))
                      (remove #(= "settings" (.getName (.getParentFile %)))))]
       (is (seq files))
       (doseq [f files
               :let [type (keyword (str/replace (.getName f) #"\.edn$" ""))]
               :when (lib/types type)
               e (edn/read-string (slurp f))]
         (is (= e (io/from-json-entry type (jsonish e))) (str (.getPath f) " " (:id e)))))))
