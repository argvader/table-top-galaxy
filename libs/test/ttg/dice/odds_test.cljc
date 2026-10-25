(ns ttg.dice.odds-test
  (:require [clojure.test :refer [deftest is testing]]
            [ttg.dice :as dice]
            [ttg.dice.odds :as odds]
            [ttg.dice.rng :as rng]))

(defn- close? [tolerance a b] (< (abs (- a b)) tolerance))

(deftest distribution-sums-to-one
  (doseq [p [{} {:ability 2} {:proficiency 3 :challenge 3 :boost 2 :setback 2} {:ability 4 :difficulty 5}]]
    (is (close? 1e-9 1.0 (reduce + (vals (odds/distribution (dice/pool p))))) (pr-str p))))

(deftest single-die-chances
  (is (close? 1e-9 (/ 1.0 12) (:triumph (odds/odds (dice/pool {:proficiency 1})))))
  (is (close? 1e-9 0.5 (:success (odds/odds (dice/pool {:ability 1})))))
  (is (zero? (:success (odds/odds (dice/pool {}))))))

;; CRB p.16-18 quotes rough chances for a "focused" character (characteristic 3, 1 rank: AAP)
;; and a "general" one (characteristic 2, no ranks: AA) at each difficulty.
(def book-chances
  ;; [difficulty dice, focused low, focused high, general low, general high]
  [[0 0.90 1.00 0.70 0.80]   ; Simple: more than 90%; 75%
   [1 0.75 0.80 0.55 0.65]   ; Easy: almost 80%; about 60%
   [2 0.62 0.70 0.40 0.50]   ; Average: about two thirds; slightly less than half
   [3 0.50 0.58 0.30 0.37]   ; Hard: just over half; about a third
   [4 0.40 0.50 0.20 0.30]   ; Daunting: about 45%; a quarter
   [5 0.30 0.38 0.10 0.20]]) ; Formidable: one third; under 20%

(deftest matches-the-book's-quoted-chances
  (doseq [[n flo fhi glo ghi] book-chances
          :let [focused (:success (odds/odds (dice/pool {:ability 2 :proficiency 1 :difficulty n})))
                general (:success (odds/odds (dice/pool {:ability 2 :difficulty n})))]]
    (is (<= flo focused fhi) (str "focused vs " n " difficulty: " focused))
    (is (<= glo general ghi) (str "general vs " n " difficulty: " general))))

(deftest seeded-rolls-agree-with-exact-odds
  (let [p (dice/pool {:ability 2 :proficiency 1 :difficulty 2 :challenge 1 :boost 1 :setback 1})
        n 20000
        rolls (map :result (map #(dice/roll p (rng/seeded %)) (range n)))
        freq (fn [pred] (/ (count (filter pred rolls)) (double n)))
        o (odds/odds p)]
    (testing "Monte Carlo over 20,000 seeds is within 1.5 points of the exact chance"
      (is (close? 0.015 (:success o) (freq :success?)))
      (is (close? 0.015 (:advantage o) (freq #(pos? (:net-advantage %)))))
      (is (close? 0.015 (:triumph o) (freq #(pos? (:triumph %))))))))
