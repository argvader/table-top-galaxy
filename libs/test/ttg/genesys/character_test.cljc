(ns ttg.genesys.character-test
  (:require [clojure.test :refer [deftest is]]
            [ttg.genesys.character :as character]
            [ttg.genesys.rules :as rules]))

(deftest a-blank-character-survives-the-database
  (is (character/json-safe? (rules/blank-character))))

(deftest json-unsafe-shapes-are-refused
  (is (not (character/json-safe? {:career :tech})) "keyword values come back as strings")
  (is (not (character/json-safe? {:talents #{"grit"}})))
  (is (not (character/json-safe? {:talents (list "grit")})))
  (is (not (character/json-safe? {(keyword "Ranged (Light)") 1})) "unreadable as EDN")
  (is (not (character/json-safe? {:skill/brawl 1})))
  (is (not (character/json-safe? {"name" "Fizz"})) "string keys come back as keywords")
  (is (character/json-safe? {:skill-buy {:ranged-light 1} :talents [{:id "grit" :rank 1}] :pocket nil :done true})))
