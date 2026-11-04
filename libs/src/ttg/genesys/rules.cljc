(ns ttg.genesys.rules
  "Genesys character creation rules, ported from the approved builder prototype (prototypes/src/character-builder.js).
   Pure functions of a character and an index of the library. Purchases are checked by the *-block functions, which
   return nil when allowed and otherwise the reason to show. Page references are to the Genesys Core Rulebook.

   The character (ttg.genesys.character) names library entries by string id, so it survives JSON. Skills are passed
   around as library entries: careers list skills by name, and purchases are stored by id.

   Two values for each characteristic and skill: the creation value (characteristic, rank-of), which XP prices and
   limits are figured from, and the total (characteristic-total, rank-total), which adds Dedication and cybernetics
   and is what the sheet shows and rolls."
  (:require [clojure.string :as str]
            [ttg.genesys.mods :as mods]))

(def characteristics [:brawn :agility :intellect :cunning :willpower :presence])
(def characteristic-names {:brawn "Brawn" :agility "Agility" :intellect "Intellect"
                           :cunning "Cunning" :willpower "Willpower" :presence "Presence"})
(def characteristic-short {:brawn "Br" :agility "Ag" :intellect "Int" :cunning "Cun" :willpower "Will" :presence "Pr"})
(def skill-groups ["General" "Combat" "Social" "Knowledge"])
(def motive-types ["Desire" "Fear" "Strength" "Flaw"])
(def steps [{:id :archetype :label "Archetype"} {:id :career :label "Career"} {:id :obligation :label "Obligation"}
            {:id :xp :label "Spend XP"} {:id :gear :label "Gear"} {:id :story :label "Motivations"} {:id :review :label "Review"}])
(def magnitudes [5 10 15])
(def career-picks-allowed 4)
(def max-rank 2)        ; at creation, p.44
(def max-characteristic 5)
(def start-credits 500) ; p.51

(defn- by-id [entries] (into {} (map (juxt (comp name :id) identity)) entries))

(defn index
  "The parts of a {type [entry]} library the rules read, keyed by string id."
  [{:keys [archetypes careers skills talents weapons armor gear motivations obligation-types crits item-qualities]}]
  {:archetypes (by-id archetypes) :careers (by-id careers) :talents (by-id talents) :qualities (by-id item-qualities)
   :gear (by-id (concat weapons armor gear))
   :skills (vec skills) :motivations (vec motivations) :obligations (vec obligation-types)
   :crits (vec (sort-by (comp first :roll) crits))})

(defn blank-character []
  {:name "" :tagline "" :archetype nil :career nil :career-picks [] :arch-picks []
   :char-buy {} :skill-buy {} :talents [] :gear [] :free-item nil :free-picks [] :pocket nil :s0 ""
   :motives (into {} (map (fn [t] [(keyword (str/lower-case t)) {:name "" :text ""}])) motive-types)
   :ob {:type nil :magnitude 10 :attachment "" :why ""}
   :wounds-taken 0 :strain-taken 0 :earned-xp 0 :notes "" :crits []
   :adjust {} :adjust-note ""})

;; ---------- Critical injuries (Table I.6-10, CRB p.115) ----------

(defn crit-bonus
  "Each critical injury a character already has adds 10 to the next roll."
  [c]
  (* 10 (count (:crits c))))

(defn crit-for
  "The injury a d100 total lands on. The table's last row has no upper end."
  [ix total]
  (some (fn [{[lo hi] :roll :as crit}] (when (and (>= total lo) (or (nil? hi) (<= total hi))) crit)) (:crits ix)))

