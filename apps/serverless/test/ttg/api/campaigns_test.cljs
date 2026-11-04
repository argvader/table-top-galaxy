(ns ttg.api.campaigns-test
  "Characters, campaigns, invites and redaction, on real SQL through PGlite."
  (:require [cljs.core.async :refer [go <!]]
            [cljs.test :refer [deftest is async]]
            [ttg.api.campaigns :as campaigns]
            [ttg.api.characters :as characters]
            [ttg.api.chronicle :as chronicle]
            [ttg.api.db :as db]
            [ttg.api.images :as images]
            [ttg.api.pglite :as pglite]))

(def gm {:id "gm-1" :name "Gary"})
(def prance {:id "p-1" :name "Prance"})
(def krum {:id "p-2" :name "Krum"})

(defn- status [x] (:status (ex-data x)))

(defrecord FakeImages [stored]
  images/Images
  (put-image! [_ key _bytes] (go (swap! stored conj key) true))
  (delete-image! [_ key] (go (swap! stored disj key) true)))

(defn- fake-images [] (->FakeImages (atom #{})))
(def ^:private jpeg (js/Buffer.from #js [0xFF 0xD8 0xFF 0xE0 1 2 3]))

(deftest characters-belong-to-their-owner
  (async done
    (go (let [t (<! (pglite/pglite {}))
              {c :character} (<! (characters/create! t (:id prance) {:name "Prance Galavent" :xp 110 :id "forged" :owner "krum"}))]
          (is (= (:id prance) (:owner c)) "the server, not the body, decides owner and id")
          (is (not= "forged" (:id c)))
          (is (= ["Prance Galavent"] (map :name (:characters (<! (characters/mine t (:id prance)))))))
          (is (empty? (:characters (<! (characters/mine t (:id krum))))))
          (is (= 403 (status (<! (characters/fetch t (:id krum) (:id c))))))
          (is (= 403 (status (<! (characters/save! t (:id krum) (:id c) {:name "Stolen" :version 1})))))
          (is (= 120 (get-in (<! (characters/save! t (:id prance) (:id c) {:name "Prance" :xp 120 :version 1})) [:character :xp])))
          (is (= 404 (status (<! (characters/fetch t (:id prance) "00000000-0000-0000-0000-000000000000")))))
          (<! (characters/delete! t (fake-images) (:id prance) (:id c)))
          (is (empty? (:characters (<! (characters/mine t (:id prance))))))
          (done)))))

(deftest a-campaign-from-invite-to-gm-view
  (async done
    (go (let [t (<! (pglite/pglite {}))
              {{cid :id} :campaign} (<! (campaigns/create! t gm {:name "Manifest Destiny" :gm {:secrets ["The mayor is a clone"]}}))
              {code :code} (<! (campaigns/invite! t (:id gm) cid))
              {c :character} (<! (characters/create! t (:id prance) {:name "Prance Galavent"}))]
          (is (= 404 (status (<! (campaigns/fetch t (:id prance) cid)))) "non-members can't see it at all")
          (is (= 403 (status (<! (characters/set-campaign! t (:id prance) (:id c) cid)))) "join before adding a character")
          (is (= {:campaign-name "Manifest Destiny"} (<! (campaigns/invite-info t code))))
          (<! (campaigns/join! t prance code))
          (<! (campaigns/join! t prance code))
          (is (= 403 (status (<! (campaigns/invite! t (:id prance) cid)))) "players can't invite")
          (<! (characters/set-campaign! t (:id prance) (:id c) cid))
          (let [player-view (<! (campaigns/fetch t (:id prance) cid))
                gm-view (<! (campaigns/fetch t (:id gm) cid))]
            (is (nil? (get-in player-view [:campaign :gm])) "GM-only data is stripped for players")
            (is (= ["The mayor is a clone"] (get-in gm-view [:campaign :gm :secrets])))
            (is (= #{"gm" "player"} (set (map :role (:members gm-view)))) "joining twice is one membership")
            (is (= [{:id (:id c) :name "Prance Galavent" :owner (:id prance)}] (:characters gm-view))))
          (is (= "Prance Galavent" (get-in (<! (characters/fetch t (:id gm) (:id c))) [:character :name])) "the GM reads player sheets")
          (is (= "gm" (get-in (<! (characters/fetch t (:id gm) (:id c))) [:character :access])))
          (is (= "owner" (get-in (<! (characters/fetch t (:id prance) (:id c))) [:character :access])))
          (let [fixed (:character (<! (characters/save! t (:id gm) (:id c) {:name "Prance Galavent" :adjust {:wounds -1}
                                                                            :version 1 :access "owner"})))]
            (is (= {:wounds -1} (:adjust fixed)) "the GM can override a player's sheet")
            (is (= (:id prance) (:owner fixed)) "and it stays the player's")
            (is (= "gm" (:access fixed)) "access comes from the server, not the body"))
          (is (= 403 (status (<! (characters/save! t (:id krum) (:id c) {:name "Stolen" :version 2})))) "other players can't")
          (is (= 403 (status (<! (characters/delete! t (fake-images) (:id gm) (:id c))))) "deleting stays the owner's")
          (is (= "gm" (get-in (<! (characters/set-portrait! t (fake-images) (:id gm) (:id c) jpeg)) [:character :access])))
          (is (= 403 (status (<! (characters/fetch t (:id krum) (:id c))))))
          (is (= [{:id cid :name "Manifest Destiny" :role "player"}] (:campaigns (<! (campaigns/mine t prance)))))
          (done)))))

(deftest leaving-and-deleting-detach-characters
  (async done
    (go (let [t (<! (pglite/pglite {}))
              {{cid :id} :campaign} (<! (campaigns/create! t gm {:name "MD"}))
              {code :code} (<! (campaigns/invite! t (:id gm) cid))
              _ (<! (campaigns/join! t prance code))
              _ (<! (campaigns/join! t krum code))
              {a :character} (<! (characters/create! t (:id prance) {:name "Prance"}))
              {b :character} (<! (characters/create! t (:id krum) {:name "Krum"}))]
          (<! (characters/set-campaign! t (:id prance) (:id a) cid))
          (<! (characters/set-campaign! t (:id krum) (:id b) cid))
          (is (= 403 (status (<! (campaigns/remove-member! t (:id prance) cid (:id krum))))) "players can't kick players")
          (is (= 400 (status (<! (campaigns/remove-member! t (:id gm) cid (:id gm))))))
          (<! (campaigns/remove-member! t (:id krum) cid (:id krum)))
          (is (nil? (get-in (<! (characters/fetch t (:id krum) (:id b))) [:character :campaign-id])))
          (is (= ["Prance"] (map :name (:characters (<! (campaigns/fetch t (:id gm) cid))))))
          (is (= 403 (status (<! (campaigns/delete! t (:id prance) cid)))))
          (<! (campaigns/save! t (:id gm) cid {:name "Manifest Destiny"}))
          (is (= "Manifest Destiny" (:name (first (:campaigns (<! (campaigns/mine t prance)))))) "renames reach members' lists")
          (<! (campaigns/delete! t (:id gm) cid))
          (is (nil? (get-in (<! (characters/fetch t (:id prance) (:id a))) [:character :campaign-id])))
          (is (empty? (:campaigns (<! (campaigns/mine t prance)))))
          (is (= 404 (status (<! (campaigns/join! t krum code)))) "invites to a deleted campaign stop working")
          (done)))))

(deftest stale-saves-are-refused
  (async done
    (go (let [t (<! (pglite/pglite {}))
              {c :character} (<! (characters/create! t (:id krum) {:name "Krum" :strain 1}))
              saved (:character (<! (characters/save! t (:id krum) (:id c) (assoc c :strain 2))))]
          (is (= 2 (:version saved)))
          (is (= 409 (status (<! (characters/save! t (:id krum) (:id c) (assoc c :strain 9))))) "a second tab still on version 1")
          (is (= 2 (:strain (:character (<! (characters/fetch t (:id krum) (:id c)))))))
          (done)))))

(deftest portraits-belong-to-the-owner
  (async done
    (go (let [t (<! (pglite/pglite {}))
              imgs (fake-images)
              {c :character} (<! (characters/create! t (:id krum) {:name "Krum"}))
              one (:character (<! (characters/set-portrait! t imgs (:id krum) (:id c) jpeg)))
              two (:character (<! (characters/set-portrait! t imgs (:id krum) (:id c) jpeg)))]
          (is (not= (:portrait-key one) (:portrait-key two)) "a new key each time, so caches never go stale")
          (is (= #{(:portrait-key two)} @(:stored imgs)) "the old picture is removed")
          (is (= 1 (:version two)) "a portrait doesn't invalidate an open sheet")
          (is (= 403 (status (<! (characters/set-portrait! t imgs (:id prance) (:id c) jpeg)))))
          (is (= 422 (status (<! (characters/set-portrait! t imgs (:id krum) (:id c) (js/Buffer.from "<svg></svg>"))))) "not a JPEG")
          (is (= 413 (status (<! (characters/set-portrait! t imgs (:id krum) (:id c) (js/Buffer.alloc 262145))))))
          (<! (characters/delete! t imgs (:id krum) (:id c)))
          (is (empty? @(:stored imgs)) "deleting the character removes the picture")
          (done)))))

(deftest the-written-prep-is-gm-only
  (async done
    (go (let [t (<! (pglite/pglite {}))
              {{cid :id} :campaign} (<! (campaigns/create! t gm {:name "MD"}))
              {code :code} (<! (campaigns/invite! t (:id gm) cid))]
          (<! (campaigns/join! t prance code))
          (is (= "{}" (<! (campaigns/manager-content t (:id gm) cid))))
          (<! (campaigns/save-manager-content! t (:id gm) cid "{\"npcs\": [\"The saboteur\"]}"))
          (is (= ["The saboteur"] (js->clj (.-npcs (js/JSON.parse (<! (campaigns/manager-content t (:id gm) cid)))))))
          (is (= 403 (status (<! (campaigns/manager-content t (:id prance) cid)))))
          (is (= 403 (status (<! (campaigns/save-manager-content! t (:id prance) cid "{}")))))
          (is (= 404 (status (<! (campaigns/manager-content t (:id krum) cid)))))
          (is (not (re-find #"saboteur" (pr-str (<! (campaigns/fetch t (:id prance) cid))))) "a player's view never carries it")
          (done)))))

(deftest the-party-is-the-players-at-the-table
  (async done
    (go (let [t (<! (pglite/pglite {}))
              {{cid :id} :campaign} (<! (campaigns/create! t gm {:name "MD"}))
              {code :code} (<! (campaigns/invite! t (:id gm) cid))]
          (<! (campaigns/join! t prance code))
          (<! (campaigns/join! t krum code))
          (let [{old :character} (<! (characters/create! t (:id prance) {:name "Old Prance"}))
                {pc :character} (<! (characters/create! t (:id prance) {:name "Prance Galavent" :archetype "human" :career "tech"
                                                                        :notes "Private" :ob {:type "debt" :magnitude 10 :attachment "NBN" :why "Owes"}}))
                {elsewhere :character} (<! (characters/create! t (:id krum) {:name "Krum"}))]
            (<! (characters/set-campaign! t (:id prance) (:id old) cid))
            (<! (characters/set-campaign! t (:id prance) (:id pc) cid))
            (let [{members :members} (<! (campaigns/party t (:id gm) cid))
                  by-name (into {} (map (juxt :name identity)) members)]
              (is (= #{"Prance" "Krum"} (set (keys by-name))) "the GM isn't in the party")
              (is (nil? (:character (by-name "Krum"))) "joined, nothing built here yet")
              (is (= (:id krum) (:user-id (by-name "Krum"))))
              (is (not= (:id elsewhere) (get-in by-name ["Krum" :character :id])) "a character outside the campaign doesn't count")
              (is (= {:id (:id pc) :name "Prance Galavent" :archetype "human" :career "tech"
                      :ob {:type "debt" :magnitude 10 :attachment "NBN" :why "Owes"}}
                     (:character (by-name "Prance")))
                  "the latest character, and only what the manager needs")))
          (is (= 403 (status (<! (campaigns/party t (:id prance) cid)))))
          (is (= 404 (status (<! (campaigns/party t (:id "stranger") cid)))))
          (done)))))

(deftest the-manager-is-gm-only
  (async done
    (go (let [t (<! (pglite/pglite {}))
              {{cid :id} :campaign} (<! (campaigns/create! t gm {:name "MD"}))
              {code :code} (<! (campaigns/invite! t (:id gm) cid))]
          (<! (campaigns/join! t prance code))
          (is (= "{}" (<! (campaigns/manager t (:id gm) cid))))
          (<! (campaigns/save-manager! t (:id gm) cid "{\"secrets\": [\"The mayor\"]}"))
          (is (= ["The mayor"] (js->clj (.-secrets (js/JSON.parse (<! (campaigns/manager t (:id gm) cid)))))))
          (is (= 403 (status (<! (campaigns/manager t (:id prance) cid)))) "a player can't read it")
          (is (= 403 (status (<! (campaigns/save-manager! t (:id prance) cid "{}")))))
          (is (= 404 (status (<! (campaigns/manager t (:id krum) cid)))) "nor can a stranger")
          (is (= (:id gm) (<! (campaigns/library-owner t (:id prance) cid))) "a player builds from the GM's library")
          (is (= 404 (status (<! (campaigns/library-owner t (:id krum) cid)))))
          (done)))))

(defn- publish [t id kind slug title body & [number]]
  (db/query t "INSERT INTO chronicle_entries (campaign_id, kind, slug, title, body, number)
               VALUES (CAST(:id AS uuid), :kind, :slug, :title, :body, CAST(:number AS integer))"
            {:id id :kind kind :slug slug :title title :body body :number number}))

(deftest the-chronicle-is-for-members
  (async done
    (go (let [t (<! (pglite/pglite {}))
              id (get-in (<! (campaigns/create! t gm {:name "Manifest Destiny"})) [:campaign :id])
              code (:code (<! (campaigns/invite! t (:id gm) id)))
              _ (<! (campaigns/join! t prance code))
              _ (<! (publish t id "session" "2026-11-04" "A Debt" "# A Debt" 0))
              _ (<! (publish t id "session" "2026-11-11" "The Gate" "# The Gate" 1))
              _ (<! (publish t id "npc" "rook" "Rook" "# Rook"))
              _ (<! (publish t id "pc" "prance" "Prance Galavent" "# Prance"))
              c (:character (<! (characters/create! t (:id prance) {:name "Prance Galavent"})))
              _ (<! (characters/set-campaign! t (:id prance) (:id c) id))
              listed (:entries (<! (chronicle/index t (:id prance) id)))]
          (is (= ["The Gate" "A Debt" "Prance Galavent" "Rook"] (map :title listed)) "sessions newest first, then the wiki")
          (is (not-any? :body listed) "the list has no bodies")
          (is (= "# Rook" (get-in (<! (chronicle/entry t (:id prance) id "npc" "rook")) [:entry :body])))
          (is (= {:name "Prance Galavent"} (get-in (<! (chronicle/entry t (:id gm) id "pc" "prance")) [:entry :character])))
          (is (= 404 (status (<! (chronicle/index t (:id krum) id)))) "strangers can't see it exists")
          (is (= 404 (status (<! (chronicle/entry t (:id krum) id "npc" "rook")))))
          (is (= 404 (status (<! (chronicle/entry t (:id prance) id "npc" "nobody")))))
          (<! (db/query t "DELETE FROM chronicle_entries WHERE campaign_id = CAST(:id AS uuid) AND slug = 'rook'" {:id id}))
          (is (= 404 (status (<! (chronicle/entry t (:id prance) id "npc" "rook")))) "an unpublished page is gone")
          (done)))))
