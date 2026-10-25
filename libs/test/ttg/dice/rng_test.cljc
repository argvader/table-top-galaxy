(ns ttg.dice.rng-test
  (:require [clojure.test :refer [deftest is testing]]
            [ttg.dice.rng :as rng]))

;; From a plain-JS mulberry32 (Math.imul). Both runtimes must reproduce these exactly.
(def reference
  {0 [1144304738 1416247 958946056 627933444 2007157716 2340967985]
   42 [2581720956 1925393290 3661312704 2876485805 750819978 2261697747]
   4294967295 [3850105811 813802916 3073704848 4054706436 3630262831 2315588663]})

(deftest seeded-matches-reference
  (doseq [[seed expected] reference]
    (is (= expected (take 6 (rng/seeded seed))) (str "seed " seed))))

(deftest seeded-is-repeatable
  (is (= (take 50 (rng/seeded 7)) (take 50 (rng/seeded 7))))
  (is (not= (take 5 (rng/seeded 7)) (take 5 (rng/seeded 8)))))

(deftest secure-is-uint32
  (is (every? #(and (integer? %) (<= 0 % 4294967295)) (take 200 (rng/secure)))))

(deftest pick-covers-range-evenly
  (testing "the extremes of uint32 map to the first and last index"
    (is (= 0 (rng/pick 0 12)))
    (is (= 11 (rng/pick 4294967295 12))))
  (testing "each of n equal slices maps to its own index"
    (is (= (range 8) (map #(rng/pick (quot (* % 4294967296) 8) 8) (range 8))))))
