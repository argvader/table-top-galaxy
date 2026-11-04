(ns ttg.genesys.rules-test
  "A small made-up library: the shape of the real one, with none of its text. The port was checked against the prototype's
   own code on the full library; these keep it honest from here on."
  (:require [clojure.test :refer [deftest is testing]]
            [ttg.genesys.rules :as r]))

(defn- skill [nm k] {:id (keyword (.toLowerCase nm)) :name nm :characteristic k :category "General"})

(def library
  {:archetypes [{:id :natural :name "Natural" :xp 110 :wounds 10 :strain 10
                 :characteristics {:brawn 2 :agility 2 :intellect 2 :cunning 2 :willpower 2 :presence 2}}
                {:id :cyborg :name "Cyborg" :xp 100 :wounds 11 :strain 9
                 :characteristics {:brawn 3 :agility 2 :intellect 2 :cunning 2 :willpower 1 :presence 2}
                 :free-skills {:ranks 1 :choose 2 :non-career true}
                 :free-item {:group "Cybernetics" :max-price 1000}}]
   :careers [{:id :tech :name "Tech" :skills ["Mechanics" "Cool" "Vigilance" "Brawl" "Piloting" "Stealth" "Coercion" "Melee"]}]
   :skills (mapv skill ["Mechanics" "Cool" "Vigilance" "Brawl" "Piloting" "Stealth" "Coercion" "Melee" "Streetwise" "Medicine"]
                 (cycle [:intellect :presence :willpower :brawn]))
   :talents [{:id :grit :name "Grit" :tier 1 :ranked true}
             {:id :toughened :name "Toughened" :tier 1 :ranked true}
             {:id :parry :name "Parry" :tier 1 :ranked false}
             {:id :dodge :name "Dodge" :tier 2 :ranked false}]
   :armor [{:id :vest :name "Vest" :kind :armor :soak 1 :defense 0 :price 50 :encumbrance 2 :group "Armor"}
           {:id :plate :name "Plate" :kind :armor :soak 2 :defense 1 :price 300 :encumbrance 4 :group "Armor"}]
   :weapons [{:id :baton :name "Baton" :kind :weapon :price 100 :encumbrance 1 :group "Melee"}]
   :gear [{:id :pack :name "Pack" :kind :gear :price 20 :encumbrance 0 :encumbrance-bonus 4 :group "Other"}
          {:id :eye :name "Eye" :kind :gear :price 800 :encumbrance 0 :group "Cybernetics"}]
   :obligation-types [{:id :debt :name "Debt" :kind :obligation :roll [1 100]}]})

