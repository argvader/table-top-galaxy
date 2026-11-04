(ns ttg.genesys.describe
  "Plain-language explanations of gear and talents for the builder's info drawer. Pure functions of a character, the
   rules index (ttg.genesys.rules/index) and an entry. Each fact is {:label :value :explain}, :explain optional."
  (:require [clojure.string :as str]
            [ttg.genesys.mods :as mods]
            [ttg.genesys.rules :as r]))

(def skill-blurbs
  "What each weapon skill covers, in our own words."
  {"brawl" "Fists, claws and anything strapped to your hands. Brawl uses Brawn."
   "melee" "Swinging or stabbing with a hand weapon. Melee uses Brawn."
   "melee (light)" "Swinging or stabbing with a light, one-handed weapon. Uses Brawn."
   "melee (heavy)" "Swinging a big two-handed weapon. Uses Brawn."
   "ranged (light)" "Pistols and other one-handed guns. Ranged (Light) uses Agility."
   "ranged (heavy)" "Rifles, shotguns and other two-handed guns. Ranged (Heavy) uses Agility."
   "ranged" "Any gun. Ranged uses Agility."
   "gunnery" "Mounted and crew-served heavy weapons. Gunnery uses Agility."})

(def range-blurbs
  {"Engaged" "Only someone right next to you."
   "Short" "Across a room, a few metres."
   "Medium" "Across a street or a large hall."
   "Long" "Down a long corridor or across a plaza."
   "Extreme" "As far as you can see a target clearly."
   "Strategic" "Far beyond sight: ship or vehicle scale."})

(def activation-blurbs
  {"Passive" "Always on. You don't have to do anything to use it."
   "Active (Action)" "Takes your action for the turn, the same as attacking."
   "Active (Maneuver)" "Takes one of your maneuvers, like moving or aiming."
   "Active (Incidental)" "Free to use on your turn: no action or maneuver needed."
   "Active (Incidental, Out of Turn)" "Free, and usable even when it isn't your turn, such as when you're hit."})

(def effect-blurbs
  "Talent effects (ttg.genesys.mods/talent-effects) in words, per rank."
  {:wounds "wound threshold" :strain "strain threshold" :soak "soak" :defense "defense"})

(defn- fact
  ([label value] (fact label value nil))
  ([label value explain] (cond-> {:label label :value (str value)} explain (assoc :explain explain))))

(defn- kind [item] (some-> (:kind item) name))

(defn- damage-fact [c ix {:keys [adds-brawn damage damage-bonus]}]
  (if adds-brawn
    (fact "Damage" (str "Brawn + " (or damage-bonus 0))
          (str "With your Brawn that's " (+ (r/characteristic-total c ix :brawn) (or damage-bonus 0))
               ", plus one for each net success on the attack."))
    (fact "Damage" damage "Plus one for each net success on the attack. The target's soak is taken off.")))

(defn- crit-fact [{:keys [crit]}]
  (when crit
    (fact "Critical" crit (str "Spend " crit " advantage on a hit to cause a Critical Injury."))))

(defn- weapon-facts [c ix {:keys [skill range] :as w}]
  [(fact "Skill" skill (skill-blurbs (str/lower-case (str skill))))
   (damage-fact c ix w)
   (crit-fact w)
   (fact "Range" range (range-blurbs range))])

(defn- armor-facts [{:keys [soak defense]}]
  [(fact "Soak" (str "+" soak) "Taken off the damage of every hit.")
   (fact "Defense" defense "Adds setback dice to attacks against you.")])

(defn- carry-facts [c ix {:keys [encumbrance encumbrance-bonus] :as item}]
  (let [{:keys [encumbrance-max]} (r/derived c ix)]
    [(when (some? encumbrance)
       (fact "Encumbrance" encumbrance
             (str "Counts toward your carrying limit of " encumbrance-max "."
                  (when (= "armor" (kind item)) " Worn armor counts 3 less."))))
     (when encumbrance-bonus (fact "Carry" (str "+" encumbrance-bonus) "Raises your carrying limit."))]))

(defn- buy-facts [c ix {:keys [price rarity restricted]}]
  [(fact "Price" (if price (str price " credits") "–") (str "You have " (r/credits-left c ix) " left to spend."))
   (fact "Rarity" (if (some? rarity) rarity "–") "0 is sold everywhere; 10 is nearly impossible to find.")
   (when restricted (fact "Restricted" "Yes" "Illegal to carry without a licence. Expect questions."))])

(defn gear-facts
  "Facts for a weapon, armor or piece of gear."
  [c ix item]
  (vec (remove nil? (concat (case (kind item) "weapon" (weapon-facts c ix item) "armor" (armor-facts item) nil)
                            (carry-facts c ix item)
                            (buy-facts c ix item)))))

(defn- quality-by-name [ix nm]
  (let [k (str/lower-case nm)]
    (some #(when (= k (str/lower-case (:name %))) %) (vals (:qualities ix)))))

(defn qualities
  "A weapon's qualities with their library text: [{:name :rating :activation :text}]. :text is nil when the library
   doesn't have that quality."
  [item ix]
  (for [{nm :name :keys [rating]} (:qualities item) :let [q (quality-by-name ix nm)]]
    {:name nm :rating rating :activation (:activation q) :text (:text q)}))

(defn- bonus-words [{:keys [chars skills wounds soak knowledge]}]
  (concat (for [[k n] chars] (str "+" n " " (r/characteristic-names k)))
          (for [[s n] skills] (str "+" n " rank in " s))
          (when wounds [(str "+" wounds " wound threshold")])
          (when soak [(str "+" soak " soak")])
          (when knowledge ["+1 rank in every Knowledge skill"])))

(defn body-mod-facts
  "What a cybernetic or g-mod does, or nil for ordinary gear: {:kind :effects :note :strain :options}."
  [item]
  (when-let [{:keys [cyber note options] :as m} (mods/body-mod item)]
    {:kind (if cyber "Cybernetic" "G-mod")
     :effects (vec (bonus-words m))
     :note note
     :strain (when cyber "Costs 1 strain threshold, as your body adjusts to the hardware.")
     :options (vec (for [[_ label] options] label))}))

(defn- talent-effect [talent]
  (when-let [e (mods/talent-effects (name (:id talent)))]
    (str/join ", " (for [[k n] e] (str "+" n " " (effect-blurbs k))))))

(defn talent-facts
  "Facts for a talent, as this character would buy it next."
  [c ix {:keys [tier activation ranked] :as talent}]
  (let [owned (r/owned-talent c (name (:id talent)))
        next-tier (r/next-tier talent owned)
        useful (some #(= (name (:id talent)) (name (:id %))) (:useful-talents (r/career c ix)))]
    (vec (remove nil?
                 [(fact "Tier" tier "Higher tiers need more lower-tier talents first: the pyramid.")
                  (fact "Activation" activation (activation-blurbs activation))
                  (fact "Ranked" (if ranked "Yes" "No") (if ranked "You can buy it again, and each rank adds to it." "Bought once."))
                  (when-let [e (talent-effect talent)] (fact "Each rank" e "Already counted on your sheet."))
                  (when owned (fact "You have" (str "Rank " (:rank owned))))
                  (when (or ranked (not owned)) (fact "Next costs" (str (* 5 next-tier) " XP") (str "Bought as a tier " next-tier " talent.")))
                  (when useful (fact "Career" "Suggested" "Your career's book entry recommends it."))]))))
