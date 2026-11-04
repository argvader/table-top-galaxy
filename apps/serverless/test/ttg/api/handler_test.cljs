(ns ttg.api.handler-test
  (:require [cljs.core.async :refer [go <!]]
            [cljs.test :refer [deftest is async use-fixtures]]
            [ttg.api.db :as db]
            [ttg.api.handler :as handler]
            [ttg.api.memory :as memory]
            [ttg.api.pglite :as pglite]))

(use-fixtures :each {:before #(aset js/process.env "ALLOW_DEV_OWNER" "true")
                     :after #(js-delete js/process.env "ALLOW_DEV_OWNER")})

(def armor {:id :padded :name "Padded" :defense 0 :soak 1 :price 50 :rarity 1 :setting "android" :source {:book "custom"}})

(defn- req [method path & [body owner]]
  {:method method :path path :headers (if owner {"x-ttg-owner" owner} {}) :body (some-> body pr-str)})

(deftest routes-and-statuses
  (async done
    (go (let [st {:table (memory/memory-table) :db (<! (pglite/pglite {}))}
              call #(handler/handle st %)
              heavy (assoc armor :id :heavy :name "Heavy")]
          (is (= 401 (:status (<! (call (req "GET" "/library"))))) "no owner, no library")
          (is (= 404 (:status (<! (call (req "GET" "/nope" nil "gm"))))))
          (is (= 400 (:status (<! (call (req "PUT" "/library/spells/x" {:entry armor} "gm"))))) "unknown type")
          (is (= 400 (:status (<! (call (req "PUT" "/library/armor/padded" {:wrong 1} "gm"))))))
          (is (= 400 (:status (<! (call (assoc (req "PUT" "/library/armor/padded" nil "gm") :body "{:entry"))))))
          (is (= 200 (:status (<! (call (req "PUT" "/library/armor/padded" {:entry armor} "gm"))))))
          (is (= {:entries {:armor [armor]}} (:body (<! (call (req "GET" "/library" nil "gm"))))))
          (is (= {:saved 1 :rejected []} (:body (<! (call (req "POST" "/library/import" {:entries {:armor [heavy]}} "gm"))))))
          (is (= 200 (:status (<! (call (req "DELETE" "/library/armor/padded" nil "gm"))))))
          (is (= ["Heavy"] (map :name (get-in (<! (call (req "GET" "/library" nil "gm"))) [:body :entries :armor]))))
          (let [created (:body (<! (call (req "POST" "/characters" {:character {:name "Fizz"}} "gm"))))
                path (str "/characters/" (get-in created [:character :id]))]
            (is (= 200 (:status (<! (call (req "GET" path nil "gm"))))))
            (is (= 400 (:status (<! (call (req "POST" "/characters" {:character {:name ""}} "gm"))))) "a name is required")
            (is (= 400 (:status (<! (call (req "GET" "/characters/not-a-uuid" nil "gm"))))))
            (is (= 400 (:status (<! (call (req "PUT" (str path "/campaign") {:campaign-id "nope"} "gm"))))))
            (is (= 400 (:status (<! (call (req "PUT" path {:character {:name "Fizz"}} "gm"))))) "a save says which version it started from")
            (is (= 422 (:status (<! (call (req "POST" "/characters" {:character {:name "Fizz" :career :tech}} "gm"))))) "keywords don't survive JSON")
            (is (= 422 (:status (<! (call (req "POST" "/characters" {:character {:name "Fizz" :ranks {:skill/brawl 1}}} "gm"))))))
            (is (= 400 (:status (<! (call (req "PUT" (str path "/portrait") {:image 7} "gm")))))))
          (let [cid (get-in (<! (call (req "POST" "/campaigns" {:campaign {:name "MD"}} "gm"))) [:body :campaign :id])
                path (str "/campaigns/" cid "/manager")
                put (fn [text] (call (assoc (req "PUT" path nil "gm") :body text)))]
            (is (= 200 (:status (<! (put "{\"acts\":[]}")))))
            (is (= 400 (:status (<! (put "[1]")))) "the document is an object")
            (is (= 400 (:status (<! (put "not json")))))
            (is (string? (:body (<! (call (req "GET" path nil "gm"))))) "JSON text, which the Lambda sends as it is")
            (is (= 404 (:status (<! (call (req "GET" (str "/campaigns/" cid "/library") nil "stranger"))))))
            (is (= {:entries {:armor [(assoc armor :id :heavy :name "Heavy")]}}
                   (:body (<! (call (req "GET" (str "/campaigns/" cid "/library") nil "gm")))))))
          (is (= {:user-id "gm" :name "gm"} (:body (<! (call (req "GET" "/me" nil "gm"))))))
          (done)))))

(deftest chronicle-routes
  (async done
    (go (let [t (<! (pglite/pglite {}))
              st {:table (memory/memory-table) :db t}
              call #(handler/handle st %)
              cid (get-in (<! (call (req "POST" "/campaigns" {:campaign {:name "MD"}} "gm"))) [:body :campaign :id])
              path (str "/campaigns/" cid "/chronicle")]
          (<! (db/query t "INSERT INTO chronicle_entries (campaign_id, kind, slug, title, body)
                           VALUES (CAST(:id AS uuid), 'npc', 'rook', 'Rook', '# Rook')" {:id cid}))
          (is (= {:entries [{:kind "npc" :slug "rook" :title "Rook"}]} (:body (<! (call (req "GET" path nil "gm"))))))
          (is (= "# Rook" (get-in (<! (call (req "GET" (str path "/npc/rook") nil "gm"))) [:body :entry :body])))
          (is (= 401 (:status (<! (call (req "GET" path))))) "signed out")
          (is (= 404 (:status (<! (call (req "GET" path nil "stranger"))))))
          (is (= 400 (:status (<! (call (req "GET" (str path "/spell/rook") nil "gm"))))) "unknown kind")
          (is (= 400 (:status (<! (call (req "GET" (str path "/npc/Not%20Valid") nil "gm"))))) "bad slug")
          (is (= 404 (:status (<! (call (req "GET" (str path "/npc/nobody") nil "gm"))))))
          (done)))))

(deftest dev-owner-header-needs-the-flag
  (js-delete js/process.env "ALLOW_DEV_OWNER")
  (is (nil? (handler/user {:headers {"x-ttg-owner" "gm"}})))
  (is (= {:id "abc" :name "a@b.c"} (handler/user {:claims {:sub "abc" :email "a@b.c"} :headers {"x-ttg-owner" "gm"}}))))

