(ns ttg.dice-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [ttg.dice :as dice]
            [ttg.dice.model :as model]
            [ttg.dice.rng :as rng]))

;; ---------- Faces: symbol totals per die, counted from Table I.1-2 (CRB p.10) ----------

(deftest standard-dice-stay-on-their-faces
  (doseq [seed (range 200)
          {:keys [type value]} (:rolled (dice/roll-plain {:d4 3 :d10 3 :d100 3} (rng/seeded seed)))]
    (is (<= 1 value (dice/plain-sides type)))))

(deftest a-d100-is-thrown-as-tens-and-ones
  (is (= [{:type :d100-tens :face 7} {:type :d100-ones :face 3}] (dice/thrown {:type :d100 :value 73})))
  (is (= [{:type :d100-tens :face 1} {:type :d100-ones :face 0}] (dice/thrown {:type :d100 :value 10})) "reads 10 + 0")
  (is (= [{:type :d100-tens :face 0} {:type :d100-ones :face 5}] (dice/thrown {:type :d100 :value 5})) "reads 00 + 5")
  (is (= [{:type :d100-tens :face 0} {:type :d100-ones :face 0}] (dice/thrown {:type :d100 :value 100})) "00 + 0 is 100")
  (is (= [{:type :d6 :face 3}] (dice/thrown {:type :d6 :value 4}))))

(deftest labels-read-like-the-table-says-them
  (is (= "2d6 + 1d20" (dice/plain-label {:d6 2 :d20 1 :d4 0})))
  (is (= "1d100" (dice/plain-label {:d100 1}))))

(deftest face-tables-match-the-book
  (let [totals (fn [t] (frequencies (mapcat identity (dice/faces t))))]
    (is (= [6 6 8 8 12 12] (map (comp count dice/faces) [:boost :setback :ability :difficulty :proficiency :challenge])))
    (is (= {:success 2 :advantage 4} (totals :boost)))
    (is (= {:failure 2 :threat 2} (totals :setback)))
    (is (= {:success 5 :advantage 5} (totals :ability)))
    (is (= {:failure 4 :threat 6} (totals :difficulty)))
    (is (= {:success 9 :advantage 8 :triumph 1} (totals :proficiency)))
    (is (= {:failure 8 :threat 8 :despair 1} (totals :challenge)))))

;; ---------- Pool modification (CRB p.20-23) ----------

(deftest upgrading
  (is (= (dice/pool {:ability 1 :proficiency 1}) (dice/upgrade (dice/pool {:ability 2}) :ability 1)))
  (testing "with no Ability dice left, an upgrade adds one, and the next upgrade upgrades it (p.22)"
    (is (= (dice/pool {:proficiency 3}) (dice/upgrade (dice/pool {:proficiency 2}) :ability 2)))
    (is (= (dice/pool {:proficiency 3 :ability 1}) (dice/upgrade (dice/pool {:proficiency 2}) :ability 3))))
  (is (= (dice/pool {:challenge 1 :difficulty 1}) (dice/upgrade (dice/pool {:difficulty 2}) :difficulty 1)))
  (testing "a Story Point upgrade on a Simple check adds a Difficulty die"
    (is (= (dice/pool {:ability 2 :difficulty 1}) (dice/upgrade (dice/pool {:ability 2}) :difficulty 1)))))

(deftest downgrading
  (is (= (dice/pool {:ability 2}) (dice/downgrade (dice/pool {:ability 1 :proficiency 1}) :ability 1)))
  (testing "downgrades beyond the available dice are ignored (p.22)"
    (is (= (dice/pool {:difficulty 3}) (dice/downgrade (dice/pool {:challenge 1 :difficulty 2}) :difficulty 4)))))

(deftest removing
  (is (= (dice/pool {:ability 2}) (dice/remove-dice (dice/pool {:ability 2 :setback 2}) {:setback 5}))))

(deftest modify-applies-the-book-order
  (testing "upgrades happen before downgrades, so an upgrade-added die survives the downgrade (p.22-23)"
    (is (= (dice/pool {:ability 1 :difficulty 2})
           (dice/modify (dice/pool {:difficulty 2}) {:upgrade {:ability 1} :downgrade {:ability 1}}))))
  (testing "removal comes after adding"
    (is (= (dice/pool {:ability 2 :setback 1})
           (dice/modify (dice/pool {:ability 2}) {:add {:setback 3} :remove {:setback 2}})))))

;; ---------- Generators ----------

(def gen-die
  (gen/bind (gen/elements dice/die-types)
            (fn [t] (gen/fmap (fn [i] {:type t :face i :symbols (get-in dice/faces [t i])})
                              (gen/choose 0 (dec (count (dice/faces t))))))))

(def gen-pool (gen/fmap dice/pool (gen/map (gen/elements dice/die-types) (gen/choose 0 5))))

;; ---------- Properties ----------

(defspec cancellation-matches-the-book 300
  (prop/for-all [ds (gen/vector gen-die 0 20)]
    (let [n (frequencies (mapcat :symbols ds))
          c #(get n % 0)
          r (dice/result ds)]
      (and (= (:net-success r) (- (+ (c :success) (c :triumph)) (c :failure) (c :despair)))
           (= (:net-advantage r) (- (c :advantage) (c :threat)))
           (= (:success? r) (pos? (:net-success r)))
           (= [(c :triumph) (c :despair)] [(:triumph r) (:despair r)])))))

(defspec triumph-counts-as-a-success 200
  (prop/for-all [ds (gen/vector gen-die 0 12)]
    (let [r (dice/result ds)
          r+ (dice/result (conj ds {:type :proficiency :face 11 :symbols [:triumph]}))]
      (and (= (inc (:net-success r)) (:net-success r+))
           (= (inc (:triumph r)) (:triumph r+))
           (= (:net-advantage r) (:net-advantage r+))))))

(defspec upgrades-never-lose-dice-and-downgrades-keep-the-count 200
  (prop/for-all [p gen-pool side (gen/elements [:ability :difficulty]) n (gen/choose 0 6)]
    (let [total #(reduce + (vals %))]
      (and (<= (total p) (total (dice/upgrade p side n)) (+ (total p) n))
           (= (total p) (total (dice/downgrade p side n)))))))

(defspec rolls-are-valid-and-replayable 100
  (prop/for-all [p gen-pool seed gen/nat]
    (let [r (dice/roll p (rng/seeded seed))]
      (and (s/valid? ::model/roll r)
           (= (count (:dice r)) (reduce + (vals p)))
           (= r (dice/roll p (rng/seeded seed)))))))

(deftest secure-roll-is-valid
  (is (s/valid? ::model/roll (dice/roll (dice/pool {:ability 2 :proficiency 1 :difficulty 2 :boost 1})))))
