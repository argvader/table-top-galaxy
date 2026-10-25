(ns ttg.roller.events-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is testing]]
            [ttg.dice :as dice]
            [ttg.dice.rng :as rng]
            [ttg.roller.db :as db]
            [ttg.roller.events :as ev]))

(defn- run
  "Applies db events in order, checking the spec after each, as the validate-spec interceptor does in dev."
  [db & events]
  (reduce (fn [db [id & _ :as e]]
            (let [db' ((case id :add ev/add-die :remove ev/remove-die :ladder ev/set-ladder :up ev/upgrade
                             :down ev/downgrade :clear ev/clear-pool :spend ev/spend-option :undo ev/undo-spend) db e)]
              (is (s/valid? ::db/app-db db') (s/explain-str ::db/app-db db'))
              db'))
          db events))

(defn- land [db] (:db (ev/landed {:db db} [::ev/landed (:rolling db)])))

(defn- roll-with
  "Rolls and lets the dice land."
  [db seed]
  (land (:db (ev/roll {:db db :seed seed} [::ev/roll]))))

(deftest default-db-is-valid
  (is (s/valid? ::db/app-db db/default-db))
  (is (= 2 (get-in db/default-db [:pool :difficulty])) "starts at Average"))

(deftest building-a-pool
  (let [db (run db/default-db [:add :ability] [:add :ability] [:up :ability] [:ladder 3] [:up :difficulty])]
    (is (= (dice/pool {:ability 1 :proficiency 1 :difficulty 2 :challenge 1}) (:pool db))))
  (testing "steppers stay between 0 and 9"
    (is (zero? (get-in (run db/default-db [:remove :boost]) [:pool :boost])))
    (is (= 9 (get-in (apply run db/default-db (repeat 12 [:add :boost])) [:pool :boost]))))
  (testing "choosing a ladder step replaces the Difficulty and Challenge dice"
    (is (= (dice/pool {:difficulty 4}) (:pool (run db/default-db [:up :difficulty] [:ladder 4])))))
  (testing "clear empties the pool back to Average"
    (is (= (:pool db/default-db) (:pool (run db/default-db [:add :ability] [:add :setback] [:clear]))))))

(deftest rolling
  (let [db (run db/default-db [:add :ability] [:add :ability] [:add :boost])
        after (roll-with db 42)
        entry (first (:rolls after))]
    (is (s/valid? ::db/app-db after))
    (testing "the roll is the engine's roll for that seed, so it can be replayed"
      (is (= (:dice (dice/roll (:pool db) (rng/seeded 42))) (:dice entry)))
      (is (= 42 (:seed entry))))
    (is (= (:id entry) (:shown after)))
    (testing "the next check starts fresh at Average with no Boost or Setback"
      (is (= (dice/pool {:ability 2 :difficulty 2}) (:pool after)))
      (is (:reset-note? after)))
    (testing "editing the pool hides the result and the note"
      (let [edited (run after [:add :ability])]
        (is (nil? (:shown edited)))
        (is (not (:reset-note? edited)))))))

(deftest the-result-waits-for-the-dice-to-land
  (let [db (run db/default-db [:add :ability])
        {rolling :db fx :fx} (ev/roll {:db db :seed 7} [::ev/roll])
        id (:id (first (:rolls rolling)))]
    (is (s/valid? ::db/app-db rolling))
    (is (= id (:rolling rolling)))
    (is (nil? (:shown rolling)) "nothing shows while the dice are in the air")
    (testing "the 3D dice are handed exactly the engine's faces"
      (is (= [[:ttg.roller.effects/act-out {:dice (mapv #(select-keys % [:type :face]) (:dice (first (:rolls rolling))))
                                            :on-done [::ev/landed id]}]]
             fx)))
    (testing "no second roll while one is on the table"
      (is (= rolling (:db (ev/roll {:db rolling :seed 8} [::ev/roll])))))
    (testing "landing shows the roll, once"
      (let [landed (land rolling)]
        (is (= id (:shown landed)))
        (is (nil? (:rolling landed)))
        (is (= landed (:db (ev/landed {:db landed} [::ev/landed id]))))))))

(deftest empty-pool-does-not-roll
  (let [db (run db/default-db [:clear] [:ladder 0])]
    (is (= db (roll-with db 1)))))

(deftest log-keeps-the-newest-rolls
  (let [db (reduce roll-with (run db/default-db [:add :ability]) (range 30))]
    (is (= db/log-size (count (:rolls db))))
    (is (= 29 (:seed (first (:rolls db)))) "newest first")
    (is (s/valid? ::db/app-db db))))

(deftest spending-on-the-shown-roll
  ;; Find a seed whose roll leaves net Advantage, so there is something to spend.
  (let [db (run db/default-db [:add :ability] [:add :ability] [:add :boost] [:ladder 0])
        seed (first (filter #(pos? (get-in (dice/roll (:pool db) (rng/seeded %)) [:result :net-advantage])) (range)))
        rolled (roll-with db seed)
        spent (run rolled [:spend :advantage 0])]
    (is (= ["Recover 1 strain"] (map :label (get-in (first (:rolls spent)) [:spent :advantage]))))
    (is (empty? (get-in (first (:rolls (run spent [:undo :advantage]))) [:spent :advantage])))))
