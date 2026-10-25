(ns ttg.dice.odds
  "Exact chances for a dice pool, by convolving each die's face distribution.
   Powers the \"chance of success\" preview before a roll."
  (:require [ttg.dice :as dice]))

(defn- die-dist
  "{[net-success net-advantage] probability} for one die. Triumph and despair are left out of the convolution
   (their success and failure parts are counted in net-success); see `odds` for their chances."
  [type]
  (let [fs (dice/faces type)
        p (/ 1.0 (count fs))]
    (reduce #(update %1 (subvec (dice/face-value %2) 0 2) (fnil + 0) p) {} fs)))

(def ^:private die-dists (into {} (map (juxt identity die-dist)) dice/die-types))

(defn- convolve [dist die]
  (persistent!
   (reduce-kv (fn [acc k p]
                (reduce-kv (fn [acc k2 p2]
                             (let [k3 (mapv + k k2)]
                               (assoc! acc k3 (+ (get acc k3 0) (* p p2)))))
                           acc die))
              (transient {}) dist)))

(defn distribution
  "Every outcome of the pool with its probability: {[net-success net-advantage] p}."
  [pool]
  (reduce convolve {[0 0] 1.0} (map die-dists (dice/dice-list pool))))

(defn- at-least-one
  "Chance that one of n dice shows a symbol that sits on a single face of each die."
  [n sides]
  (- 1.0 (Math/pow (/ (dec sides) (double sides)) n)))

(defn- chance [dist pred]
  (reduce-kv (fn [n k p] (if (pred k) (+ n p) n)) 0.0 dist))

(defn- mean [dist i]
  (reduce-kv (fn [n k p] (+ n (* p (nth k i)))) 0.0 dist))

(defn odds
  "Headline chances for a pool, each between 0 and 1, plus the average net success and advantage."
  [pool]
  (let [d (distribution pool)]
    {:success (chance d #(pos? (% 0)))
     :advantage (chance d #(pos? (% 1)))
     :threat (chance d #(neg? (% 1)))
     :triumph (at-least-one (get pool :proficiency 0) 12)
     :despair (at-least-one (get pool :challenge 0) 12)
     :mean-net-success (mean d 0)
     :mean-net-advantage (mean d 1)}))
