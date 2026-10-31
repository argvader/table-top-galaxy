(ns ttg.sheet.events-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is testing]]
            [ttg.genesys.rules :as rules]
            [ttg.http :as http]
            [ttg.sheet.db :as db]
            [ttg.sheet.events :as ev]))

(def krum (assoc (rules/blank-character) :id "x1" :version 2 :name "Krum" :wounds-taken 1))
(def ix {:archetypes {} :careers {} :talents {} :gear {} :skills [] :motivations [] :obligations []})
(defn- state [& kvs]
  {:content {:ix ix} :sheet (apply assoc db/default-sheet :status :ready :character krum kvs)})

(deftest a-change-schedules-a-save
  (let [fx (ev/change {:db (state)} [::ev/change assoc :notes "Owes Fizz"])]
    (is (= "Owes Fizz" (get-in fx [:db :sheet :character :notes])))
    (is (= [::ev/flush 1] (get-in fx [:dispatch-later :dispatch])))))

(deftest only-the-latest-flush-sends
  (is (nil? (ev/flush-edits {:db (state :dirty 3)} [::ev/flush 2])))
  (let [fx (ev/flush-edits {:db (state :dirty 3)} [::ev/flush 3])]
    (is (= "PUT" (:method (::http/request fx))))
    (is (= 2 (get-in fx [::http/request :body :character :version])))
    (is (= 3 (get-in fx [:db :sheet :sent])))))

(deftest one-save-at-a-time
  (is (nil? (ev/flush-edits {:db (state :dirty 3 :saving? true)} [::ev/flush 3]))))

(deftest edits-made-during-a-save-survive-and-send-next
  (let [db (state :dirty 4 :sent 3 :saving? true :character (assoc krum :notes "newer"))
        fx (ev/saved {:db db} [::ev/saved {:character (assoc krum :version 3 :notes "older")}])]
    (is (= "newer" (get-in fx [:db :sheet :character :notes])))
    (is (= 3 (get-in fx [:db :sheet :character :version])))
    (is (= 3 (get-in fx [::http/request :body :character :version])) "sent with the new version")
    (is (get-in fx [:db :sheet :saving?]))))

(deftest a-reply-for-another-character-is-ignored
  (is (nil? (ev/saved {:db (state)} [::ev/saved {:character {:id "other" :version 9}}]))))

(deftest a-conflict-stops-saving
  (let [db (ev/save-failed (state :saving? true :dirty 2 :sent 2) [::ev/save-failed {:status 409 :error "x"}])]
    (is (get-in {:db db} [:db :sheet :conflict?]))
    (is (nil? (ev/flush-edits {:db (assoc-in db [:sheet :dirty] 3)} [::ev/flush 3])))))

(deftest other-failures-keep-the-edits-for-a-retry
  (let [db (ev/save-failed (state :saving? true :dirty 2 :sent 2) [::ev/save-failed {:status 0 :error "Offline"}])]
    (is (= "Offline" (get-in db [:sheet :error])))
    (is (= "PUT" (:method (::http/request (ev/retry {:db db} [::ev/retry])))))))

(deftest a-hit-is-damage-less-soak
  (with-redefs [rules/derived (constantly {:soak 3})]
    (testing "soak comes off"
      (let [fx (ev/take-hit {:db (state :hit "7")} [::ev/take-hit])]
        (is (= 5 (get-in fx [:db :sheet :character :wounds-taken])))
        (is (= "" (get-in fx [:db :sheet :hit])))))
    (testing "soak can absorb it all"
      (is (= 1 (get-in (ev/take-hit {:db (state :hit "2")} [::ev/take-hit]) [:db :sheet :character :wounds-taken]))))
    (testing "not a number"
      (is (nil? (ev/take-hit {:db (state :hit "")} [::ev/take-hit]))))))

(deftest a-portrait-changes-only-the-portrait
  (let [db (ev/portrait-saved (state :uploading? true :dirty 2)
                              [::ev/portrait-saved {:character {:portrait-key "portraits/x1/a.jpg" :version 99}}])]
    (is (= "portraits/x1/a.jpg" (get-in db [:sheet :character :portrait-key])))
    (is (= 2 (get-in db [:sheet :character :version])))
    (is (s/valid? ::db/app-db db))))
