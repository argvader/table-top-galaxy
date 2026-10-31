(ns ttg.ui.text
  "Book text marks dice and symbols as [ability], [advantage] ...; these draw them as the same icons the roller uses."
  (:require [clojure.string :as str]
            [ttg.library :as lib]
            [ttg.ui.dice :as dice]))

(defn- token [part i]
  (let [k (some-> (second (re-matches #"\[(\w+)\]" part)) keyword)]
    (cond (dice/symbol-name k) ^{:key i} [:span.glyph {:title (name k)} [dice/sym k]]
          (dice/die-info k) ^{:key i} [:span.glyph.die-i [dice/die k nil (:name (dice/die-info k))]]
          :else part)))

(defn rich [text]
  (into [:<>] (map-indexed (fn [i part] (token part i))) (remove str/blank? (str/split (or text "") #"(?=\[\w+\])|(?<=\])"))))

(defn cite [source]
  (when source [:span.cite (lib/cite source)]))

(defn pool-dice
  "The small dice beside a skill."
  [{:keys [proficiency ability]}]
  (into [:<>] (concat (repeat proficiency [dice/die :proficiency nil "Proficiency"])
                      (repeat ability [dice/die :ability nil "Ability"]))))
