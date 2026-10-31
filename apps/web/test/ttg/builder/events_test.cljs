(ns ttg.builder.events-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is]]
            [ttg.builder.db :as db]
            [ttg.builder.events :as ev]
            [ttg.genesys.rules :as rules]
            [ttg.http :as http]))

(def ix {:archetypes {"average" {:id :average :name "Average" :xp 100 :characteristics {:brawn 2 :agility 2}}}
         :careers {} :talents {} :gear {} :skills [] :motivations []
         :obligations [{:id :debt :name "Debt" :roll [1 50]} {:id :bounty :name "Bounty" :roll [51 100]}]})

(defn- state [& kvs] {:content {:ix ix} :builder (apply assoc (db/default-builder) kvs)})

(deftest opening-picks-up-the-draft-and-loads-that-library
  (let [fx (ev/open-new {:db {} :stored-draft {:name "Krum"}} [::ev/open-new "c1"])]
    (is (= "Krum" (get-in fx [:db :builder :character :name])))
    (is (= {:name "" :text ""} (get-in fx [:db :builder :character :motives :fear])) "old drafts gain new fields")
    (is (= "c1" (get-in fx [:db :builder :campaign-id])))
    (is (= "c1" (second (:dispatch fx))))))

(deftest changes-reach-the-draft-only-for-new-characters
  (let [fx (ev/change {:db (state :campaign-id "c1")} [::ev/change assoc :name "Krum"])]
    (is (= "Krum" (get-in fx [:db :builder :character :name])))
    (is (= ["c1" "Krum"] (update (::ev/store-draft fx) 1 :name))))
  (is (not (contains? (ev/change {:db (state :editing? true)} [::ev/change assoc :name "Krum"]) ::ev/store-draft))))

(deftest rules-functions-get-the-library
  (let [fx (ev/change-with-library {:db (state)} [::ev/change-with-library rules/choose-archetype "average"])]
    (is (= "average" (get-in fx [:db :builder :character :archetype])))))

(deftest the-roll-lands-in-the-table
  (let [fx (ev/roll-obligation {:db (state) :d100 77} [::ev/roll-obligation])]
    (is (= {:type "bounty" :rolled 77} (select-keys (get-in fx [:db :builder :character :ob]) [:type :rolled])))))

(deftest steps-stay-in-range
  (is (= 0 (get-in (ev/go {:db (state)} [::ev/go -1]) [:db :builder :step])))
  (is (= 6 (get-in (ev/go {:db (state)} [::ev/go 99]) [:db :builder :step])))
  (is (= :talents (get-in (ev/go {:db (state)} [::ev/go 3 :talents]) [:db :builder :xp-tab]))))

(def ready (assoc (rules/blank-character) :name "Krum" :archetype "average"))

(deftest save-sends-only-a-valid-character
  (with-redefs [rules/issues (constantly [{:level :block :text "Name it"}])]
    (is (nil? (ev/save {:db (state :character ready)} [::ev/save]))))
  (with-redefs [rules/issues (constantly [])]
    (let [fx (ev/save {:db (state :character ready)} [::ev/save])]
      (is (= "POST" (:method (::http/request fx))))
      (is (get-in fx [:db :builder :saving?])))))

(deftest a-failed-link-never-makes-a-twin
  (let [saved-char (assoc ready :id "x1" :version 1)
        fx (ev/created {:db (state :character ready :campaign-id "c1")} [::ev/created {:character saved-char}])
        request (::http/request fx)]
    (is (= "/characters/x1/campaign" (:path request)))
    (is (= ["c1" nil] (::ev/store-draft fx)) "the draft goes")
    (with-redefs [rules/issues (constantly [])]
      (let [db (ev/save-failed (:db fx) [::ev/save-failed {:error "Nope"}])]
        (is (= "/characters/x1/campaign" (:path (::http/request (ev/save {:db db} [::ev/save])))))))))

(deftest created-without-a-campaign-goes-straight-to-the-sheet
  (let [c (assoc ready :id "x1" :version 1)]
    (is (= [::ev/saved {:character c}]
           (:dispatch (ev/created {:db (state :character ready)} [::ev/created {:character c}]))))))

(deftest editing-saves-over-the-version
  (let [c (assoc ready :id "x1" :version 3 :campaign-id "c1")
        request (#'ev/save-request {:character c :editing? true :campaign-id "c1"})]
    (is (= "PUT" (:method request)))
    (is (= "/characters/x1" (:path request)))
    (is (= 3 (get-in request [:body :character :version])))))

(deftest every-handler-keeps-the-spec
  (is (s/valid? ::db/app-db (state)))
  (is (s/valid? ::db/app-db (ev/save-failed (state :saving? true) [::ev/save-failed {:error "x"}])))
  (is (s/valid? ::db/app-db (ev/load-failed (state) [::ev/load-failed {}]))))
