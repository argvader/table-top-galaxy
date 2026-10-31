(ns ttg.library.fields
  "The fields a GM edits for each library type. One schema drives the editor form, CSV import and CSV export,
   so a column means the same thing everywhere. Every field reads and writes a string (a form input or a CSV
   cell); ttg.library.io parses them.

   Field keys:
     :path    where the value lives in the entry
     :label   form label and CSV header
     :kind    :text :long :int :bool :choice :list :qualities :abilities
     :blank   what an empty cell means: :omit (drop the key), :nil (keep the key, value nil) or :required
     :min :max for :int; :options for :choice (and suggestions for :text)"
  (:require [clojure.string :as str]
            [ttg.genesys.model :as model]))

(def activations
  ["Passive" "Active (Action)" "Active (Maneuver)" "Active (Incidental)" "Active (Incidental, Out of Turn)"])

(def ranges ["Engaged" "Short" "Medium" "Long" "Extreme" "Strategic"])

(def weapon-skills ["Brawl" "Melee" "Ranged (Light)" "Ranged (Heavy)" "Gunnery" "Ranged"])

(defn- f [path label kind & {:as opts}]
  (merge {:path path :label label :kind kind :blank :omit} opts))

(def ^:private name-field (f [:name] "Name" :text :blank :required))

(def ^:private cost-fields
  [(f [:price] "Price" :int :min 0 :blank :nil)
   (f [:rarity] "Rarity" :int :min 0 :max 10 :blank :nil)
   (f [:restricted] "Restricted" :bool)])

(def ^:private catalog-fields
  [(f [:setting] "Setting" :text :blank :required :options ["android" "general"])
   (f [:group] "Group" :text)])

(def by-type
  {:talents
   [name-field
    (f [:tier] "Tier" :int :min 1 :max 5 :blank :required)
    (f [:activation] "Activation" :choice :options activations :blank :required)
    (f [:ranked] "Ranked" :bool)
    (f [:text] "Text" :long :blank :required)
    (f [:settings] "Setting note" :text)]

   :item-qualities
   [name-field
    (f [:activation] "Activation" :choice :options ["passive" "active"] :blank :required)
    (f [:ranked] "Ranked" :bool)
    (f [:text] "Text" :long :blank :required)]

   :weapons
   (into [name-field
          (f [:skill] "Skill" :text :blank :required :options weapon-skills)
          (f [:damage] "Damage" :int :min 0)
          (f [:damage-bonus] "Damage bonus" :int :min 0)
          (f [:adds-brawn] "Adds Brawn" :bool)
          (f [:crit] "Crit" :int :min 0 :blank :nil)
          (f [:range] "Range" :choice :options ranges :blank :required)
          (f [:encumbrance] "Encumbrance" :int)
          (f [:hard-points] "Hard points" :int :min 0)
          (f [:qualities] "Qualities" :qualities)]
         (concat cost-fields catalog-fields))

   :armor
   (into [name-field
          (f [:defense] "Defense" :int :min 0 :blank :required)
          (f [:soak] "Soak" :int :min 0 :blank :required)
          (f [:encumbrance] "Encumbrance" :int)
          (f [:hard-points] "Hard points" :int :min 0)]
         (concat cost-fields catalog-fields))

   :gear
   (into [name-field
          (f [:encumbrance] "Encumbrance" :int)
          (f [:encumbrance-bonus] "Encumbrance bonus" :int :min 1)]
         (concat cost-fields catalog-fields))

   :attachments
   (into [name-field (f [:hard-points] "Hard points" :int :min 0)] (concat cost-fields catalog-fields))

   :archetypes
   (into [name-field]
         (concat (for [c model/characteristics]
                   (f [:characteristics c] (str/capitalize (name c)) :int :min 1 :max 6 :blank :required))
                 [(f [:wounds] "Wounds" :int :min 0 :blank :required)
                  (f [:strain] "Strain" :int :min 0 :blank :required)
                  (f [:xp] "Starting XP" :int :min 0 :blank :required)
                  (f [:starting-skills] "Starting skills" :text)
                  (f [:abilities] "Abilities" :abilities)
                  (f [:text] "Notes" :long)]))

   :careers
   [name-field
    (f [:skills] "Career skills" :list :count 8 :blank :required)
    (f [:text] "Notes" :long)]})

(def editable-types
  "Library types the editor shows, in sidebar order. Other types (skills, crits, ...) still import and store."
  [{:type :talents :label "Talents" :one "talent"}
   {:type :weapons :label "Weapons" :one "weapon"}
   {:type :armor :label "Armor" :one "armor"}
   {:type :gear :label "Gear" :one "gear item"}
   {:type :attachments :label "Attachments" :one "attachment"}
   {:type :item-qualities :label "Item qualities" :one "item quality"}
   {:type :archetypes :label "Archetypes" :one "archetype"}
   {:type :careers :label "Careers" :one "career"}])

(def type-info (into {} (map (juxt :type identity)) editable-types))

;; Values a new entry starts with, before the GM types anything.
(def defaults
  {:talents {:tier 1 :activation "Passive" :ranked false :text ""}
   :item-qualities {:activation "passive" :ranked false :text ""}
   :weapons {:skill "Ranged (Light)" :damage 0 :range "Short" :crit nil :price nil :rarity nil :qualities [] :setting "android"}
   :armor {:defense 0 :soak 0 :price nil :rarity nil :setting "android"}
   :gear {:price nil :rarity nil :setting "android"}
   :attachments {:price nil :rarity nil :setting "android"}
   :archetypes {:characteristics (zipmap model/characteristics (repeat 2)) :wounds 10 :strain 10 :xp 100 :abilities []}
   :careers {:skills []}})
