(ns ttg.chronicle.events-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is]]
            [ttg.chronicle.db :as db]
            [ttg.chronicle.events :as ev]
            [ttg.http :as http]))

(defn- opened [& args]
  (ev/open {:db {:chronicle db/default-chronicle}} (into [::ev/open "c1"] args)))

(deftest the-list-and-a-page-load-from-their-own-routes
  (is (= "/campaigns/c1/chronicle" (:path (::http/request (opened)))))
  (is (= "/campaigns/c1/chronicle/npc/rook" (:path (::http/request (opened "npc" "rook"))))))

(deftest loaded-pages-show
  (let [db (:db (opened))
        ready (ev/index-loaded db [::ev/index-loaded {:entries [{:kind "npc" :slug "rook" :title "Rook"}]}])]
    (is (s/valid? ::db/app-db ready))
    (is (= :ready (get-in ready [:chronicle :status])))
    (is (= "Rook" (get-in ready [:chronicle :entries 0 :title]))))
  (let [db (:db (opened "npc" "rook"))
        ready (ev/entry-loaded db [::ev/entry-loaded {:entry {:kind "npc" :slug "rook" :title "Rook" :body "x"}}])]
    (is (= "x" (get-in ready [:chronicle :entry :body])))))

(deftest a-late-reply-for-another-page-is-dropped
  (let [db (:db (opened "npc" "rook"))]
    (is (= db (ev/entry-loaded db [::ev/entry-loaded {:entry {:kind "npc" :slug "other"}}])))
    (is (= db (ev/index-loaded db [::ev/index-loaded {:entries [{:slug "x"}]}])))))

(deftest a-failure-says-so
  (is (= "No such campaign" (get-in (ev/failed (:db (opened)) [::ev/failed {:error "No such campaign"}]) [:chronicle :error]))))
