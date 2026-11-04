(ns ttg.story.db
  "The table's story point pool (CRB p.27-30), under :story. :campaign-id is the campaign it was loaded for and :pool
   is {:player n :gm n}, nil before the GM starts a session. :busy? is a spend or change on its way to the server;
   :note is the last thing that happened to the pool, for a status line. There is no live sync yet (ttg-1kf): the pool
   is fetched when a sheet or campaign opens, and every spend returns the server's current count."
  (:require [clojure.spec.alpha :as s]))

(def max-points 20)

(s/def ::player (s/int-in 0 (inc max-points)))
(s/def ::gm (s/int-in 0 (inc max-points)))
(s/def ::pool (s/nilable (s/keys :req-un [::player ::gm])))
(s/def ::campaign-id (s/nilable string?))
(s/def ::busy? boolean?)
(s/def ::note (s/nilable string?))
(s/def ::story (s/keys :req-un [::campaign-id ::pool ::busy? ::note]))
(s/def ::app-db (s/keys :req-un [::story]))

(def default-story {:campaign-id nil :pool nil :busy? false :note nil})

(defn new-session
  "A session's starting pool: one player point per player, and 1 for the GM."
  [players]
  {:player (min max-points players) :gm 1})
