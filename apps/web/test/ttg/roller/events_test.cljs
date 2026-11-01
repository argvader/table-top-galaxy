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
            (let [db' ((case id :mode ev/set-mode :add ev/add-die :remove ev/remove-die :ladder ev/set-ladder :up ev/upgrade
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

(def stealth {:label "Stealth" :detail "Agility 3 · Rank 1" :pool {:proficiency 1 :ability 2 :setback 1}})

(defn- open-check [db] (ev/open-check db [::ev/open-check stealth]))
(defn- valid [db] (is (s/valid? ::db/app-db db) (s/explain-str ::db/app-db db)) db)

(deftest the-drawer-holds-a-check
  (let [before (run db/default-db [:mode :plain] [:add :d6] [:mode :campaign] [:add :boost] [:ladder 3])
        db (valid (open-check (assoc before :mode :plain :shown 1)))]
    (is (= :campaign (:mode db)))
    (is (nil? (:shown db)))
    (testing "the pool replaces what was there, at Average"
      (is (= (dice/pool {:proficiency 1 :ability 2 :setback 1 :difficulty 2}) (:pool db))))
    (testing "the log says what was rolled"
      (is (= "Stealth" (:label (first (:rolls (roll-with db 42)))))))
    (testing "the result shows, then Roll again starts from the check, keeping its Setback and dropping what was added"
      (let [after (roll-with (run db [:add :boost] [:ladder 3]) 42)]
        (is (some? (:shown after)))
        (is (nil? (:shown (valid (ev/roll-again after [::ev/roll-again])))))
        (is (= (dice/pool {:proficiency 1 :ability 2 :setback 1 :difficulty 2}) (:pool after)))))
    (testing "closing leaves the Dice roller page as it was, keeping the log"
      (let [after (roll-with db 42)
            closed (valid (ev/close-check after [::ev/close-check]))]
        (is (nil? (:check closed)))
        (is (nil? (:shown closed)))
        (is (= :plain (:mode closed)))
        (is (= (:pool before) (:pool closed)))
        (is (= 3 (:ladder closed)))
        (is (= 1 (count (:rolls closed))))))
    (testing "closing mid-roll: the late landing shows nothing, and the roll is in the log"
      (let [{rolling :db} (ev/roll {:db db :seed 7} [::ev/roll])
            closed (valid (ev/close-check rolling [::ev/close-check]))
            landed (:db (ev/landed {:db closed} [::ev/landed (:rolling rolling)]))]
        (is (nil? (:rolling closed)))
        (is (nil? (:shown landed)))
        (is (= 1 (count (:rolls landed))))))))

(deftest only-ranged-attacks-show-range-bands
  (is (db/ranged? {:range "Short"}))
  (is (not (db/ranged? {:range "Engaged"})))
  (is (not (db/ranged? nil))))

(deftest closing-with-no-check-changes-nothing
  (is (= db/default-db (ev/close-check db/default-db [::ev/close-check]))))

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

(deftest standard-dice
  (let [db (run db/default-db [:mode :plain] [:add :d6] [:add :d6] [:add :d20] [:add :d100])
        {rolling :db fx :fx} (ev/roll {:db db :seed 9} [::ev/roll])
        entry (first (:rolls rolling))
        expected (dice/roll-plain (:plain-pool db) (rng/seeded 9))]
    (is (= {:d4 0 :d6 2 :d8 0 :d10 0 :d20 1 :d100 1} (:plain-pool db)))
    (is (= (:pool db/default-db) (:pool db)) "the campaign pool isn't touched")
    (is (s/valid? ::db/app-db rolling) (s/explain-str ::db/app-db rolling))
    (testing "the entry is the engine's roll for that seed"
      (is (= (:rolled expected) (:rolled entry)))
      (is (= (reduce + (map :value (:rolled entry))) (:total entry)))
      (is (= "2d6 + 1d20 + 1d100" (:label entry))))
    (testing "the d100 goes to the table as two dice"
      (is (= 5 (count (get-in fx [0 1 :dice])))))
    (testing "the dice stay in the pool for the next roll"
      (is (= (:plain-pool db) (:plain-pool (land rolling)))))
    (testing "steppers stay between 0 and 9, and clear only empties the standard pool"
      (is (= 9 (get-in (apply run db (repeat 12 [:add :d4])) [:plain-pool :d4])))
      (let [cleared (run db [:clear])]
        (is (= dice/empty-plain-pool (:plain-pool cleared)))
        (is (= (:pool db) (:pool cleared)))))
    (testing "an empty standard pool doesn't roll, whatever the campaign pool holds"
      (let [empty-pool (run db/default-db [:add :ability] [:mode :plain])]
        (is (= empty-pool (:db (ev/roll {:db empty-pool :seed 1} [::ev/roll]))))))
    (testing "a bigger roll than the table holds still counts every die"
      (let [big (apply run db (mapcat #(repeat 9 [:add %]) [:d4 :d10 :d100]))
            {after :db fx :fx} (ev/roll {:db big :seed 3} [::ev/roll])
            shown (get-in fx [0 1 :dice])]
        (is (= db/max-on-table (count shown)))
        (is (every? (set (map :type shown)) [:d4 :d10 :d100-tens]) "the spread reaches the whole roll")
        (is (= 30 (count (:rolled (first (:rolls after))))))))
    (testing "switching hides the last result"
      (is (nil? (:shown (run (land rolling) [:mode :campaign])))))))

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
