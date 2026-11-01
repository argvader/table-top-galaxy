(ns ttg.chronicle.db
  "The Chronicle's slice of app-db, under :chronicle: the list of pages or the one page open. :kind and :slug say which
   page was asked for, so a late reply for another page is dropped."
  (:require [clojure.spec.alpha :as s]))

(s/def ::status #{:idle :loading :ready :error})
(s/def ::campaign-id (s/nilable string?))
(s/def ::kind (s/nilable string?))
(s/def ::slug (s/nilable string?))
(s/def ::entries (s/coll-of map? :kind vector?))
(s/def ::entry (s/nilable map?))
(s/def ::error (s/nilable string?))
(s/def ::chronicle (s/keys :req-un [::status ::campaign-id ::kind ::slug ::entries ::entry ::error]))
(s/def ::app-db (s/keys :req-un [::chronicle]))

(def default-chronicle
  {:status :idle :campaign-id nil :kind nil :slug nil :entries [] :entry nil :error nil})
