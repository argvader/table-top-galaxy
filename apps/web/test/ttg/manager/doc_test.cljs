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
    (is (= [] (:seats d))))
  (is (= (doc/seed content) (doc/fill-in {} content)) "nothing saved yet"))

(deftest the-draw
  (let [d (doc/draw (doc/seed content) questions "u1" [0 0])
        [a b] (get-in d [:s0 :draws :u1])]
    (is (and a b (not= a b)))
    (testing "nobody else can draw what's dealt or answered"
      (let [held (doc/add-answer d :p6 "u3" nil)]
        (is (= #{"p3" "p4" "p5"} (disj (set (doc/deck held questions "u2")) a b "p1" "p2")))))
    (testing "drawing again puts the last two aside"
      (let [again (get-in (doc/draw d questions "u1" [4294967295 4294967295]) [:s0 :draws :u1])]
        (is (empty? (filter #{a b} again)))))
    (testing "too few left leaves the draw as it was"
      (is (= d (doc/draw d (take 2 questions) "u1" [0 0]))))))

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
