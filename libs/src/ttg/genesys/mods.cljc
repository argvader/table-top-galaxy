(ns ttg.genesys.mods
  "What changes a character's numbers beyond the archetype and XP: body modifications (Shadow of the Beanstalk
   pp.104-108) and the talents that raise thresholds, soak, defense or a characteristic (Genesys Core Rulebook).

   Body modifications are matched by item name, so a library entry gets its effect whatever its id. An item that
   needs a choice (which limb, which organ) keeps one pick per unit, and Dedication keeps one characteristic per rank.
   Everything here is a pure function of plain data; ttg.genesys.rules does the bookkeeping."
  (:require [clojure.string :as str]))

;; ---------- Talents (CRB) ----------

(def talent-effects
  "Per rank."
  {"toughened" {:wounds 2} "grit" {:strain 1} "enduring" {:soak 1} "defensive" {:defense 1}})

(def big-guns "big-guns")     ; SotB p.49: encumbrance threshold 10 + Brawn instead of 5 + Brawn
(def dedication "dedication") ; CRB p.75: +1 to a different characteristic per rank, never above 5

;; ---------- Body modifications (SotB) ----------

(def ^:private limb-options
  [["brawn-arm" "Arm, +1 Brawn" {:chars {:brawn 1} :limb "arm"}]
   ["agility-arm" "Arm, +1 Agility" {:chars {:agility 1} :limb "arm"}]
   ["brawn-leg" "Leg, +1 Brawn" {:chars {:brawn 1} :limb "leg"}]
   ["agility-leg" "Leg, +1 Agility" {:chars {:agility 1} :limb "leg"}]])

(def ^:private organ-options
  [["heart" "Heart, +2 wound threshold" {:wounds 2}]
   ["lungs" "Lungs, +1 Athletics" {:skills {"Athletics" 1} :note "Five minutes in an unbreathable atmosphere (not vacuum)."}]
   ["liver" "Liver, +1 Resilience" {:skills {"Resilience" 1} :note "Immune to alcohol."}]])

(def body-mods
  "Cybernetics (:cyber) cost 1 strain threshold each, with the exceptions in strain-cost. G-mods change no numbers,
   so they carry only a note of what they let the character do."
  {"Bone Lacing" {:cyber true :note "On a Critical Injury, roll twice and choose the result."}
   "Cybereyes" {:cyber true :skills {"Perception" 1} :note "Removes [setback] from smoke or darkness. Shows PAD data."}
   "Cyberlimb" {:cyber true :options limb-options}
   "Cyberorgan" {:cyber true :options organ-options}
   "Enhanced Hearing" {:cyber true :skills {"Vigilance" 1} :note "Removes [setback] from loud noises and sound-based attacks."}
   "Enhanced Reflexes" {:cyber true :skills {"Coordination" 1 "Vigilance" 1}}
   "Neuro-Regulator" {:cyber true :chars {:willpower 1} :note "At the GM's discretion, [setback] on social checks."}
   "Pain Editor" {:cyber true :note "Once per encounter, ignore Critical Injury penalties on checks until it ends."}
   "Skulljack" {:cyber true :note "A BMI. +1 rank in every Knowledge skill while linked to a computer."}
   "Spinal Modem" {:cyber true :knowledge 1 :note "A computer with two icebreakers and one ice. Can never disconnect from the Network."}
   "Sub-Dermal Armor" {:cyber true :soak 1}
   "Cat Claws" {:note "Suffer 1 strain: unarmed Brawl attacks deal +1 damage with Critical 3 until the encounter ends."}
   "Chameleon Skin" {:note "Suffer 1 strain before a Stealth or disguise check to add [success]."}
   "Enhanced Healing" {:note "Suffer 1 strain before a Resilience check to recover from a Critical Injury to add [success]. Heals 1 more wound when resting."}
   "Enhanced Muscles: Coordination" {:note "Suffer 1 strain before a Coordination or Ranged (Light) check to add [success]."}
   "Enhanced Muscles: Strength" {:note "Suffer 1 strain before an Athletics, Brawl or Melee check to add [success]."}
   "Enhanced Olfactory Receptors" {:note "Suffer 1 strain before a Survival check to add [success]."}
   "Follicle Control" {:note "Suffer 1 strain to shed or regrow hair."}
   "Geno-Sculpted Physique" {:note "Suffer 1 strain before a Charm or Leadership check to add [success]."}
   "Nighteyes" {:note "Suffer 1 strain before a Vigilance or Perception check in the dark to add [success]. In bright light, [success] [threat] instead."}
   "Tooth Buds" {:note "Bites: unarmed Brawl +2 damage and Vicious 1. [boost] to Coercion, [setback] to Charm."}
   "Toxin Filters" {:note "Suffer 1 strain before resisting poison, toxins or addiction to add [success], or to cancel a drug."}})

(defn body-mod [item] (get body-mods (:name item)))
(defn options [item] (:options (body-mod item)))

(defn installs
  "One entry per unit of each body modification carried, with its pick. items are carried gear with :qty and :picks."
  [items]
  (for [item items :when (body-mod item) unit (range (:qty item 1))]
    {:name (:name item) :unit unit :free? (boolean (:free item)) :pick (get (vec (:picks item)) unit)}))

(defn- effect [{:keys [pick] :as install}]
  (let [m (body-mod install)]
    (merge (dissoc m :options) (some (fn [[id _ e]] (when (= id pick) e)) (:options m)))))

(defn cyber-bonus
  "Each characteristic, skill and soak rises by at most 1 from cybernetics, and wounds by at most 2 (pp.104-105).
   knowledge is the names of the Knowledge skills."
  [installs knowledge]
  (let [effs (map effect installs)
        skills (distinct (concat (mapcat (comp keys :skills) effs) (when (some :knowledge effs) knowledge)))]
    {:chars (zipmap (distinct (mapcat (comp keys :chars) effs)) (repeat 1))
     :skills (zipmap skills (repeat 1))
     :wounds (min 2 (reduce + (keep :wounds effs)))
     :soak (min 1 (reduce + (keep :soak effs)))}))

(defn- strain-key
  "What counts as one cybernetic for strain: each organ, arms as one, legs as one, anything else once however many."
  [{:keys [name unit] :as install}]
  (case name
    "Cyberorgan" [name unit]
    "Cyberlimb" [name (or (:limb (effect install)) "unchosen")]
    [name]))

(defn strain-cost
  "Strain threshold lost to cybernetics. Bioroids lose none (p.26), and the cyborg's free one costs nothing (p.28)."
  [installs bioroid?]
  (if bioroid?
    0
    (count (distinct (map strain-key (filter #(and (:cyber (body-mod %)) (not (:free? %))) installs))))))

(defn unchosen
  "Names of the body modifications still waiting for a pick."
  [installs]
  (distinct (keep #(when (and (:options (body-mod %)) (nil? (:pick %))) (:name %)) installs)))

;; ---------- Dedication ----------

(defn dedication-bonus
  "talent is the character's Dedication entry: one pick per rank, each a different characteristic."
  [{:keys [rank picks]}]
  (zipmap (distinct (keep #(some-> % keyword) (take (or rank 0) picks))) (repeat 1)))

(defn label [id] (when id (str/capitalize (name id))))
