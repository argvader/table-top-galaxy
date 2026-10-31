(ns ttg.manager.events-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is testing]]
            [ttg.manager.db :as db]
            [ttg.manager.events :as ev]))

(def content {:dials [{:id "grit" :start 4}] :npcs [] :ledger []})

(defn- opened
  ([] (opened nil))
  ([unsent]
   (-> (ev/open {:db {} :unsent unsent} [::ev/open "c1"]) :db)))

(defn- got-all [db saved c members]
  (reduce (fn [{:keys [db]} [k v]] (ev/got {:db db} [::ev/got k v]))
          {:db db} [[:saved saved] [:content c] [:party {:members members}]]))

(defn- ready-db [] (:db (got-all (opened) {} content [])))

(deftest opening-waits-for-all-three-replies
  (let [db (:db (ev/got {:db (opened)} [::ev/got :saved {}]))]
    (is (= :loading (get-in db [:manager :status]))))
  (let [db (ready-db)]
    (is (= :ready (get-in db [:manager :status])))
    (is (= 4 (get-in db [:manager :doc :s0 :dials :grit])))
    (is (s/valid? ::db/manager (:manager db)) (s/explain-str ::db/manager (:manager db)))))

(deftest no-prep-says-so
  (is (= :no-prep (get-in (got-all (opened) {} {} []) [:db :manager :status]))))

(deftest work-this-browser-couldnt-send-wins-and-goes-out
  (let [unsent {:v 1 :s0 {:done true}}
        {:keys [db fx]} (got-all (opened unsent) {:v 1 :s0 {:done false}} content [])]
    (is (true? (get-in db [:manager :doc :s0 :done])))
    (is (= "PUT" (get-in fx [0 1 :method])))))

(deftest saving
  (let [{edited :db fx :fx} (ev/change {:db (ready-db)} [::ev/change [:s0 :done] true])]
    (is (= 1 (get-in edited [:manager :dirty])))
    (testing "the browser's copy comes first, then the pause"
      (is (= ::ev/keep-copy (ffirst fx)))
      (is (= [::ev/flush 1] (get-in fx [1 1 :dispatch]))))
    (testing "a stale timer doesn't send"
      (let [newer (:db (ev/change {:db edited} [::ev/change [:s0 :done] false]))]
        (is (nil? (:fx (ev/flush-edits {:db newer} [::ev/flush 1]))))))
    (let [{sending :db send :fx} (ev/flush-edits {:db edited} [::ev/flush 1])]
      (is (true? (get-in sending [:manager :sending?])))
      (is (= "/campaigns/c1/manager" (get-in send [0 1 :path])))
      (testing "only a save that arrives clears the unsent mark"
        (is (= [[::ev/sent-all "c1"]] (:fx (ev/saved {:db sending} [::ev/saved 1]))))
        (is (= :failed (get-in (ev/save-failed sending [::ev/save-failed {:status 500}]) [:manager :problem])))
        (is (= :signin (get-in (ev/save-failed sending [::ev/save-failed {:status 401}]) [:manager :problem]))))
      (testing "edits made while it was sending go straight after it"
        (let [more (:db (ev/change {:db sending} [::ev/change [:s0 :done] false]))
              {after :fx} (ev/saved {:db more} [::ev/saved 1])]
          (is (= "PUT" (get-in after [0 1 :method]))))))))
