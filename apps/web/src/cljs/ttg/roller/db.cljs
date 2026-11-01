(ns ttg.roller.db
  "The roller's slice of app-db: the pools being built and the log of rolls (newest first). :mode says which pool
   the Roll button throws: the campaign's narrative dice, or standard numbered dice. :check is the check open in a
   character sheet's roll drawer, which uses the same pool and log; :before is what the Dice roller page gets back,
   and :closing? means the drawer is on its way off screen."
  (:require [clojure.spec.alpha :as s]
            [ttg.dice :as dice]
            [ttg.dice.model :as dm]))

;; Difficulty ladder (CRB p.18); the ranged band each step stands for (p.102).
(def ladder
  [{:name "Simple" :n 0} {:name "Easy" :n 1 :range "Short"} {:name "Average" :n 2 :range "Medium"}
   {:name "Hard" :n 3 :range "Long"} {:name "Daunting" :n 4 :range "Extreme"} {:name "Formidable" :n 5}])

(def default-ladder 2)
(def max-per-type 9)
;; More 3D dice than this can't all lie flat on a phone, and the failed throws freeze the page while they're tried.
(def max-on-table 10)
(def log-size 25)

(defn ranged?
  "An attack whose difficulty comes from the range to its target (p.102), so the ladder shows the bands."
  [{:keys [range]}]
  (boolean (and range (not= "Engaged" range))))

(s/def ::mode #{:campaign :plain})
(s/def ::pool ::dm/pool)
(s/def ::plain-pool ::dm/plain-pool)
(s/def ::ladder (s/int-in 0 (count ladder)))
(s/def ::reset-note? boolean?)
(s/def ::next-id pos-int?)
(s/def ::shown (s/nilable pos-int?))
(s/def ::rolling (s/nilable pos-int?))   ; id of the roll the 3D dice are still acting out

(s/def ::id pos-int?)
(s/def ::seed nat-int?)
(s/def ::label string?)
(s/def ::paid-small nat-int?)
(s/def ::paid-big nat-int?)
(s/def ::spend (s/keys :req-un [::label ::paid-small ::paid-big]))
(s/def ::advantage (s/coll-of ::spend :kind vector?))
(s/def ::threat (s/coll-of ::spend :kind vector?))
(s/def ::spent (s/keys :opt-un [::advantage ::threat]))
(s/def ::detail string?)
(s/def ::weapon map?)
(s/def ::before (s/keys :req-un [::mode ::pool ::ladder]))
(s/def ::closing? boolean?)
(s/def ::check (s/nilable (s/keys :req-un [::label ::detail ::pool ::before] :opt-un [::weapon ::closing?])))
(s/def ::campaign-roll (s/merge ::dm/roll (s/keys :req-un [::id ::seed ::label ::spent])))
(s/def ::plain-roll (s/merge ::dm/plain-roll (s/keys :req-un [::id ::seed ::label])))
(s/def ::roll (s/or :campaign ::campaign-roll :plain ::plain-roll))
(s/def ::rolls (s/coll-of ::roll :kind vector? :max-count log-size))

(s/def ::app-db (s/keys :req-un [::mode ::pool ::plain-pool ::ladder ::check ::reset-note? ::next-id ::shown ::rolling ::rolls]))

(def drawer-out-ms
  "How long the drawer takes to leave. Matches the drawer-out animations in css/game.css."
  220)

(def default-db
  {:mode :campaign
   :pool (dice/pool {:difficulty (:n (ladder default-ladder))})
   :plain-pool dice/empty-plain-pool
   :ladder default-ladder
   :check nil
   :reset-note? false
   :next-id 1
   :shown nil
   :rolling nil
   :rolls []})
