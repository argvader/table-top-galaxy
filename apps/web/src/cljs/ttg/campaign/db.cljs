(ns ttg.campaign.db
  "The campaigns page's slice of app-db, under :table: your campaigns and characters, the campaign open now, and the
   invite being accepted."
  (:require [clojure.spec.alpha :as s]))

(s/def ::status #{:idle :loading :ready :error})
(s/def ::error (s/nilable string?))
(s/def ::campaigns (s/coll-of map? :kind vector?))
(s/def ::characters (s/coll-of map? :kind vector?))
(s/def ::current (s/nilable map?))
(s/def ::invite-code (s/nilable string?))
(s/def ::copied? boolean?)
(s/def ::join (s/nilable map?))
(s/def ::table (s/keys :req-un [::status ::error ::campaigns ::characters ::current ::invite-code ::copied? ::join]))
(s/def ::app-db (s/keys :req-un [::table]))

(def default-table
  {:status :idle :error nil :campaigns [] :characters [] :current nil :invite-code nil :copied? false :join nil})
