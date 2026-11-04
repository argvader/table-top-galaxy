(ns ttg.genesys.describe-test
  (:require [clojure.test :refer [deftest is testing]]
            [ttg.genesys.describe :as d]
            [ttg.genesys.rules :as r]
            [ttg.genesys.rules-test :as rt]))

(def ix
  (r/index (-> rt/library
               (assoc :item-qualities [{:id :auto-fire :name "Auto-Fire" :activation "active" :text "Sprays."}])
               (update :talents conj {:id :enduring :name "Enduring" :tier 3 :ranked true :activation "Passive"}))))

(defn- by-label [facts label] (first (filter #(= label (:label %)) facts)))

(deftest weapons-explain-their-numbers
  (let [club {:id :club :name "Club" :kind :weapon :skill "Melee" :adds-brawn true :damage-bonus 2 :crit 4
              :range "Engaged" :price 20 :rarity 1 :encumbrance 2}
        facts (d/gear-facts rt/fizz ix club)]
    (is (= "Brawn + 2" (:value (by-label facts "Damage"))))
    (is (re-find #"that's 4" (:explain (by-label facts "Damage"))) "Fizz has Brawn 2")
    (is (= "Spend 4 advantage on a hit to cause a Critical Injury." (:explain (by-label facts "Critical"))))
    (is (re-find #"Brawn" (:explain (by-label facts "Skill"))))
    (is (= "Only someone right next to you." (:explain (by-label facts "Range"))))))

(deftest qualities-match-whatever-the-case
  (is (= [{:name "Auto-fire" :rating nil :activation "active" :text "Sprays."}
          {:name "Silent" :rating 2 :activation nil :text nil}]
         (d/qualities {:qualities [{:name "Auto-fire"} {:name "Silent" :rating 2}]} ix))))

(deftest armor-and-carrying
  (let [facts (d/gear-facts rt/fizz ix (get-in ix [:gear "plate"]))]
    (is (= "+2" (:value (by-label facts "Soak"))))
    (is (re-find #"3 less" (:explain (by-label facts "Encumbrance")))))
  (is (= "+4" (:value (by-label (d/gear-facts rt/fizz ix (get-in ix [:gear "pack"])) "Carry")))))

(deftest body-mods-say-what-they-do
  (testing "a cybernetic with a choice"
    (let [{:keys [kind options strain]} (d/body-mod-facts {:name "Cyberorgan"})]
      (is (= "Cybernetic" kind))
      (is (some? strain))
      (is (= "Heart, +2 wound threshold" (first options)))))
  (is (= ["+1 rank in Coordination" "+1 rank in Vigilance"] (:effects (d/body-mod-facts {:name "Enhanced Reflexes"}))))
  (is (nil? (:strain (d/body-mod-facts {:name "Cat Claws"}))) "g-mods cost no strain")
  (is (nil? (d/body-mod-facts {:name "Rope"}))))

(deftest talents-explain-themselves
  (let [enduring (get-in ix [:talents "enduring"])
        facts (d/talent-facts (assoc rt/fizz :talents [{:id "enduring" :rank 1}]) ix enduring)]
    (is (= "+1 soak" (:value (by-label facts "Each rank"))))
    (is (= "Rank 1" (:value (by-label facts "You have"))))
    (is (= "20 XP" (:value (by-label facts "Next costs"))) "rank 2 is bought at tier 4")
    (is (re-find #"Always on" (:explain (by-label facts "Activation")))))
  (is (nil? (by-label (d/talent-facts (assoc rt/fizz :talents [{:id "parry" :rank 1}]) ix (get-in ix [:talents "parry"])) "Next costs"))
      "an owned unranked talent can't be bought again"))
