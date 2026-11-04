(ns ttg.api.story
  "The table's story point pool (CRB p.27-30), {:player n :gm n}, on the campaign. Any member reads it; the GM sets it
   (a new session: one player point per player, the GM starts with 1). Spending flips one point to the other side in a
   single conditional UPDATE, so two players can't both spend the last point. Players spend only player points; the
   GM may spend either, as when they roll a Discouraging Wound on a player's sheet."
  (:require [ttg.api.async :refer [go-try <?]]
            [ttg.api.campaigns :as campaigns]
            [ttg.api.db :as db]
            [ttg.api.service :refer [reject!]]))

(def max-points 20)

(defn- parsed [row] {:story (some-> (:story row) db/parse-json)})

(defn pool
  "{:story {:player n :gm n}}, or {:story nil} before the GM has started a session."
  [d user-id id]
  (go-try
    (<? (campaigns/role! d user-id id))
    (parsed (<? (db/one d (str "SELECT " (db/json-col "story") " FROM campaigns WHERE id = CAST(:id AS uuid)") {:id id})))))

(defn set-pool! [d user-id id {:keys [player gm]}]
  (go-try
    (when-not (= "gm" (<? (campaigns/role! d user-id id))) (reject! 403 "Only the GM can set the story points" {}))
    (parsed (<? (db/one d "UPDATE campaigns SET story = CAST(:story AS jsonb), updated_at = now()
                           WHERE id = CAST(:id AS uuid) RETURNING CAST(story AS text) AS story"
                        {:id id :story (db/->json {:player player :gm gm})})))))

(def ^:private spend-sql
  "UPDATE campaigns SET updated_at = now(), story = CASE WHEN CAST(:side AS text) = 'player'
     THEN jsonb_build_object('player', CAST(story->>'player' AS int) - 1, 'gm', CAST(story->>'gm' AS int) + 1)
     ELSE jsonb_build_object('player', CAST(story->>'player' AS int) + 1, 'gm', CAST(story->>'gm' AS int) - 1) END
   WHERE id = CAST(:id AS uuid) AND CAST(story->>CAST(:side AS text) AS int) > 0
   RETURNING CAST(story AS text) AS story")

(defn spend!
  "Moves one point from side (\"player\" or \"gm\") to the other. 409 when that side has none left."
  [d user-id id side]
  (go-try
    (let [role (<? (campaigns/role! d user-id id))]
      (when (and (= "gm" side) (not= "gm" role)) (reject! 403 "Only the GM spends GM story points" {}))
      (or (some-> (<? (db/one d spend-sql {:id id :side side})) parsed)
          (reject! 409 (str "No " (if (= "gm" side) "GM" "player") " story points left") {})))))
