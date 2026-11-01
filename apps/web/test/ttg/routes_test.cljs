(ns ttg.routes-test
  (:require [clojure.test :refer [deftest is]]
            [ttg.routes :as routes]))

(deftest hashes-round-trip
  (doseq [r [{:page :roller} {:page :library} {:page :library :type :weapons} {:page :library :type :talents :id :new}
             {:page :campaigns} {:page :campaign :id "c1"} {:page :join :code "ab12"}
             {:page :builder} {:page :builder :campaign-id "c1"} {:page :builder :character-id "x1"}
             {:page :sheet :id "x1"} {:page :chronicle :id "c1"}
             {:page :chronicle :id "c1" :kind "npc" :slug "imre-vasko"}]]
    (is (= r (routes/parse (routes/href r))))))

(deftest no-hash-lands-on-your-campaigns
  (is (= {:page :campaigns} (routes/parse ""))))

(deftest unknown-hashes-go-to-the-roller
  (is (= {:page :roller} (routes/parse "#/nowhere")))
  (is (= {:page :library :type :gear} (routes/parse "#library/gear/"))))
