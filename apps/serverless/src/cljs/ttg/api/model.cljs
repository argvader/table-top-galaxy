(ns ttg.api.model
  "Shapes at the API boundary. Library entries are checked against the content specs in the service. Characters and
   campaigns are jsonb documents, so they must be JSON-safe, and bounded: the Data API answers at most 1 MiB."
  (:require [clojure.spec.alpha :as s]
            [ttg.genesys.character :as character]
            [ttg.library :as lib]))

(def max-import 5000)
(def max-doc-chars 300000)
(def max-manager-chars 700000)
(def max-portrait-chars 360000)   ; base64 of ttg.api.characters/max-portrait-bytes

(s/def ::owner (s/and string? #(re-matches #"[A-Za-z0-9_-]{1,128}" %)))
(s/def ::type ::lib/type)
(s/def ::id (s/and string? #(re-matches #"[a-z0-9][a-z0-9-]{0,127}" %)))
(s/def ::uuid (s/and string? #(re-matches #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}" %)))
(s/def ::code (s/and string? #(re-matches #"[A-Za-z0-9_-]{12}" %)))

(s/def ::entry ::lib/entry)
(s/def ::put-body (s/keys :req-un [::entry]))

(s/def ::entries (s/and (s/map-of ::type (s/coll-of ::lib/entry :kind sequential?))
                        #(<= (reduce + (map count (vals %))) max-import)))
(s/def ::import-body (s/keys :req-un [::entries]))

(s/def ::name (s/and string? #(<= 1 (count (.trim %)) 120)))
(s/def ::fits #(<= (count (pr-str %)) max-doc-chars))
(s/def ::json-safe character/json-safe?)

(s/def ::theme (s/nilable string?))
(s/def ::version pos-int?)
(s/def ::character (s/and (s/keys :req-un [::name] :opt-un [::theme]) ::fits))
(s/def ::character-body (s/keys :req-un [::character]))
(s/def ::saved-character (s/and ::character (s/keys :req-un [::version])))
(s/def ::character-put (s/keys :req-un [:ttg.api.model.put/character]))
(s/def :ttg.api.model.put/character ::saved-character)

(s/def ::image (s/and string? #(<= (count %) max-portrait-chars)))
(s/def ::portrait-body (s/keys :req-un [::image]))

;; :gm holds GM-only campaign data; players never receive it.
(s/def ::gm map?)
(s/def ::campaign (s/and (s/keys :req-un [::name] :opt-un [::gm]) ::fits))
(s/def ::campaign-body (s/keys :req-un [::campaign]))

(s/def ::chronicle-kind #{"session" "pc" "npc" "faction" "location"})
(s/def ::chronicle-slug (s/and string? #(re-matches #"[a-z0-9-]{1,120}" %)))

;; The story point pool (ttg.api.story).
(s/def :ttg.api.model.story/player (s/int-in 0 21))
(s/def :ttg.api.model.story/gm (s/int-in 0 21))
(s/def ::story (s/keys :req-un [:ttg.api.model.story/player :ttg.api.model.story/gm]))
(s/def ::story-body (s/keys :req-un [::story]))
(s/def ::side #{"player" "gm"})
(s/def ::spend-body (s/keys :req-un [::side]))

(s/def ::campaign-id (s/nilable ::uuid))
(s/def ::link-body (s/keys :req-un [::campaign-id]))
