(ns ttg.sheet.events-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is testing]]
            [ttg.genesys.rules :as rules]
            [ttg.http :as http]
            [ttg.sheet.db :as db]
            [ttg.sheet.events :as ev]))

(def krum (assoc (rules/blank-character) :id "x1" :version 2 :name "Krum" :wounds-taken 1))
(def ix {:archetypes {} :careers {} :talents {} :gear {} :skills [] :motivations [] :obligations []
         :crits [{:id :minor-nick :name "Minor Nick" :roll [1 50] :severity 1}
                 {:id :bad-one :name "Bad One" :roll [51 nil] :severity 3}]})
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

(deftest critical-injuries-go-on-the-character
  (let [fx (ev/roll-crit {:db (state) :now "t1"} [::ev/roll-crit 12])
        sheet (get-in fx [:db :sheet])
        c (:character sheet)]
    (is (= [{:id "minor-nick" :name "Minor Nick" :roll 12 :at "t1"}] (:crits c)))
    (is (= {:raw 12 :bonus 0 :total 12 :name "Minor Nick"} (:last-crit sheet)))
    (is (= [::ev/flush 1] (get-in fx [:dispatch-later :dispatch])) "it saves")
    (testing "the next roll is 10 higher for each one already carried"
      (let [next-fx (ev/roll-crit {:db (:db fx) :now "t2"} [::ev/roll-crit 45])]
        (is (= ["Minor Nick" "Bad One"] (map :name (get-in next-fx [:db :sheet :character :crits]))))
        (is (= 55 (get-in next-fx [:db :sheet :last-crit :total])))))
    (testing "a library with no injury table does nothing"
      (is (nil? (ev/roll-crit {:db (assoc-in (state) [:content :ix :crits] [])} [::ev/roll-crit 12]))))))

(deftest healing-removes-only-that-injury
  (let [nick {:id "minor-nick" :name "Minor Nick" :roll 12}
        bad {:id "bad-one" :name "Bad One" :roll 60}
        db (state :character (assoc krum :crits [nick bad nick]))
        c (get-in (ev/heal-crit {:db db} [::ev/heal-crit 1]) [:db :sheet :character])]
    (is (= [nick nick] (:crits c)))
    (testing "an injury that isn't there"
      (is (nil? (ev/heal-crit {:db db} [::ev/heal-crit 3]))))))

(deftest a-portrait-changes-only-the-portrait
  (let [db (ev/portrait-saved (state :uploading? true :dirty 2)
                              [::ev/portrait-saved {:character {:portrait-key "portraits/x1/a.jpg" :version 99}}])]
    (is (= "portraits/x1/a.jpg" (get-in db [:sheet :character :portrait-key])))
    (is (= 2 (get-in db [:sheet :character :version])))
    (is (s/valid? ::db/app-db db))))

(deftest the-info-drawer-opens-and-slides-away
  (let [db (ev/open-info (state) [::ev/open-info :gear "baton"])
        fx (ev/dismiss-info {:db db} [::ev/dismiss-info])]
    (is (= {:kind :gear :id "baton"} (get-in db [:sheet :info])))
    (is (s/valid? ::db/app-db db))
    (is (true? (get-in fx [:db :sheet :info :closing?])))
    (is (= (:db fx) (ev/open-info (:db fx) [::ev/open-info :talent "grit"])) "nothing opens while the last one leaves")
    (is (nil? (get-in (ev/close-info (:db fx) [::ev/close-info]) [:sheet :info])))))

(deftest attacking-from-the-drawer-hands-over-to-the-roller
  (let [check {:label "Baton attack" :pool {:ability 2}}
        fx (ev/attack-from-info {:db (ev/open-info (state) [::ev/open-info :gear "baton"])} [::ev/attack-from-info check])]
    (is (true? (get-in fx [:db :sheet :info :closing?])) "the info drawer slides away")
    (is (some #(= [:dispatch [:ttg.roller.events/open-check check]] %) (:fx fx)))
    (testing "with nothing open, it still rolls"
      (is (= [[:dispatch [:ttg.roller.events/open-check check]]] (:fx (ev/attack-from-info {:db (state)} [::ev/attack-from-info check]))))
      (is (= (state) (:db (ev/attack-from-info {:db (state)} [::ev/attack-from-info check])))))))

(defn- mine [db] (assoc-in db [:sheet :character :access] "owner"))

(deftest roll-drawer-effects-change-strain
  (let [db (mine (state))
        suffer (ev/apply-effect {:db db} [::ev/apply-effect {:strain 2}])]
    (is (= 2 (get-in suffer [:db :sheet :character :strain-taken])))
    (is (= [::ev/flush 1] (get-in suffer [:dispatch-later :dispatch])) "it saves")
    (is (= 0 (get-in (ev/apply-effect {:db (:db suffer)} [::ev/apply-effect {:strain -5}]) [:db :sheet :character :strain-taken]))
        "never below 0"))
  (is (nil? (ev/apply-effect {:db (state)} [::ev/apply-effect {:strain 1}])) "a sheet you can only read doesn't change")
  (is (nil? (ev/apply-effect {:db (mine (state))} [::ev/apply-effect {}]))))

(deftest a-discouraging-wound-moves-a-story-point
  (let [lib (assoc ix :crits [{:id :discouraging-wound :name "Discouraging Wound" :roll [1 100] :severity 1}])
        db (-> (state) (assoc-in [:content :ix] lib))
        spends #(filter (fn [[k [id]]] (and (= :dispatch k) (= :ttg.story.events/spend id))) (:fx %))]
    (is (= [{:side "player" :note "Discouraging Wound: a player story point moved to the GM."}]
           (map (comp second second) (spends (ev/roll-crit {:db (assoc db :story {:pool {:player 2 :gm 1}}) :now "t"} [::ev/roll-crit 30])))))
    (is (empty? (spends (ev/roll-crit {:db (assoc db :story {:pool {:player 0 :gm 3}}) :now "t"} [::ev/roll-crit 30])))
        "no player points, nothing to move")
    (is (empty? (spends (ev/roll-crit {:db (-> db (assoc :story {:pool {:player 2 :gm 1}}) (assoc-in [:content :ix] ix)) :now "t"}
                                      [::ev/roll-crit 30])))
        "any other injury leaves the pool alone")))