(def ix (r/index library))
(defn- s [nm] (first (filter #(= nm (:name %)) (:skills ix))))

(def fizz
  (assoc (r/blank-character) :name "Fizz" :archetype "natural" :career "tech"
         :career-picks ["Mechanics" "Cool" "Vigilance" "Brawl"]))

(deftest xp-prices
  (testing "a raise costs ten times the new rating"
    (is (= 70 (r/characteristics-xp (assoc fizz :char-buy {:brawn 2}) ix)) "from 2 to 4 is 30 + 40")
    (is (= 30 (r/characteristics-xp (assoc fizz :char-buy {:intellect 1}) ix))))
  (testing "skills cost 5 x rank, plus 5 out of career"
    (is (= 10 (r/skills-xp (assoc fizz :skill-buy {:mechanics 1}) ix)) "already 1 free, so rank 2")
    (is (= 10 (r/skills-xp (assoc fizz :skill-buy {:streetwise 1}) ix)) "non-career rank 1")
    (is (= 5 (r/skills-xp (assoc fizz :skill-buy {:piloting 1}) ix))))
  (testing "talents cost 5 x tier, and ranks climb a tier"
    (is (= 15 (r/talents-xp (assoc fizz :talents [{:id "grit" :rank 2}]) ix)))))

(deftest budget-includes-the-obligation
  (is (= 110 (r/budget fizz ix)))
  (is (= 125 (r/budget (assoc fizz :ob {:type "debt" :magnitude 15}) ix))))

(deftest the-talent-pyramid
  (is (r/pyramid-ok? [0 2 1 0 0 0]))
  (is (not (r/pyramid-ok? [0 1 1 0 0 0])))
  (let [dodge (first (filter #(= :dodge (:id %)) (:talents library)))]
    (is (= "Needs 2 tier 1 talents first (you have 1)"
           (r/talent-buy-block (assoc fizz :talents [{:id "parry" :rank 1}]) ix dodge)))
    (is (nil? (r/talent-buy-block (assoc fizz :talents [{:id "parry" :rank 1} {:id "grit" :rank 1}]) ix dodge)))
    (is (= "Remove a tier 2 talent first"
           (r/talent-drop-block (assoc fizz :talents [{:id "parry" :rank 1} {:id "grit" :rank 1} {:id "dodge" :rank 1}]) ix "parry")))))

(deftest derived-stats
  (let [c (assoc fizz :char-buy {:brawn 1} :talents [{:id "toughened" :rank 2} {:id "grit" :rank 1}]
                 :gear [{:id "vest" :qty 1} {:id "plate" :qty 1} {:id "pack" :qty 2}])]
    (is (= {:wounds 17 :strain 13 :soak 5 :defense 1 :encumbrance 3 :encumbrance-max 12 :encumbrance-bonus 4}
           (r/derived c ix)) "the best armor is worn and weighs 3 less; a second pack adds no more carry")))

(deftest checks-carry-their-pool
  (let [c (assoc fizz :skill-buy {:brawl 1})]
    (is (= {:label "Brawl" :detail "Brawn 2 · Rank 2" :pool {:proficiency 2 :ability 0 :setback 0}}
           (r/skill-check c ix (s "Brawl"))))
    (is (= {:label "Brawn check" :detail "Brawn 2, no skill" :pool {:ability 2 :setback 0}}
           (r/characteristic-check c ix :brawn)))
    (is (= {:label "Baton attack" :detail "Brawl · Brawn 2 · Rank 2" :pool {:proficiency 2 :ability 0 :setback 0}
            :weapon {:range "Engaged" :crit 4 :damage 3}}
           (r/weapon-check c ix {:name "Baton" :skill "Brawl" :range "Engaged" :crit 4 :adds-brawn true :damage-bonus 1})))
    (is (= 6 (r/weapon-damage c ix {:damage 6})))
    (is (nil? (r/weapon-check c ix {:name "Baton" :skill "Nothing"})))))

(deftest being-encumbered-adds-setback
  (let [heavy (r/index (assoc library :gear [{:id :crate :name "Crate" :kind :gear :price 1 :encumbrance 9 :group "Other"}]))
        c (assoc fizz :gear [{:id "crate" :qty 1}])]
    (testing "two over the threshold, on Brawn and Agility only"
      (is (= 2 (r/encumbrance-setback c heavy :brawn) (r/encumbrance-setback c heavy :agility)))
      (is (zero? (r/encumbrance-setback c heavy :presence))))
    (is (= {:label "Brawn check" :detail "Brawn 2, no skill · Encumbered +2 setback" :pool {:ability 2 :setback 2}}
           (r/characteristic-check c heavy :brawn)))))

(deftest defense-is-capped
  (let [lib (assoc library :armor [{:id :fort :name "Fort" :kind :armor :soak 0 :defense 6 :price 1 :encumbrance 2}])
        ix (r/index lib)]
    (is (= 4 (:defense (r/derived (assoc fizz :gear [{:id "fort" :qty 1}]) ix))))
    (is (= 0 (:encumbrance (r/derived (assoc fizz :gear [{:id "fort" :qty 1}]) ix))) "never below 0")))

(deftest pools-mix-characteristic-and-rank
  (is (= {:proficiency 1 :ability 1} (r/skill-pool fizz ix (s "Mechanics"))))
  (is (= {:proficiency 0 :ability 2} (r/skill-pool fizz ix (s "Medicine")))))

(deftest purchase-limits
  (is (= "Rank 2 is the most at creation" (r/skill-up-block (assoc fizz :skill-buy {:mechanics 1}) ix (s "Mechanics"))))
  (is (= "5 is the most" (r/characteristic-up-block (assoc fizz :char-buy {:brawn 3}) ix :brawn)))
  (is (= "Choose an archetype first" (r/characteristic-up-block (r/blank-character) ix :brawn)))
  (is (= "Needs 30 XP, you have 0" (r/characteristic-up-block (assoc fizz :talents [] :char-buy {:agility 3 :brawn 1}) ix :cunning)))
  (is (= "Bought" (r/gear-buy-block (assoc fizz :gear [{:id "vest" :qty 1}]) ix (get-in ix [:gear "vest"]))))
  (is (nil? (r/gear-buy-block (assoc fizz :gear [{:id "pack" :qty 1}]) ix (get-in ix [:gear "pack"]))) "gear stacks")
  (is (= "Not enough credits" (r/gear-buy-block fizz ix (get-in ix [:gear "eye"])))))

(deftest changing-your-mind
  (let [c (-> (assoc (r/blank-character) :archetype "cyborg" :career "tech" :arch-picks ["Streetwise" "Medicine"] :free-item "eye")
              (r/choose-archetype ix "natural"))]
    (is (= [] (:arch-picks c)))
    (is (nil? (:free-item c)) "the free cybernetic was the cyborg's"))
  (is (= [] (:career-picks (r/choose-career fizz ix "other"))))
  (is (= [{:id "grit" :rank 2}] (:talents (-> fizz (r/add-talent "grit") (r/add-talent "grit")))))
  (is (= [] (:talents (-> fizz (r/add-talent "grit") (r/remove-talent "grit")))))
  (is (= [] (:gear (-> fizz (r/add-gear "pack") (r/remove-gear "pack"))))))

(deftest issues-block-or-warn
  (let [messages #(set (map :text (r/issues % ix)))]
    (is (= ["Choose an archetype" "Choose a career" "Pick or roll a Secret or Obligation" "Give your character a name"]
           (mapv :text (r/blocks (r/issues (assoc (r/blank-character) :name "") ix)))))
    (is (contains? (messages (assoc fizz :career-picks ["Cool"])) "Pick 3 more career skills for a free rank"))
    (is (contains? (messages (assoc fizz :career-picks ["Cool" "Brawl" "Melee"])) "Pick 1 more career skill for a free rank"))
    (is (contains? (messages (assoc fizz :char-buy {:brawn 4})) "Brawn is 6. The most is 5"))
    (is (contains? (messages (assoc fizz :ob {:type "debt" :magnitude 5 :attachment "" :why ""})) "Say who or what your Obligation ties you to"))
    (is (= :warn (:level (first (filter #(= "No gear bought yet" (:text %)) (r/issues fizz ix))))))))

(deftest critical-injuries
  (let [crits {:crits [{:id :top :name "Top" :roll [151 nil] :severity 0}
                       {:id :low :name "Low" :roll [1 5] :severity 1 :text "Sting."}
                       {:id :mid :name "Mid" :roll [6 150] :severity 2}]}
        ix (r/index crits)]
    (testing "each injury already carried adds 10"
      (is (zero? (r/crit-bonus {:crits []})))
      (is (= 20 (r/crit-bonus {:crits [{} {}]}))))
    (testing "a total lands on its row, and the last row has no top"
      (is (= "Low" (:name (r/crit-for ix 5))))
      (is (= "Mid" (:name (r/crit-for ix 6))))
      (is (= "Top" (:name (r/crit-for ix 400)))))
    (testing "no table, no injury"
      (is (nil? (r/crit-for (r/index {}) 50))))
    (testing "an injury reads its text from the library and keeps the total rolled"
      (is (= {:id "low" :name "Low" :roll 4 :severity 1 :text "Sting."}
             (r/crit-entry ix {:id "low" :name "Low" :roll 4}))))
    (testing "an injury the library has lost still shows"
      (is (= {:id "gone" :name "Gone" :roll 4} (r/crit-entry ix {:id "gone" :name "Gone" :roll 4}))))))

;; ---------- Talents and body modifications that change the numbers ----------

(def mod-library
  (-> library
      (update :archetypes conj {:id :bioroid :name "Bioroid" :xp 170 :wounds 11 :strain 8
                                :characteristics {:brawn 3 :agility 1 :intellect 1 :cunning 1 :willpower 1 :presence 1}})
      (update :skills into [(assoc (skill "Perception" :cunning) :category "General")
                            (assoc (skill "Athletics" :brawn) :category "General")
                            (assoc (skill "Science" :intellect) :category "Knowledge")])
      (update :talents into [{:id :enduring :name "Enduring" :tier 4 :ranked true}
                             {:id :defensive :name "Defensive" :tier 4 :ranked true}
                             {:id :dedication :name "Dedication" :tier 5 :ranked true}
                             {:id :big-guns :name "Big Guns" :tier 2 :ranked false}])
      (update :gear into (for [n ["Cyberlimb" "Cyberorgan" "Cybereyes" "Sub-Dermal Armor" "Neuro-Regulator" "Spinal Modem" "Nighteyes"]]
                           {:id (keyword (.toLowerCase (.replace n " " "-"))) :name n :kind :gear :price 0 :group "Cybernetics"}))))

(def mix (r/index mod-library))
(defn- ms [nm] (first (filter #(= nm (:name %)) (:skills mix))))

(deftest talents-change-the-numbers
  (let [c (assoc fizz :talents [{:id "enduring" :rank 1} {:id "defensive" :rank 2} {:id "big-guns" :rank 1}
                                {:id "dedication" :rank 1 :picks ["brawn"]}])
        d (r/derived c mix)]
    (is (= 3 (r/characteristic-total c mix :brawn)) "Dedication adds 1")
    (is (= 2 (r/characteristic c mix :brawn)) "but not to the creation value XP is priced from")
    (is (= 4 (:soak d)) "Brawn 3 and Enduring 1")
    (is (= 2 (:defense d)))
    (is (= 13 (:encumbrance-max d)) "Big Guns: 10 plus Brawn")
    (is (= 13 (:wounds d)) "the higher Brawn counts")
    (is (= [:agility :intellect :cunning :willpower :presence] (r/dedication-options (r/add-talent c "dedication") mix 1))
        "a second rank can't pick Brawn again")))

(deftest cybernetics-change-the-numbers
  (let [c (assoc fizz :gear [{:id "cyberlimb" :qty 2 :picks ["brawn-arm" "agility-arm"]} {:id "cyberorgan" :qty 2 :picks ["heart" "lungs"]}
                             {:id "cybereyes" :qty 1} {:id "sub-dermal-armor" :qty 1} {:id "neuro-regulator" :qty 1}])
        d (r/derived c mix)]
    (is (= 3 (r/characteristic-total c mix :brawn)) "the arm")
    (is (= 3 (r/characteristic-total c mix :willpower)) "the neuro-regulator")
    (is (= 1 (r/rank-total c mix (ms "Perception"))) "cybereyes")
    (is (= 1 (r/rank-total c mix (ms "Athletics"))) "lungs")
    (is (= 0 (r/rank-of c mix (ms "Perception"))) "ranks from cybernetics aren't bought ranks")
    (is (= 15 (:wounds d)) "10 + Brawn 3 + 2 for the heart")
    (is (= 7 (:strain d)) "10 + Willpower 3, less 6: two arms count once, then each organ, eyes, armor, regulator")
    (is (= 4 (:soak d)) "Brawn 3 + 1 sub-dermal")
    (is (= {:proficiency 1 :ability 1} (r/skill-pool c mix (ms "Perception"))) "rolls use the totals")))

(deftest cybernetic-limits-and-exemptions
  (let [two-arms (assoc fizz :gear [{:id "cyberlimb" :qty 2 :picks ["brawn-arm" "brawn-leg"]}])]
    (is (= 3 (r/characteristic-total two-arms mix :brawn)) "no more than +1 to a characteristic from cybernetics")
    (is (= 10 (:strain (r/derived two-arms mix))) "an arm and a leg cost 1 each"))
  (is (= 1 (r/rank-total (assoc fizz :gear [{:id "spinal-modem" :qty 1}]) mix (ms "Science"))) "the spinal modem raises Knowledge skills")
  (is (= 9 (:strain (r/derived (assoc fizz :archetype "bioroid" :gear [{:id "cybereyes" :qty 1}]) mix))) "8 + Willpower 1: bioroids lose no strain")
  (let [cyborg (assoc fizz :archetype "cyborg" :free-item "cybereyes" :gear [{:id "sub-dermal-armor" :qty 1}])]
    (is (= 9 (:strain (r/derived cyborg mix))) "9 + Willpower 1, less 1: the free cybernetic costs nothing")
    (is (= 1 (r/rank-total cyborg mix (ms "Perception"))) "but still works"))
  (is (= 12 (:strain (r/derived (assoc fizz :gear [{:id "nighteyes" :qty 1}]) mix))) "g-mods change no numbers"))

(deftest choices-and-adjustments
  (let [c (assoc fizz :gear [{:id "cyberlimb" :qty 1}])
        limb (first (r/carried c mix))]
    (is (= ["Cyberlimb"] (r/unchosen c mix)))
    (is (some #(re-find #"Cyberlimb" (:text %)) (r/issues c mix)) "the builder says so")
    (is (= 3 (r/characteristic-total (r/set-pick c limb 0 "brawn-leg") mix :brawn)))
    (is (empty? (r/unchosen (r/set-pick c limb 0 "brawn-leg") mix))))
  (let [cyborg (assoc fizz :archetype "cyborg" :free-item "cyberlimb")
        limb (first (r/carried cyborg mix))]
    (is (= ["agility-arm"] (:free-picks (r/set-pick cyborg limb 0 "agility-arm"))) "the free item keeps its own pick"))
  (is (= ["Dedication"] (r/unchosen (assoc fizz :talents [{:id "dedication" :rank 1}]) mix)))
  (is (= ["cunning"] (:picks (first (:talents (r/set-dedication (assoc fizz :talents [{:id "dedication" :rank 1}]) 0 :cunning))))))
  (let [d (r/derived (assoc fizz :adjust {:wounds -1 :strain 2 :soak 1 :defense 1}) mix)]
    (is (= [11 14 3 1] ((juxt :wounds :strain :soak :defense) d)) "a GM adjustment is added last")
    (is (= ["GM adjustment" -1] (last (:wounds (r/derived-parts (assoc fizz :adjust {:wounds -1}) mix)))))))

(deftest a-free-item-over-its-cap
  (let [cyborg (assoc fizz :archetype "cyborg" :arch-picks ["Streetwise" "Medicine"])
        over (-> library (update :gear conj {:id :limb :name "Limb" :kind :gear :price 2000 :group "Cybernetics"}) r/index)]
    (is (nil? (r/free-item-over (assoc cyborg :free-item "eye") over)))
    (is (= 1000 (r/free-item-over (assoc cyborg :free-item "limb") over)))
    (is (some #(= "Your free cybernetic costs 1000 credits more than the 1000 allowed" (:text %))
              (r/blocks (r/issues (assoc cyborg :free-item "limb") over))))))

(deftest only-the-gm-may-break-rules
  (is (r/gm? (assoc fizz :access "gm")))
  (is (not (r/gm? (assoc fizz :access "owner"))))
  (is (not (r/gm? fizz))))

(deftest the-index-carries-item-qualities
  (is (= "Vicious" (get-in (r/index {:item-qualities [{:id :vicious :name "Vicious"}]}) [:qualities "vicious" :name]))))

(deftest weapon-qualities-in-the-pool
  (is (= {:boost 2} (r/quality-dice {:qualities [{:name "accurate" :rating 2}]})) "any case")
  (is (= {} (r/quality-dice {:qualities [{:name "Vicious" :rating 2}]})))
  (is (nil? (r/quality-rating {} "Accurate"))))

(deftest strain-recovery-uses-the-better-of-cool-and-discipline
  (let [lib (r/index (update library :skills conj (skill "Discipline" :willpower)))]
    (testing "same number of dice: Cool, a career skill with a rank, has the Proficiency"
      (is (= {:label "Recover strain: Cool" :pool {:proficiency 1 :ability 1 :setback 0} :ladder 0 :recover? true}
             (dissoc (r/recovery-check fizz lib) :detail))))
    (testing "more Willpower rolls more dice on Discipline"
      (is (= "Recover strain: Discipline" (:label (r/recovery-check (assoc fizz :char-buy {:willpower 1}) lib)))))
    (is (nil? (r/recovery-check fizz (r/index (assoc library :skills [])))) "nothing to roll without either skill")))
