(ns ttg.manager.db
  "The manager's slice of app-db, under :manager. :doc is what the GM records, :content the written prep it's seeded
   from, and :party the members. Saving follows the sheet: edits count up in :dirty, replies count up :sent."
  (:require [clojure.spec.alpha :as s]))

(s/def ::status #{:idle :loading :ready :failed :no-prep})
(s/def ::campaign-id (s/nilable string?))
(s/def ::dirty nat-int?)
(s/def ::sent nat-int?)
(s/def ::sending? boolean?)
(s/def ::problem (s/nilable #{:signin :failed}))
(s/def ::doc (s/nilable map?))
(s/def ::content (s/nilable map?))
(s/def ::members vector?)
(s/def ::ui map?)
(s/def ::manager (s/keys :req-un [::status ::campaign-id ::dirty ::sent ::sending? ::problem ::doc ::content ::members ::ui]))

(def default-ui {:tab :s0 :s0-tab :run :block nil :drawer nil :draw-for nil :group "all" :query "" :act-view nil})

(def default-manager
  {:status :idle :campaign-id nil :dirty 0 :sent 0 :sending? false :problem nil :doc nil :content nil :members [] :ui default-ui})
