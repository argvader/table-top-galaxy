(ns ttg.story.events-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is testing]]
            [ttg.http :as http]
            [ttg.story.db :as db]
            [ttg.story.events :as ev]))

(defn- story [& kvs] {:story (apply assoc db/default-story :campaign-id "c1" kvs)})
(defn- valid [db] (is (s/valid? ::db/app-db db) (s/explain-str ::db/app-db db)) db)

(deftest a-session-starts-with-a-point-per-player
  (is (= {:player 3 :gm 1} (db/new-session 3)))
  (is (= db/max-points (:player (db/new-session 40)))))

(deftest loading-follows-the-campaign
  (let [fx (ev/load-pool {:db {}} [::ev/load "c1"])]
    (is (= "/campaigns/c1/story" (get-in fx [::http/request :path])))
    (valid (:db fx))
    (is (= {:player 2 :gm 1} (get-in (ev/loaded (:db fx) [::ev/loaded "c1" {:story {:player 2 :gm 1}}]) [:story :pool])))
    (is (= (:db fx) (ev/loaded (:db fx) [::ev/loaded "c2" {:story {:player 9 :gm 9}}])) "a stale reply is ignored"))
  (is (nil? (::http/request (ev/load-pool {:db {}} [::ev/load nil]))) "no campaign, nothing to load"))

(deftest spending-waits-for-the-server
  (let [db (story :pool {:player 2 :gm 1})
        fx (ev/spend {:db db} [::ev/spend {:side "player" :note "Upgraded" :on-ok [:upgrade]}])]
    (is (= {:method "POST" :path "/campaigns/c1/story/spend" :body {:side "player"}}
           (select-keys (::http/request fx) [:method :path :body])))
    (is (get-in fx [:db :story :busy?]))
    (is (nil? (ev/spend {:db (:db fx)} [::ev/spend {:side "player"}])) "one spend at a time")
    (testing "once spent, the count is the server's and the upgrade happens"
      (let [done (ev/spent {:db (:db fx)} [::ev/spent "c1" "Upgraded" [:upgrade] {:story {:player 1 :gm 2}}])]
        (is (= {:player 1 :gm 2} (get-in done [:db :story :pool])))
        (is (= "Upgraded" (get-in done [:db :story :note])))
        (is (= [[:dispatch [:upgrade]]] (:fx done)))
        (valid (:db done))))
    (testing "refused: nothing happens but a fresh count"
      (let [refused (ev/spend-failed {:db (:db fx)} [::ev/spend-failed "c1" {:status 409 :error "No player story points left"}])]
        (is (nil? (:fx refused)))
        (is (= "/campaigns/c1/story" (get-in refused [::http/request :path])))
        (is (false? (get-in refused [:db :story :busy?]))))))
  (is (nil? (ev/spend {:db (story)} [::ev/spend {:side "player"}])) "no session started"))

(deftest the-gm-sets-the-pool
  (let [fx (ev/set-pool {:db (story)} [::ev/set-pool {:player 4 :gm 1}])]
    (is (= {:method "PUT" :path "/campaigns/c1/story" :body {:story {:player 4 :gm 1}}}
           (select-keys (::http/request fx) [:method :path :body])))))
