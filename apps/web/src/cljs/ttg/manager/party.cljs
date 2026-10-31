(ns ttg.manager.party
  "The manager's list of PCs, built from the campaign's members. A PC is keyed by the member's user id, not the
   character's, so Session 0 answers survive a player who joins before building or who rebuilds. Character detail is
   read here and never copied into the manager's document."
  (:require [clojure.string :as str]))

(def ^:private colors 5)

(defn- humanize [id]
  (when id (->> (str/split (name id) #"-") (map str/capitalize) (str/join " "))))

(defn- corp-for
  "The corp this Obligation is tied to, if its free text names one."
  [corps attachment]
  (let [text (str/lower-case (or attachment ""))]
    (when-not (str/blank? text)
      (some (fn [{:keys [id name short] :as corp}]
              (when (some #(and % (str/includes? text (str/lower-case %))) [name short id]) corp))
            corps))))

(defn- seated
  "Members in seat order; anyone not seated yet sits at the end, in the order they joined."
  [members seats]
  (let [position (zipmap seats (range))]
    (sort-by #(get position (:user-id %) (count seats)) members)))

;; A character keeps a default magnitude until an Obligation is chosen, so only :type says they carry one.
(defn- pc [corps i {:keys [user-id name character]}]
  (let [{:keys [archetype career ob]} character]
    (cond-> {:id user-id :player name :name (or (:name character) name) :built? (some? character)
             :color (str "var(--pc-" (inc (mod i colors)) ")")
             :debt (if (:type ob) (:magnitude ob) 0) :owes (:attachment ob) :why (:why ob) :debt-type (humanize (:type ob))
             :creditor (:id (corp-for corps (:attachment ob)))
             :character-id (:id character) :portrait-key (:portrait-key character) :theme (:theme character)}
      archetype (assoc :archetype (humanize archetype))
      career (assoc :role (humanize career)))))

(defn party
  "The PCs, in seat order."
  [members corps seats]
  (vec (map-indexed #(pc corps %1 %2) (seated members seats))))

(defn first-name [{:keys [name]}] (first (str/split (or name "") #" ")))

(defn left-of
  "The next player round the table, for the crew questions."
  [pcs id]
  (when (> (count pcs) 1)
    (let [i (first (keep-indexed #(when (= id (:id %2)) %1) pcs))]
      (:id (nth pcs (mod (inc (or i 0)) (count pcs)))))))

(defn crew-debt [pcs] (reduce + (map :debt pcs)))

(defn move
  "Seat order after moving a player one place: by -1 for earlier, 1 for later."
  [pcs id by]
  (let [ids (mapv :id pcs)
        i (.indexOf ids id)
        j (+ i by)]
    (if (and (>= i 0) (< -1 j (count ids)))
      (assoc ids i (ids j) j id)
      ids)))
