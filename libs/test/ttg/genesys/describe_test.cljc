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

(deftest gear-facts-leave-shopping-to-buy-facts
  (let [plate (get-in ix [:gear "plate"])]
    (is (nil? (by-label (d/gear-facts rt/fizz ix plate) "Price")))
    (is (= "300 credits" (:value (by-label (d/buy-facts rt/fizz ix plate) "Price"))))))

(deftest an-attack-explains-its-dice
  (let [baton {:id :baton :name "Baton" :kind :weapon :skill "Melee" :damage 3 :crit 4 :range "Engaged"}
        fizz (assoc rt/fizz :skill-buy {:melee 1})
        {:keys [check facts]} (d/attack fizz ix baton)]
    (is (= "Baton attack" (:label check)))
    (is (= "Rank 1" (:value (by-label facts "Melee"))))
    (is (= "1 Proficiency, 1 Ability" (:value (by-label facts "Dice"))) "Brawn 2, rank 1")
    (is (re-find #"lower, 1," (:explain (by-label facts "Dice"))))
    (is (nil? (by-label facts "Encumbered"))))
  (is (nil? (d/attack rt/fizz ix {:name "Ray" :skill "Gunnery"})) "no such skill in the library"))

(deftest pools-in-words
  (is (= "2 Proficiency, 1 Ability, 1 Setback" (d/pool-words {:ability 1 :proficiency 2 :setback 1})))
  (is (= "" (d/pool-words {}))))

(deftest weapon-qualities-change-the-attack
  (let [rifle {:id :rifle :name "Rifle" :kind :weapon :skill "Melee" :damage 8 :crit 3 :range "Long"
               :qualities [{:name "Accurate" :rating 1} {:name "Cumbersome" :rating 4} {:name "Inaccurate"}]}
        {:keys [check facts]} (d/attack rt/fizz ix rifle)]
    (is (= 1 (get-in check [:pool :boost])) "Accurate 1 is a Boost die in the pool")
    (is (= 1 (get-in check [:pool :setback])) "an unrated Inaccurate counts as 1")
    (is (= "+1 Boost" (:value (by-label facts "Accurate"))))
    (is (= "+2 Difficulty" (:value (by-label facts "Cumbersome"))) "Brawn 2 against Cumbersome 4")
    (is (re-find #"you have 2" (:explain (by-label facts "Cumbersome")))))
  (is (empty? (r/weapon-difficulty rt/fizz ix {:qualities [{:name "Cumbersome" :rating 2}]})) "Brawn 2 is enough"))
