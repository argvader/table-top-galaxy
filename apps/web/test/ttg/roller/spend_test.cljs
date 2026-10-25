(ns ttg.roller.spend-test
  (:require [clojure.test :refer [deftest is testing]]
            [ttg.roller.spend :as spend]))

(defn- roll [net-advantage triumph despair]
  {:result {:success? true :net-success 1 :net-advantage net-advantage :triumph triumph :despair despair} :spent {}})

(defn- opt [side cost] (first (filter #(= cost (:cost %)) (spend/options side))))
(defn- index [side cost] (.indexOf (mapv :cost (spend/options side)) cost))

(deftest remaining-per-side
  (is (= {:small 2 :big 1} (spend/remaining (:result (roll 2 1 0)) [] :advantage)))
  (is (= {:small 0 :big 0} (spend/remaining (:result (roll 2 1 0)) [] :threat)))
  (is (= {:small 3 :big 1} (spend/remaining (:result (roll -3 0 1)) [] :threat))))

(deftest payment-rules
  (testing "Advantage pays first"
    (is (= {:paid-small 2 :paid-big 0} (spend/payment (opt :advantage 2) {:small 2 :big 1}))))
  (testing "a Triumph covers any option when Advantage runs short (CRB p.103)"
    (is (= {:paid-small 0 :paid-big 1} (spend/payment (opt :advantage 3) {:small 1 :big 1}))))
  (testing "Triumph-only options need a Triumph"
    (is (nil? (spend/payment (opt :advantage :big) {:small 5 :big 0})))
    (is (= {:paid-small 0 :paid-big 1} (spend/payment (opt :advantage :big) {:small 0 :big 1}))))
  (is (nil? (spend/payment (opt :threat 2) {:small 1 :big 0}))))

(deftest spending-and-undo
  (let [r (-> (roll 3 0 0) (spend/spend :advantage (index :advantage 2)) (spend/spend :advantage (index :advantage 1)))]
    (is (= {:small 0 :big 0} (spend/remaining (:result r) (get-in r [:spent :advantage]) :advantage)))
    (testing "an unaffordable option changes nothing"
      (is (= r (spend/spend r :advantage (index :advantage 1)))))
    (is (= 1 (count (get-in (spend/undo r :advantage) [:spent :advantage]))))
    (is (= [] (get-in (spend/undo (roll 1 0 0) :threat) [:spent :threat])))))
