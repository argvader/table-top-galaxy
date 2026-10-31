(ns ttg.api.service-test
  (:require [cljs.core.async :refer [go <!]]
            [cljs.test :refer [deftest is async]]
            [ttg.api.memory :as memory]
            [ttg.api.service :as service]))

(def knife
  {:id :knife :name "Knife" :skill "Melee" :damage-bonus 1 :adds-brawn true :crit 3 :range "Engaged"
   :qualities [] :price 25 :rarity 1 :setting "android" :source {:book "custom"}})

(deftest save-list-delete
  (async done
    (go (let [t (memory/memory-table)]
          (is (= {:entry knife} (<! (service/save-entry! t "gm" :weapons "knife" (assoc knife :sources [])))))
          (is (= {:entries {:weapons [knife]}} (<! (service/library t "gm"))))
          (is (= {:entries {}} (<! (service/library t "someone-else"))) "owners never see each other's entries")
          (<! (service/delete-entry! t "gm" :weapons "knife"))
          (is (= {:entries {}} (<! (service/library t "gm"))))
          (done)))))

(deftest save-rejects-bad-entries
  (async done
    (go (let [t (memory/memory-table)
              mismatch (<! (service/save-entry! t "gm" :weapons "spoon" knife))
              invalid (<! (service/save-entry! t "gm" :weapons "knife" (dissoc knife :range)))]
          (is (= 400 (:status (ex-data mismatch))))
          (is (= 422 (:status (ex-data invalid))))
          (is (= {[:range] "Required"} (:problems (ex-data invalid))))
          (is (= {:entries {}} (<! (service/library t "gm"))))
          (done)))))

(deftest import-keeps-the-good-rows
  (async done
    (go (let [t (memory/memory-table)
              r (<! (service/import! t "gm" {:weapons [knife (dissoc (assoc knife :id :bad) :name)]}))]
          (is (= 1 (:saved r)))
          (is (= [{:type :weapons :id :bad :name nil :problems {[:name] "Required"}}] (:rejected r)))
          (is (= [knife] (get-in (<! (service/library t "gm")) [:entries :weapons])))
          (done)))))
