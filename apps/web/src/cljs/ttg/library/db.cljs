(ns ttg.library.db
  "The library page's slice of app-db, under :library.
   :entries mirrors the server ({type {id entry}}); :draft is the entry open in the editor, held as the
   strings its inputs show; :import walks a file through pick -> preview -> running -> done."
  (:require [clojure.spec.alpha :as s]
            [ttg.library :as lib]))

(def import-chunk 100)   ; entries per request: keeps each one well inside API Gateway's 30s limit

(s/def ::status #{:idle :loading :ready :error})
(s/def ::error (s/nilable string?))
(s/def ::entries (s/map-of ::lib/type (s/map-of keyword? map?)))
(s/def ::query string?)
(s/def ::toast (s/nilable string?))

(s/def ::path (s/coll-of keyword? :kind vector?))
(s/def ::strings (s/map-of ::path (s/or :text string? :abilities vector?)))
(s/def ::errors (s/map-of ::path string?))
(s/def ::type ::lib/type)
(s/def ::base map?)
(s/def ::new? boolean?)
(s/def ::saving? boolean?)
(s/def ::confirm-delete? boolean?)
(s/def ::draft (s/nilable (s/keys :req-un [::type ::base ::strings ::errors ::new? ::saving? ::confirm-delete?])))

(s/def ::step #{:pick :preview :running :done})
(s/def ::file-name (s/nilable string?))
(s/def ::csv-type ::lib/type)
(s/def ::rows (s/coll-of map? :kind vector?))
(s/def ::notes (s/coll-of string? :kind vector?))
(s/def ::saved nat-int?)
(s/def ::import (s/nilable (s/keys :req-un [::step ::csv-type ::rows ::notes ::saved] :opt-un [::file-name ::error])))

(s/def ::library (s/keys :req-un [::status ::error ::entries ::query ::draft ::import ::toast]))
(s/def ::app-db (s/keys :req-un [::library]))

(def default-library
  {:status :idle :error nil :entries {} :query "" :draft nil :import nil :toast nil})
