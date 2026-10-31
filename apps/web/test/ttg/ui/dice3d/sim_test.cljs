(ns ttg.ui.dice3d.sim-test
  (:require [clojure.test :refer [deftest is testing]]
            [ttg.dice :as dice]
            [ttg.dice.rng :as rng]
            [ttg.ui.dice :as ui]
            [ttg.ui.dice3d.geometry :as geo]
            [ttg.ui.dice3d.sim :as sim]))

;; A phone-sized table: the walls the renderer computes for a 390x844 viewport.
(def bounds {:x 5.33 :z 12.01})

(defn- seeded-rand
  "A repeatable [0, 1) source for the throw, so a failing test can be rerun."
  [seed]
  (let [ints (atom (rng/seeded seed))]
    (fn [] (let [u (first @ints)] (swap! ints rest) (/ u 4294967296)))))

(defn- final-quaternions [{:keys [frames]} n]
  (let [f (peek frames)]
    (for [i (range n) :let [o (* 7 i)]] [(aget f (+ o 3)) (aget f (+ o 4)) (aget f (+ o 5)) (aget f (+ o 6))])))

(def pools
  [[:ability :ability :proficiency :difficulty :difficulty]
   [:boost :setback :challenge :challenge :proficiency :proficiency :ability]
   [:difficulty :difficulty :difficulty :difficulty :difficulty :ability :ability :ability :boost :boost]])

(deftest every-throw-ends-with-each-die-flat-on-the-face-it-reports
  (doseq [[pi types] (map-indexed vector pools)
          seed (range 4)
          :let [kinds (map #(:shape (ui/die-info %)) types)
                throw (sim/simulate kinds bounds (seeded-rand (+ seed (* 100 pi))))]]
    (is (some? throw) (str "pool " pi " seed " seed " never came to rest flat"))
    (when throw
      (is (<= (count (:frames throw)) (inc sim/max-steps)))
      (doseq [[kind q landed] (map vector kinds (final-quaternions throw (count kinds)) (:landed throw))
              :let [{:keys [face flatness]} (geo/up-face (geo/shape kind) q)]]
        (is (= landed face))
        (is (>= flatness sim/flat))))))

(deftest standard-dice-come-to-rest-too
  (doseq [seed (range 4)
          :let [kinds [:d4 :d4 :d6 :d8 :d10 :d10 :d20 :d20 :d10 :d10]
                throw (sim/simulate kinds bounds (seeded-rand (+ 500 seed)))]]
    (is (some? throw) (str "seed " seed " never came to rest flat"))
    (when throw
      (doseq [[kind q landed] (map vector kinds (final-quaternions throw (count kinds)) (:landed throw))
              :let [{:keys [face flatness]} (geo/up-face (geo/shape kind) q)]]
        (is (= landed face) (name kind))
        (is (>= flatness sim/flat) (name kind))))))

(deftest acting-out-shows-exactly-the-engine's-roll
  (testing "repainting each die by face-layout puts the engine's chosen face on the face that landed up"
    (let [pool (dice/pool {:proficiency 2 :ability 1 :challenge 2 :difficulty 1 :boost 1 :setback 1})
          roll (dice/roll pool (rng/seeded 11))
          kinds (map #(:shape (ui/die-info (:type %))) (:dice roll))
          throw (sim/simulate kinds bounds (seeded-rand 5))]
      (is (some? throw))
      (doseq [[d landed] (map vector (:dice roll) (:landed throw))
              :let [layout (geo/face-layout (count (dice/faces (:type d))) landed (:face d))
                    shown (get-in dice/faces [(:type d) (layout landed)])]]
        (is (= (:symbols d) shown) (str (name (:type d)) " shows its engine face"))))))
