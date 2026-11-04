(ns ttg.manager.doc-test
  (:require [clojure.test :refer [deftest is testing]]
            [ttg.manager.doc :as doc]))

(def content
  {:dials [{:id "grit" :start 4}]
   :npcs [{:id "vasko" :name "Imre Vasko"}]
   :ledger [{:id "saboteur" :clues ["A fuel line cut clean."]}]})

(def questions (mapv (fn [i] {:id (str "p" i) :set "personal"}) (range 1 7)))

(deftest a-new-document-starts-before-session-0
  (let [d (doc/seed content)]
    (is (= 4 (get-in d [:s0 :dials :grit])))
    (is (= {:t "A fuel line cut clean." :s nil} (get-in d [:ledger 0 :clues 0])))
    (is (= "hidden" (get-in d [:ledger 0 :state])))
    (is (nil? (get-in d [:npcs 0 :met])))))

(deftest saved-work-is-kept-and-gaps-filled
  (let [saved {:v 1 :s0 {:done true :answers {:p1 [{:pc "u1"}]}} :npcs [] :sessions [{:n 1}]}
        d (doc/fill-in saved content)]
    (is (= [{:pc "u1"}] (get-in d [:s0 :answers :p1])))
    (is (= [{:n 1}] (:sessions d)))
    (is (= {} (get-in d [:s0 :run])) "added since that document was written")
    (is (= "" (get-in d [:s0 :bid :stake])) "the bid, added later still")
    (is (= [] (:seats d))))
  (is (= (doc/seed content) (doc/fill-in {} content)) "nothing saved yet"))

(deftest the-d20
  (let [d (doc/roll (doc/seed content) questions "u1" [0])
        [a :as rolled] (get-in d [:s0 :draws :u1])]
    (is (= ["p1"] rolled) "one question, the lowest open number for the lowest roll")
    (testing "nobody else can roll what's rolled or answered"
      (let [held (doc/add-answer d :p6 "u3" nil)]
        (is (= #{"p2" "p3" "p4" "p5"} (set (doc/deck held questions "u2"))))))
    (testing "rolling again puts the last one aside"
      (is (not= [a] (get-in (doc/roll d questions "u1" [0]) [:s0 :draws :u1]))))
    (testing "with nothing left the roll stays as it was"
      (is (= d (doc/roll d (take 1 questions) "u1" [0]))))
    (testing "a real die's number is taken as rolled"
      (is (= ["p4"] (get-in (doc/take-number d questions "u2" "4") [:s0 :draws :u2]))))
    (testing "a number someone holds doesn't count"
      (is (= d (doc/take-number d questions "u2" "1"))))))

(deftest answers
  (let [d (-> (doc/seed content) (doc/add-answer :c1 "u1" "u2") (doc/add-answer :c1 "u2" "u1"))]
    (is (= "u2" (get-in d [:s0 :answers :c1 0 :about])))
    (is (= ["u2"] (map :pc (get-in (doc/remove-answer d :c1 0) [:s0 :answers :c1]))))
    (is (not (contains? (get-in (-> d (doc/remove-answer :c1 0) (doc/remove-answer :c1 0)) [:s0 :answers]) :c1)))))

(deftest secrets-and-sessions
  (let [d (assoc (doc/seed content) :session 3)
        planted (doc/plant-clue d "saboteur" 0)]
    (is (= 3 (get-in planted [:ledger 0 :clues 0 :s])))
    (is (= "hinted" (get-in planted [:ledger 0 :state])) "a planted clue hints a hidden secret")
    (is (nil? (get-in (doc/plant-clue planted "saboteur" 0) [:ledger 0 :clues 0 :s])))
    (is (= 3 (get-in (doc/set-state d "saboteur" "revealed") [:ledger 0 :revealedIn])))
    (is (nil? (get-in (-> d (doc/set-state "saboteur" "revealed") (doc/set-state "saboteur" "hinted")) [:ledger 0 :revealedIn]))))
  (let [d (-> (doc/seed content) (doc/plan-session "ki") (doc/plan-session "ki") (doc/start-session 1) (doc/start-session 2))]
    (is (= ["played" "now"] (map :status (:sessions d))))
    (is (= 2 (:session d)))
    (is (true? (get-in d [:s0 :done])))))
