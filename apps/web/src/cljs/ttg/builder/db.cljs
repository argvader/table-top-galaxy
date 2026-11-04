(ns ttg.builder.db
  "The builder's slice of app-db, under :builder. :character is the character being built (ttg.genesys.character);
   the rest is where the player is in the wizard. :campaign-id is where a new character goes; :editing? means this one
   already exists, so saving updates it. :info is the gear or talent open in the info drawer. New characters are kept in localStorage until they're saved."
  (:require [clojure.spec.alpha :as s]
            [ttg.genesys.rules :as rules]))

(s/def ::step (s/int-in 0 (count rules/steps)))
(s/def ::visited set?)
(s/def ::character map?)
(s/def ::xp-tab #{:chars :skills :talents})
(s/def ::gear-tab #{:weapon :armor :gear})
(s/def ::q string?)
(s/def ::tq string?)
(s/def ::gq string?)
(s/def ::career-only? boolean?)
(s/def ::tier (s/int-in 0 6))
(s/def ::preview? boolean?)
(s/def ::saving? boolean?)
(s/def ::editing? boolean?)
(s/def ::campaign-id (s/nilable string?))
(s/def ::error (s/nilable string?))
(s/def ::load-error (s/nilable string?))
(s/def :ttg.builder.db.info/kind #{:gear :talent})
(s/def :ttg.builder.db.info/id string?)
(s/def :ttg.builder.db.info/closing? boolean?)
(s/def ::info (s/nilable (s/keys :req-un [:ttg.builder.db.info/kind :ttg.builder.db.info/id]
                                 :opt-un [:ttg.builder.db.info/closing?])))
(s/def ::builder (s/keys :req-un [::step ::visited ::character ::xp-tab ::gear-tab ::q ::tq ::gq ::career-only? ::tier
                                  ::preview? ::saving? ::editing? ::campaign-id ::error ::load-error ::info]))
(s/def ::app-db (s/keys :req-un [::builder]))

(defn default-builder []
  {:step 0 :visited #{0} :character (rules/blank-character) :xp-tab :chars :gear-tab :weapon :q "" :tq "" :gq ""
   :career-only? false :tier 0 :preview? false :saving? false :editing? false :campaign-id nil :error nil :load-error nil :info nil})

;; Campaign data (Manifest Destiny), not book content.
(def corps ["Haas-Bioroid" "Jinteki" "NBN" "Weyland Consortium"])
(def session-0-question "The Concord's manifest lists one more crate than you loaded. What was in it, and why didn't you report it?")
(def motive-hints {"Desire" "What they want most"
                   "Fear" "What they avoid at any cost"
                   "Strength" "What they can count on"
                   "Flaw" "What gets them in trouble"})
