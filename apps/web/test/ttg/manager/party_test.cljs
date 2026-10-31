(ns ttg.manager.party-test
  (:require [clojure.test :refer [deftest is testing]]
            [ttg.manager.party :as party]))

(def corps [{:id "nbn" :short "NBN" :name "Near-Earth Broadcast Network"} {:id "jinteki" :short "Jinteki" :name "Jinteki"}])

(def prance {:user-id "u1" :name "Gary" :role "player"
             :character {:id "c1" :name "Prance Galavent" :career "bounty-hunter" :archetype "natural-human"
                         :ob {:type "debt" :magnitude 10 :attachment "NBN" :why "Owes"}}})
(def krum {:user-id "u2" :name "Krum's player" :role "player"})
(def fizz {:user-id "u3" :name "Pat" :role "player"
           :character {:id "c3" :name "Fizz" :ob {:type "favor" :magnitude 5 :attachment "The Tsang family"}}})

(deftest pcs-come-from-members-keyed-by-the-player
  (let [[p k f] (party/party [prance krum fizz] corps [])]
    (is (= "u1" (:id p)) "not the character's id, so answers survive a rebuild")
    (is (= ["Prance Galavent" "Gary" "Bounty Hunter" "Natural Human"] ((juxt :name :player :role :archetype) p)))
    (is (= ["nbn" 10] ((juxt :creditor :debt) p)))
    (testing "someone who hasn't built yet shows by name"
      (is (= ["Krum's player" false 0] ((juxt :name :built? :debt) k))))
    (testing "a creditor who isn't a corp stays free text"
      (is (nil? (:creditor f)))
      (is (= "The Tsang family" (:owes f))))))

(deftest no-obligation-chosen-is-no-debt
  (testing "a new character's default magnitude doesn't count until they pick one"
    (let [pcs (party/party [{:user-id "u4" :name "Lee" :character {:id "c4" :name "Adarius" :ob {:type nil :magnitude 10 :attachment "" :why ""}}}
                            fizz]
                           corps [])]
      (is (= [0 5] (map :debt pcs)))
      (is (= 5 (party/crew-debt pcs))))))

(deftest a-corp-matches-by-any-of-its-names
  (is (= "jinteki" (:creditor (first (party/party [(assoc-in prance [:character :ob :attachment] "jinteki clone registry")] corps []))))))

(deftest seats-set-the-order-and-newcomers-go-last
  (is (= ["u3" "u1" "u2"] (map :id (party/party [prance krum fizz] corps ["u3" "u1"]))))
  (testing "the left of the last player is the first"
    (let [pcs (party/party [prance krum fizz] corps [])]
      (is (= "u2" (party/left-of pcs "u1")))
      (is (= "u1" (party/left-of pcs "u3")))))
  (is (nil? (party/left-of (party/party [prance] corps []) "u1")) "nobody on your left at a table of one"))

(deftest moving-a-seat
  (let [pcs (party/party [prance krum fizz] corps [])]
    (is (= ["u2" "u1" "u3"] (party/move pcs "u2" -1)))
    (is (= ["u1" "u2" "u3"] (party/move pcs "u1" -1)) "the end of the table stays put")))
