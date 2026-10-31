(ns ttg.campaign.events-test
  (:require [clojure.test :refer [deftest is]]
            [ttg.campaign.db :as db]
            [ttg.campaign.events :as ev]
            [ttg.campaign.subscriptions :as subs]))

(deftest only-a-campaigns-gm-counts
  (is (subs/runs-a-campaign? [{:role "player"} {:role "gm"}]))
  (is (not (subs/runs-a-campaign? [{:role "player"}])))
  (is (not (subs/runs-a-campaign? []))))

(deftest roles-leave-the-open-pages-status-alone
  (let [db (ev/roles-loaded {:table (assoc db/default-table :status :loading)}
                            [::ev/roles-loaded {:campaigns [{:id "c1" :role "gm"}]}])]
    (is (= :loading (get-in db [:table :status])))
    (is (= [{:id "c1" :role "gm"}] (get-in db [:table :campaigns])))))

(deftest a-new-campaign-refreshes-the-roles
  (is (= [::ev/load-roles] (:dispatch (ev/created {} [::ev/created {:campaign {:id "c1"}}])))))
