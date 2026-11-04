(ns ttg.sheet.db
  "The character sheet's slice of app-db, under :sheet. :dirty counts edits and :sent is the edit count last sent, so
   autosave knows what's left to save. :conflict? means this copy is out of date (a 409) and saving has stopped.
   :last-crit is the critical injury roll to show on the Crits tab. :info is the gear or talent open in the info drawer."
  (:require [clojure.spec.alpha :as s]
            [ttg.config :as config]))

(s/def ::status #{:idle :loading :ready :error})
(s/def ::character (s/nilable map?))
(s/def ::tab #{:skills :combat :talents :gear :crits :story :notes})
(s/def ::q string?)
(s/def ::hit string?)
(s/def ::error (s/nilable string?))
(s/def ::conflict? boolean?)
(s/def ::dirty nat-int?)
(s/def ::sent nat-int?)
(s/def ::saving? boolean?)
(s/def ::uploading? boolean?)
(s/def ::last-crit (s/nilable map?))
(s/def :ttg.sheet.db.info/kind #{:gear :talent})
(s/def :ttg.sheet.db.info/id string?)
(s/def :ttg.sheet.db.info/closing? boolean?)
(s/def ::info (s/nilable (s/keys :req-un [:ttg.sheet.db.info/kind :ttg.sheet.db.info/id]
                                 :opt-un [:ttg.sheet.db.info/closing?])))
(s/def ::sheet (s/keys :req-un [::status ::character ::tab ::q ::hit ::last-crit ::error ::conflict? ::dirty ::sent ::saving? ::uploading?]
                       :opt-un [::info]))
(s/def ::app-db (s/keys :req-un [::sheet]))

(def default-sheet
  {:status :idle :character nil :tab :skills :q "" :hit "" :last-crit nil :error nil :conflict? false
   :dirty 0 :sent 0 :saving? false :uploading? false :info nil})

;; The server says who's asking: the owner, or the GM of the character's campaign, who can fix or override it.
;; Without :access (an older API) only the owner writes.
(defn access [db]
  (let [me (or (get-in db [:session :id]) config/dev-owner)
        {:keys [owner access]} (get-in db [:sheet :character])]
    (or access (when (= me owner) "owner"))))

(defn writable? [db] (contains? #{"owner" "gm"} (access db)))
