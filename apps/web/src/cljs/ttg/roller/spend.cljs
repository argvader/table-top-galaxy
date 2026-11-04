(ns ttg.roller.spend
  "Spending Advantage/Triumph and Threat/Despair from a roll (CRB p.102-104).
   Options are short paraphrases; once the content library lands (M3) they come from the GM's library instead.
   An option with an :effect changes the character when it's spent from a check on their sheet ({:strain -1} is
   1 strain recovered); undoing the spend undoes the effect.")

(def options
  {:advantage [{:cost 1 :label "Recover 1 strain" :effect {:strain -1}}
               {:cost 1 :label "Add a Boost die to an ally's next check"}
               {:cost 1 :label "Notice a useful detail in the scene"}
               {:cost 2 :label "Take a free maneuver"}
               {:cost 2 :label "Add a Setback die to the target's next check"}
               {:cost 3 :label "Negate the target's defense until the round ends"}
               {:cost 3 :label "Ignore cover or difficult terrain until your next turn"}
               {:cost :big :label "Upgrade the difficulty of the target's next check"}
               {:cost :big :label "Do something vital: blow the door, cut the lights, end the chase"}]
   :threat [{:cost 1 :label "Suffer 1 strain" :effect {:strain 1}}
            {:cost 1 :label "Lose the benefit of a prior maneuver"}
            {:cost 2 :label "An opponent takes a free maneuver"}
            {:cost 2 :label "Add a Boost die to the target's next check"}
            {:cost 3 :label "Fall prone or drop what you're holding"}
            {:cost :big :label "Weapon runs dry or jams until repaired"}
            {:cost :big :label "Upgrade the difficulty of your next check"}]})

;; :big is Triumph on the Advantage side and Despair on the Threat side.
(def big-symbol {:advantage :triumph :threat :despair})

(defn- paid [spent k] (reduce + (map k spent)))

(defn remaining
  "What is left to spend on one side of a roll: {:small n :big n}."
  [{:keys [net-advantage triumph despair]} spent side]
  (let [small (if (= side :advantage) (max 0 net-advantage) (max 0 (- net-advantage)))
        big (if (= side :advantage) triumph despair)]
    {:small (- small (paid spent :paid-small)) :big (- big (paid spent :paid-big))}))

(defn payment
  "How an option gets paid from what's left, or nil. Advantage pays first; one Triumph covers any option (p.103)."
  [{:keys [cost]} {:keys [small big]}]
  (cond
    (= cost :big) (when (pos? big) {:paid-small 0 :paid-big 1})
    (>= small cost) {:paid-small cost :paid-big 0}
    (pos? big) {:paid-small 0 :paid-big 1}))

(defn spend
  "Spends option i on one side of a logged roll; an unaffordable option leaves the roll unchanged."
  [roll side i]
  (let [opt (get-in options [side i])
        pay (payment opt (remaining (:result roll) (get-in roll [:spent side]) side))]
    (cond-> roll
      pay (update-in [:spent side] (fnil conj []) (merge (select-keys opt [:label :effect]) pay)))))

(defn inverse
  "What undoes an effect: {:strain -1} -> {:strain 1}."
  [effect]
  (update-vals effect -))

(defn undo [roll side]
  (update-in roll [:spent side] #(if (seq %) (pop %) [])))