(defn crit-entry
  "The library's entry for an injury on a character, keeping what the character recorded (the total rolled, the date)."
  [ix {:keys [id] :as injury}]
  (merge (some #(when (= (name id) (name (:id %))) %) (:crits ix)) injury))

(defn motive-key [type] (keyword (str/lower-case type)))

(defn archetype [c ix] (get-in ix [:archetypes (:archetype c)]))
(defn career [c ix] (get-in ix [:careers (:career c)]))
(defn obligation [c ix] (first (filter #(= (some-> % :id name) (get-in c [:ob :type])) (:obligations ix))))
(defn- skill-id [skill] (keyword (name (:id skill))))
(defn- skill-characteristic [skill] (keyword (:characteristic skill)))

(defn base-characteristic [c ix k] (if-let [a (archetype c ix)] (get-in a [:characteristics k]) 2))
(defn characteristic [c ix k] (+ (base-characteristic c ix k) (get-in c [:char-buy k] 0)))
(defn characteristics-of [c ix] (into {} (map (juxt identity #(characteristic c ix %))) characteristics))

(defn career-skill? [c ix skill] (boolean (some #{(:name skill)} (:skills (career c ix)))))

(defn- arch-free-ranks [c ix skill]
  (let [{:keys [ranks skills choose]} (:free-skills (archetype c ix))]
    (+ (if (some #{(:name skill)} skills) ranks 0)
       (if (and choose (some #{(:name skill)} (:arch-picks c))) ranks 0))))

(defn free-ranks [c ix skill]
  (+ (if (some #{(:name skill)} (:career-picks c)) 1 0) (arch-free-ranks c ix skill)))

(defn bought-ranks [c skill] (get-in c [:skill-buy (skill-id skill)] 0))
(defn rank-of [c ix skill] (+ (free-ranks c ix skill) (bought-ranks c skill)))
(defn rank-price [c ix skill rank] (+ (* 5 rank) (if (career-skill? c ix skill) 0 5)))

(defn- sum-range [lo hi f] (reduce + (map f (range lo (inc hi)))))

(defn characteristics-xp [c ix]
  (reduce + (map #(sum-range (inc (base-characteristic c ix %)) (characteristic c ix %) (partial * 10)) characteristics)))

(defn skills-xp [c ix]
  (reduce + (map #(sum-range (inc (free-ranks c ix %)) (rank-of c ix %) (partial rank-price c ix %)) (:skills ix))))

(defn- talent-def [ix t] (get-in ix [:talents (:id t)]))

(defn slot-tiers
  "A ranked talent buys each further rank one tier higher, capped at tier 5 (p.72)."
  [ix t]
  (mapv #(min 5 (+ (:tier (talent-def ix t)) %)) (range (:rank t))))

(defn tier-counts [ix talents]
  (reduce (fn [counts tier] (update counts tier inc)) [0 0 0 0 0 0] (mapcat #(slot-tiers ix %) talents)))

(defn pyramid-ok? [counts]
  (every? #(or (zero? (counts %)) (> (counts (dec %)) (counts %))) [2 3 4 5]))

(defn talents-xp [c ix]
  (reduce + (map #(* 5 %) (mapcat #(slot-tiers ix %) (:talents c)))))

(defn obligation-xp [c] (if (get-in c [:ob :type]) (get-in c [:ob :magnitude]) 0))
(defn budget [c ix] (+ (:xp (archetype c ix) 0) (obligation-xp c)))
(defn spent-xp [c ix] (+ (characteristics-xp c ix) (skills-xp c ix) (talents-xp c ix)))
(defn xp-left [c ix] (- (budget c ix) (spent-xp c ix)))

(defn gm?
  "The campaign's GM editing a player's character: they may break the creation rules, and each break is flagged."
  [c]
  (= "gm" (:access c)))

(defn owned-talent [c id] (first (filter #(= id (:id %)) (:talents c))))

(defn- talent-mod [c k]
  (reduce + (map #(* (:rank %) (get-in mods/talent-effects [(:id %) k] 0)) (:talents c))))

(defn owned-gear [c ix]
  (keep (fn [{:keys [id qty picks]}] (some-> (get-in ix [:gear id]) (assoc :qty qty :picks (vec picks)))) (:gear c)))

(defn carried
  "Bought gear, plus the archetype's free item."
  [c ix]
  (cond-> (vec (owned-gear c ix))
    (get-in ix [:gear (:free-item c)])
    (conj (assoc (get-in ix [:gear (:free-item c)]) :qty 1 :free true :picks (vec (:free-picks c))))))

(defn- kind [item] (some-> (:kind item) name))

(defn best-armor [c ix]
  (->> (carried c ix) (filter #(= "armor" (kind %))) (sort-by #(- (+ (:soak % 0) (:defense % 0)))) first))

(defn credits-left [c ix]
  (- start-credits (reduce + (map #(* (or (:price %) 0) (:qty %)) (owned-gear c ix)))))

(defn- item-encumbrance
  "Worn armor counts 3 less, never below 0 (p.92)."
  [worn item]
  (* (:qty item) (cond-> (or (:encumbrance item) 0)
                   (= (:id item) (:id worn)) (-> (- 3) (max 0)))))

;; ---------- Bonuses from talents and body modifications ----------

(defn installs [c ix] (mods/installs (carried c ix)))
(defn- bioroid? [c ix] (= "Bioroid" (:name (archetype c ix))))
(defn- knowledge-skills [ix] (map :name (filter #(= "Knowledge" (:category %)) (:skills ix))))

(defn bonuses
  "Everything Dedication and cybernetics add: {:chars {k n} :skills {name n} :wounds :soak :strain-cost}."
  [c ix]
  (let [ins (installs c ix)]
    (-> (mods/cyber-bonus ins (knowledge-skills ix))
        (update :chars #(merge-with + % (mods/dedication-bonus (owned-talent c mods/dedication))))
        (assoc :strain-cost (mods/strain-cost ins (bioroid? c ix))))))

(defn characteristic-total [c ix k] (+ (characteristic c ix k) (get-in (bonuses c ix) [:chars k] 0)))
(defn rank-total [c ix skill] (+ (rank-of c ix skill) (get-in (bonuses c ix) [:skills (:name skill)] 0)))

(defn- adjust [c k] (let [n (get-in c [:adjust k])] (if (number? n) n 0)))

(defn- parts
  "[label n] rows that add up to a derived value; zero rows other than the base are dropped."
  [base & rows]
  (into [base] (remove (comp zero? second)) rows))

(defn- total [rows] (reduce + (map second rows)))

(defn derived-parts
  "How wounds, strain and soak are made up, for the sheet to explain them."
  [c ix]
  (let [a (archetype c ix) armor (best-armor c ix) b (bonuses c ix)
        brawn (characteristic-total c ix :brawn) will (characteristic-total c ix :willpower)]
    {:wounds (parts ["Archetype" (:wounds a 0)] ["Brawn" brawn] ["Toughened" (talent-mod c :wounds)]
                    ["Cybernetic heart" (:wounds b)] ["GM adjustment" (adjust c :wounds)])
     :strain (parts ["Archetype" (:strain a 0)] ["Willpower" will] ["Grit" (talent-mod c :strain)]
                    ["Cybernetics" (- (:strain-cost b))] ["GM adjustment" (adjust c :strain)])
     :soak (parts ["Brawn" brawn] ["Armor" (or (:soak armor) 0)] ["Enduring" (talent-mod c :soak)]
                  ["Sub-dermal armor" (:soak b)] ["GM adjustment" (adjust c :soak)])}))

(defn derived
  "Thresholds, soak, defense (capped at 4, p.104, before any GM adjustment) and encumbrance. The best armor carried
   is the one worn."
  [c ix]
  (let [a (archetype c ix) armor (best-armor c ix) items (carried c ix) p (derived-parts c ix)
        brawn (characteristic-total c ix :brawn)
        bonus (reduce + (map #(or (:encumbrance-bonus %) 0) items))]
    {:wounds (if a (total (:wounds p)) 0)
     :strain (if a (total (:strain p)) 0)
     :soak (total (:soak p))
     :defense (+ (min 4 (+ (or (:defense armor) 0) (talent-mod c :defense))) (adjust c :defense))
     :encumbrance (reduce + (map #(item-encumbrance armor %) items))
     :encumbrance-max (+ (if (owned-talent c mods/big-guns) 10 5) brawn bonus)
     :encumbrance-bonus bonus}))

(defn skill-pool
  "The higher of characteristic and rank gives the dice; the lower number of them are upgraded to Proficiency."
  [c ix skill]
  (let [ch (characteristic-total c ix (skill-characteristic skill)) rk (rank-total c ix skill)]
    {:proficiency (min ch rk) :ability (- (max ch rk) (min ch rk))}))

;; ---------- Checks: what the sheet hands the roller ----------

(defn encumbrance-setback
  "One Setback per point over the threshold, on Agility and Brawn checks (p.85)."
  [c ix k]
  (let [{:keys [encumbrance encumbrance-max]} (derived c ix)]
    (if (#{:agility :brawn} k) (max 0 (- encumbrance encumbrance-max)) 0)))

(defn- check [c ix k label detail pool]
  (let [setback (encumbrance-setback c ix k)]
    {:label label
     :detail (cond-> detail (pos? setback) (str " · Encumbered +" setback " setback"))
     :pool (assoc pool :setback setback)}))

(defn- skill-detail [c ix skill]
  (let [k (skill-characteristic skill)]
    (str (characteristic-names k) " " (characteristic-total c ix k) " · Rank " (rank-total c ix skill))))

(defn characteristic-check [c ix k]
  (let [n (characteristic-total c ix k) stat (characteristic-names k)]
    (check c ix k (str stat " check") (str stat " " n ", no skill") {:ability n})))

(defn skill-check [c ix skill]
  (check c ix (skill-characteristic skill) (:name skill) (skill-detail c ix skill) (skill-pool c ix skill)))

(defn weapon-damage [c ix w]
  (if (:adds-brawn w) (+ (characteristic-total c ix :brawn) (:damage-bonus w 0)) (:damage w)))

(defn quality-rating
  "The weapon's rating in a quality, matched by name in any case: 1 for an unrated one, nil when it hasn't it."
  [w quality]
  (some #(when (= (str/lower-case quality) (str/lower-case (:name %))) (or (:rating %) 1)) (:qualities w)))

(defn quality-dice
  "Dice a weapon's qualities add to every attack with it (CRB p.86-89): Accurate adds Boost, Inaccurate Setback."
  [w]
  (cond-> {}
    (quality-rating w "Accurate") (assoc :boost (quality-rating w "Accurate"))
    (quality-rating w "Inaccurate") (assoc :setback (quality-rating w "Inaccurate"))))

(def ^:private heavy-qualities [["Cumbersome" :brawn] ["Unwieldy" :agility]])

(defn weapon-difficulty
  "Qualities that make an attack harder because a characteristic is too low (CRB p.87, p.89): one more Difficulty
   per point short. [{:quality :rating :characteristic :have :extra}] for each one that applies."
  [c ix w]
  (for [[q k] heavy-qualities :let [rating (quality-rating w q) have (characteristic-total c ix k)]
        :when (and rating (< have rating))]
    {:quality q :rating rating :characteristic k :have have :extra (- rating have)}))

(defn weapon-check
  "nil when the library has no skill by the weapon's name. The pool carries the weapon's Accurate and Inaccurate dice;
   the difficulty is set in the roller, so weapon-difficulty says what a too-heavy weapon adds to it."
  [c ix w]
  (when-let [skill (first (filter #(= (:skill w) (:name %)) (:skills ix)))]
    (-> (check c ix (skill-characteristic skill) (str (:name w) " attack")
               (str (:name skill) " · " (skill-detail c ix skill)) (skill-pool c ix skill))
        (update :pool #(merge-with + % (quality-dice w)))
        (assoc :weapon {:range (:range w) :crit (:crit w) :damage (weapon-damage c ix w)}))))

;; ---------- Purchase checks: nil when allowed, otherwise the reason ----------

(defn- short-of [c ix cost]
  (when (> cost (xp-left c ix)) (str "Needs " cost " XP, you have " (max 0 (xp-left c ix)))))

(defn characteristic-up-block [c ix k]
  (cond (not (archetype c ix)) "Choose an archetype first"
        (>= (characteristic c ix k) max-characteristic) (str max-characteristic " is the most")
        :else (short-of c ix (* 10 (inc (characteristic c ix k))))))

(defn skill-up-block [c ix skill]
  (let [rk (rank-of c ix skill)]
    (if (>= rk max-rank)
      (str "Rank " max-rank " is the most at creation")
      (short-of c ix (rank-price c ix skill (inc rk))))))


(defn next-tier [talent owned] (if owned (min 5 (+ (:tier talent) (:rank owned))) (:tier talent)))

(defn talent-buy-block [c ix talent]
  (let [owned (owned-talent c (name (:id talent)))
        tier (next-tier talent owned)
        counts (tier-counts ix (:talents c))]
    (cond (and owned (not (:ranked talent))) "Already owned"
          (and (> tier 1) (not (> (counts (dec tier)) (inc (counts tier)))))
          (str "Needs " (+ 2 (counts tier)) " tier " (dec tier) " talents first (you have " (counts (dec tier)) ")")
          :else (short-of c ix (* 5 tier)))))

(defn- without-rank [talents id]
  (filterv #(pos? (:rank %)) (map #(if (= id (:id %)) (update % :rank dec) %) talents)))

(defn talent-drop-block [c ix id]
  (when-not (pyramid-ok? (tier-counts ix (without-rank (:talents c) id)))
    (str "Remove a tier " (inc (peek (slot-tiers ix (owned-talent c id)))) " talent first")))

(defn free-pick-block [c ix skill extra]
  (when (> (+ (free-ranks c ix skill) extra) max-rank)
    (str "Would put " (:name skill) " above rank " max-rank)))

;; ---------- Changes ----------

(defn choose-archetype
  "Purchases stay and are repriced; picks tied to the old archetype go."
  [c ix id]
  (if (= id (:archetype c))
    c
    (let [c (assoc c :archetype id :arch-picks [])
          group (get-in (archetype c ix) [:free-item :group])]
      (cond-> c
        (and (:free-item c) (or (nil? group) (not= group (get-in ix [:gear (:free-item c) :group]))))
        (assoc :free-item nil)))))

(defn choose-career [c ix id]
  (if (= id (:career c))
    c
    (let [c (assoc c :career id :career-picks [])]
      (cond-> c
        (get-in (archetype c ix) [:free-skills :non-career])
        (update :arch-picks (fn [picks] (filterv #(not (some #{%} (:skills (career c ix)))) picks)))))))

(defn toggle [items x] (if (some #{x} items) (filterv #(not= x %) items) (conj (vec items) x)))

(defn add-talent [c id]
  (if (owned-talent c id)
    (update c :talents (fn [ts] (mapv #(if (= id (:id %)) (update % :rank inc) %) ts)))
    (update c :talents conj {:id id :rank 1})))

(defn remove-talent [c id] (update c :talents without-rank id))

(defn single-item?
  "Weapons and armor are bought once; ordinary gear stacks."
  [item]
  (not= "gear" (kind item)))

(defn gear-buy-block [c ix item]
  (let [have (some #(= (name (:id item)) (:id %)) (:gear c))]
    (cond (and have (single-item? item)) "Bought"
          (> (or (:price item) 0) (credits-left c ix)) "Not enough credits")))

(defn add-gear [c id]
  (if (some #(= id (:id %)) (:gear c))
    (update c :gear (fn [gs] (mapv #(if (= id (:id %)) (update % :qty inc) %) gs)))
    (update c :gear conj {:id id :qty 1})))

(defn remove-gear [c id]
  (update c :gear (fn [gs] (filterv #(pos? (:qty %)) (map #(if (= id (:id %)) (update % :qty dec) %) gs)))))

;; ---------- Choices: which limb or organ, and Dedication's characteristics ----------

(defn- put-at [v i x]
  (let [v (vec v)] (assoc (into v (repeat (max 0 (- (inc i) (count v))) nil)) i x)))

(defn set-pick
  "Picks what one unit of a body modification is. item is as carried: :free for the archetype's free item."
  [c item unit pick]
  (if (:free item)
    (update c :free-picks put-at unit pick)
    (let [id (name (:id item))]
      (update c :gear (fn [gs] (mapv #(if (= id (:id %)) (update % :picks put-at unit pick) %) gs))))))

(defn dedication-options
  "The characteristics a Dedication rank could raise: not one another rank has, and not one already at 5."
  [c ix rank-i]
  (let [picks (vec (:picks (owned-talent c mods/dedication)))
        others (set (keep-indexed #(when (not= rank-i %1) %2) picks))]
    (filterv #(or (= (name %) (get picks rank-i))
                  (and (not (others (name %))) (< (characteristic-total c ix %) max-characteristic)))
             characteristics)))

(defn set-dedication
  "k is a characteristic, stored by name so it survives JSON; nil clears the rank's pick."
  [c rank-i k]
  (update c :talents (fn [ts] (mapv #(if (= mods/dedication (:id %)) (update % :picks put-at rank-i (some-> k name)) %) ts))))

(defn unchosen
  "What still needs a choice before its bonus counts."
  [c ix]
  (let [{:keys [rank picks]} (owned-talent c mods/dedication)]
    (cond-> (vec (mods/unchosen (installs c ix)))
      (and rank (< (count (remove nil? (take rank picks))) rank)) (conj "Dedication"))))

;; ---------- Validation ----------

(defn- plural [n word] (str n " " word (when (not= 1 n) "s")))

(defn- identity-issues [c ix]
  (let [a (archetype c ix) picks (count (:career-picks c)) {:keys [choose]} (:free-skills a)
        left (- (or choose 0) (count (:arch-picks c)))]
    [(when-not a [0 :block "Choose an archetype"])
     (cond (not (career c ix)) [1 :block "Choose a career"]
           (< picks career-picks-allowed) [1 :block (str "Pick " (plural (- career-picks-allowed picks) "more career skill") " for a free rank")])
     (when (pos? left) [1 :block (str "Pick " (plural left "more skill") " for your " (:name a) " free ranks")])]))

(defn- obligation-issues [{:keys [ob]}]
  [(cond (not (:type ob)) [2 :block "Pick or roll a Secret or Obligation"]
         (str/blank? (:attachment ob)) [2 :block "Say who or what your Obligation ties you to"]
         (str/blank? (:why ob)) [2 :warn "Say why your Obligation matters to your character"])])

(defn- xp-issues [c ix]
  (let [a (archetype c ix) left (xp-left c ix)]
    (concat
     [(when (and a (neg? left)) [3 :block (str "Over the XP budget by " (- left))])]
     (for [k characteristics :let [v (characteristic c ix k)] :when (> v max-characteristic)]
       [3 :block (str (characteristic-names k) " is " v ". The most is " max-characteristic) :chars])
     (for [s (:skills ix) :let [r (rank-of c ix s)] :when (> r max-rank)]
       [3 :block (str (:name s) " is rank " r ". The most at creation is " max-rank) :skills])
     [(when-not (pyramid-ok? (tier-counts ix (:talents c))) [3 :block "Talent pyramid is broken" :talents])
      (when (and a (pos? left)) [3 :warn (str left " XP unspent. You keep it, but characteristics can only be bought now")])])))

(defn free-item-label [a] (if (= "G-Mods" (get-in a [:free-item :group])) "g-mod" "cybernetic"))

(defn free-item-over
  "How many credits the chosen free item costs beyond the archetype's cap, or nil when it fits."
  [c ix]
  (let [cap (get-in (archetype c ix) [:free-item :max-price])
        price (get-in ix [:gear (:free-item c) :price])]
    (when (and cap price (> price cap)) (- price cap))))

(defn- gear-issues [c ix]
  (let [a (archetype c ix) {:keys [encumbrance encumbrance-max]} (derived c ix) over (- encumbrance encumbrance-max)]
    [(when (neg? (credits-left c ix)) [4 :block (str "Over budget by " (- (credits-left c ix)) " credits")])
     (when (and (:free-item a) (not (:free-item c))) [4 :warn (str "Choose your free " (free-item-label a) " (" (:name a) ")")])
     (when-let [over (free-item-over c ix)]
       [4 :block (str "Your free " (free-item-label a) " costs " over " credits more than the " (get-in a [:free-item :max-price]) " allowed")])
     (when (empty? (:gear c)) [4 :warn "No gear bought yet"])
     (when (nil? (:pocket c)) [4 :warn "Pocket money not rolled yet"])
     (when (pos? over) [4 :warn (str "Encumbered by " over ": Brawn and Agility checks add " over " Setback")])
     (when-let [names (seq (unchosen c ix))]
       [4 :warn (str "Choose what your " (str/join " and " names) " improves, on the character sheet")])]))

(defn- story-issues [c]
  (let [blank (count (filter #(str/blank? (:name %)) (vals (:motives c))))]
    [(when (str/blank? (:name c)) [5 :block "Give your character a name"])
     (when (pos? blank) [5 :warn (str (plural blank "motivation") " left blank")])
     (when (str/blank? (:s0 c)) [5 :warn "Session 0 question not answered yet"])]))

(defn issues
  "Every problem as {:step :level :text :tab}: blocks first, then warnings, each in step order."
  [c ix]
  (->> (concat (identity-issues c ix) (obligation-issues c) (xp-issues c ix) (gear-issues c ix) (story-issues c))
       (remove nil?)
       (map (fn [[step level text tab]] (cond-> {:step step :level level :text text} tab (assoc :tab tab))))
       (sort-by (juxt #(if (= :block (:level %)) 0 1) :step))
       vec))

(defn blocks [issues] (filterv #(= :block (:level %)) issues))

(defn in-roll? [n [lo hi]] (<= (or lo 0) n (or hi 100)))

(defn initials [character-name]
  (let [letters (map first (remove str/blank? (str/split (str/trim (or character-name "")) #"\s+")))]
    (if (seq letters) (str/upper-case (apply str (take 2 letters))) "?")))
