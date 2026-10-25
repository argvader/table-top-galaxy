(ns ttg.dice
  "The Genesys narrative dice: faces, pool modification, rolling and cancellation (CRB p.10-25).
   Pure data in, data out. Randomness comes in as a seq of uint32s (see ttg.dice.rng), so rolls are replayable."
  (:require [ttg.dice.rng :as rng]))

;; Table I.1-2 (CRB p.10), face 1 first. Checked cell by cell against the book; see docs/rules/dice-and-checks.md.
(def faces
  {:boost       [[] [] [:success] [:success :advantage] [:advantage :advantage] [:advantage]]
   :setback     [[] [] [:failure] [:failure] [:threat] [:threat]]
   :ability     [[] [:success] [:success] [:success :success] [:advantage] [:advantage] [:success :advantage]
                 [:advantage :advantage]]
   :difficulty  [[] [:failure] [:failure :failure] [:threat] [:threat] [:threat] [:threat :threat] [:failure :threat]]
   :proficiency [[] [:success] [:success] [:success :success] [:success :success] [:advantage] [:success :advantage]
                 [:success :advantage] [:success :advantage] [:advantage :advantage] [:advantage :advantage] [:triumph]]
   :challenge   [[] [:failure] [:failure] [:failure :failure] [:failure :failure] [:threat] [:threat]
                 [:failure :threat] [:failure :threat] [:threat :threat] [:threat :threat] [:despair]]})

;; Display and roll order: positive dice strongest first, then negative dice strongest first.
(def die-types [:proficiency :ability :boost :challenge :difficulty :setback])

(def empty-pool (zipmap die-types (repeat 0)))

(defn pool
  "A full pool map from a partial one: (pool {:ability 2 :difficulty 2})."
  [m]
  (merge empty-pool m))

(defn dice-list
  "The pool as a seq of die types, in die-types order."
  [p]
  (mapcat #(repeat (get p % 0) %) die-types))

;; ---------- Modifying a pool (CRB p.20-23) ----------

(def ^:private ladder {:ability [:ability :proficiency] :difficulty [:difficulty :challenge]})

(defn- upgrade-one
  "Upgrades one die; with none left to upgrade, adds the base die instead (p.22)."
  [p [lo hi]]
  (if (pos? (get p lo 0))
    (-> p (update lo dec) (update hi (fnil inc 0)))
    (update p lo (fnil inc 0))))

(defn- downgrade-one
  "Downgrades one die; with none left to downgrade, does nothing (p.22)."
  [p [lo hi]]
  (if (pos? (get p hi 0))
    (-> p (update hi dec) (update lo (fnil inc 0)))
    p))

(defn- times [f]
  (fn [p side n] (nth (iterate #(f % (ladder side)) p) n)))

(def upgrade
  "(upgrade pool :ability n) or (upgrade pool :difficulty n)."
  (times upgrade-one))

(def downgrade
  "(downgrade pool :ability n) or (downgrade pool :difficulty n)."
  (times downgrade-one))

(defn add-dice [p m] (merge-with + (pool p) m))

(defn remove-dice
  "Removes up to n dice of each type; asking for more than the pool has removes them all (p.23)."
  [p m]
  (merge-with (fn [have n] (max 0 (- have n))) (pool p) m))

(defn modify
  "Applies modifications in the book's order: add, then upgrade, then downgrade, then remove (p.20, 23).
   mods: {:add {:boost 1} :upgrade {:ability 1 :difficulty 1} :downgrade {...} :remove {:setback 2}}"
  [p mods]
  (let [each-side (fn [p f m] (reduce-kv f p m))]
    (-> (pool p)
        (add-dice (:add mods))
        (each-side upgrade (:upgrade mods))
        (each-side downgrade (:downgrade mods))
        (remove-dice (:remove mods)))))

;; ---------- Results (CRB p.23-25) ----------

(defn face-value
  "[net-success net-advantage triumph despair] for a seq of symbols. Triumph is also a success, despair also a failure."
  [symbols]
  (let [{:keys [success failure advantage threat triumph despair]
         :or {success 0 failure 0 advantage 0 threat 0 triumph 0 despair 0}} (frequencies symbols)]
    [(- (+ success triumph) failure despair) (- advantage threat) triumph despair]))

(defn result
  "Cancels the rolled symbols. Net success above 0 succeeds; triumph and despair never cancel each other."
  [dice]
  (let [[s a t d] (face-value (mapcat :symbols dice))]
    {:success? (pos? s) :net-success s :net-advantage a :triumph t :despair d}))

(defn- roll-die [type u]
  (let [i (rng/pick u (count (faces type)))]
    {:type type :face i :symbols (get-in faces [type i])}))

(defn roll
  "Rolls a pool. ints is a seq of uint32s (ttg.dice.rng/seeded or /secure); defaults to secure."
  ([p] (roll p (rng/secure)))
  ([p ints]
   (let [dice (mapv roll-die (dice-list p) ints)]
     {:pool (pool p) :dice dice :result (result dice)})))
