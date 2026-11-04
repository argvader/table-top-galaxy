(ns ttg.routes-test
  (:require [clojure.test :refer [deftest is]]
            [ttg.routes :as routes]))

(deftest paths-round-trip
  (doseq [r [{:page :roller} {:page :library} {:page :library :type :weapons} {:page :library :type :talents :id :new}
             {:page :campaigns} {:page :campaign :id "c1"} {:page :join :code "ab12"}
             {:page :builder} {:page :builder :campaign-id "c1"} {:page :builder :character-id "x1"}
             {:page :sheet :id "x1"} {:page :chronicle :id "c1"}
             {:page :chronicle :id "c1" :kind "npc" :slug "imre-vasko"}]]
    (is (= r (routes/parse (routes/href r))))))

(deftest the-root-lands-on-your-campaigns
  (is (= {:page :campaigns} (routes/parse "/")))
  (is (= {:page :campaigns} (routes/parse ""))))

(deftest unknown-paths-go-to-the-roller
  (is (= {:page :roller} (routes/parse "/nowhere")))
  (is (= {:page :library :type :gear} (routes/parse "/library/gear/")))
  (is (= {:page :join :code "ab12"} (routes/parse "/join/ab12?code=x&state=y")) "a query string is ignored"))

(deftest old-hash-links-become-paths
  (is (= "/join/ab12" (routes/legacy-path "#/join/ab12")))
  (is (= "/campaigns/c1/chronicle/npc/rook" (routes/legacy-path "#/campaigns/c1/chronicle/npc/rook")))
  (is (nil? (routes/legacy-path "")))
  (is (nil? (routes/legacy-path nil)))
  (is (nil? (routes/legacy-path "#section")) "an in-page anchor isn't a route"))
