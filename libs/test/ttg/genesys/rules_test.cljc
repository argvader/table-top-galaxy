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
