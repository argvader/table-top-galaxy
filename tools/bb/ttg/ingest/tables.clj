(ns ttg.ingest.tables
  "Equipment tables shared by every Genesys book: weapons, armor, gear and attachments.
   Headers vary by book ('Encum' / 'Encumbrance' / 'enc', 'HP' / 'Hard Points'), so they are normalized first."
  (:require [clojure.string :as str]
            [ttg.ingest.lines :as l]))

(defn num [s]
  (when-let [m (some->> s (re-find #"[+-]?\d[\d,]*"))] (parse-long (str/replace m #"[,+]" ""))))

(defn qualities
  "'Disorient 3, Stun Damage' -> [{:name \"Disorient\" :rating 3} {:name \"Stun Damage\"}]"
  [s]
  (->> (str/split (or s "") #",\s*")
       (map #(str/trim (str/replace % #"\s+" " "))) (remove #(or (str/blank? %) (#{"–" "-" "—"} %)))
       (mapv #(let [[_ n r] (re-find #"^(.*?)\s*(\d+)?$" %)] (cond-> {:name n} r (assoc :rating (parse-long r)))))))

(defn- price [s]
  (cond-> {:price (num s)} (and s (re-find #"\(R\)" s)) (assoc :restricted true)))

(def ^:private header-aliases
  {"name" "Name" "skill" "Skill" "dam" "Dam" "crit" "Crit" "range" "Range" "enc" "Encum" "encum" "Encum"
   "encumbrance" "Encum" "hp" "HP" "hard points" "HP" "price" "Price" "rarity" "Rarity" "special" "Special"
   "item" "Item" "type" "Type" "defense" "Defense" "soak" "Soak" "attachment" "Attachment" "damage" "Damage"})

(defn- header-name [h] (some-> h str/trim (str/replace #"\s+" " ") (#(get header-aliases (str/lower-case %) %))))

(defn- expand-header
  "Splits header cells the extractor merged ('Price Rarity', 'Encum Price') and drops blank spacer cells."
  [raw]
  (vec (mapcat (fn [h]
                 (let [parts (some-> h (str/split #"\s+"))]
                   (cond (str/blank? h) []
                         (get header-aliases (str/lower-case h)) [(header-name h)]
                         (and (< 1 (count parts)) (every? #(get header-aliases (str/lower-case %)) parts)) (mapv header-name parts)
                         :else [(header-name h)])))
               raw)))

(defn- table-kind [header]
  (let [h (set header)]
    (cond (h "Skill") :weapon (h "Soak") :armor (h "Attachment") :attachment (or (h "Item") (h "Encum")) :gear)))

(defn- clean [s] (some-> s (str/replace #"\s*\n\s*" " ") str/trim not-empty))

(defn- hard-points [m] (when-let [hp (num (m "HP"))] {:hard-points hp}))

(defn- row->entry [kind header row]
  (let [m (zipmap header (map clean row))
        nm (clean (first row))]
    (case kind
      :weapon (merge {:name nm :skill (m "Skill") :crit (num (m "Crit")) :range (m "Range")
                      :encumbrance (num (m "Encum")) :rarity (num (m "Rarity")) :qualities (qualities (m "Special"))}
                     (if (str/starts-with? (str (m "Dam")) "+") {:damage-bonus (num (m "Dam")) :adds-brawn true} {:damage (num (m "Dam"))})
                     (hard-points m) (price (m "Price")))
      :armor  (merge {:name nm :defense (or (num (m "Defense")) 0) :soak (or (num (m "Soak")) 0)
                      :encumbrance (num (m "Encum")) :rarity (num (m "Rarity"))}
                     (hard-points m) (price (m "Price")))
      :attachment (merge {:name nm :rarity (num (m "Rarity"))} (hard-points m) (price (m "Price")))
      :gear   (merge (let [enc (m "Encum")]
                       ;; "+4" on a backpack raises the carrier's encumbrance threshold rather than adding weight.
                       (if (str/starts-with? (str enc) "+")
                         {:name nm :encumbrance 0 :encumbrance-bonus (num enc) :rarity (num (m "Rarity"))}
                         {:name nm :encumbrance (num enc) :rarity (num (m "Rarity"))}))
                     (when (m "Damage") {:damage (num (m "Damage"))})
                     (price (m "Price"))))))

(defn- compact
  "Some tables come out with blank spacer columns. Drop blank headers and empty cells; if the row is then one
   cell short, the missing one is the trailing Special column."
  [header row]
  (let [hs (vec (remove str/blank? header))
        vs (vec (remove #(str/blank? (str %)) row))]
    (cond (= (count hs) (count vs)) [hs vs]
          (= (count hs) (inc (count vs))) [hs (conj vs nil)])))

(defn table-entries
  "Entries from one extracted table. opts: :book, :setting-of (page index -> setting name)."
  [{:keys [book setting-of]} page table]
  (let [raw-header (mapv header-name (first table))
        expanded (expand-header (first table))
        ;; Merged headers: the data rows still have every column, so the expanded header lines up with them.
        aligned? (= (count expanded) (count (first (rest table))))
        spaced? (and (not aligned?) (some str/blank? raw-header))
        header (cond aligned? expanded spaced? (vec (remove str/blank? raw-header)) :else raw-header)
        kind (table-kind header)
        setting (setting-of (:index page))]
    (when (and kind (not (#{"Repair Required" "Size"} (first header))))
      (loop [[row & more] (rest table), group nil, out []]
        (let [[h r] (if spaced? (compact raw-header row) [header row])]
          (cond
            (nil? row) out
            (nil? r) (recur more group out)
            (str/blank? (str (first r))) (recur more group out)
            ;; Group rows ("Melee Weapons", "Slugthrowers") have a name and nothing else.
            (every? #(str/blank? (str %)) (rest r)) (recur more (clean (first r)) out)
            :else (let [e (row->entry kind h r)]
                    (recur more group
                           (conj out (cond-> (assoc e :kind kind :setting setting
                                                    :id (l/slug (if (= "general" setting) (:name e) (str (:name e) " " setting)))
                                                    :source {:book book :page (or (:printed page) (:index page))})
                                       group (assoc :group group)))))))))))

(defn book-entries
  "All table entries on page indexes lo..hi, keeping the first entry for each id (later copies are card repeats)."
  [opts pages lo hi]
  (->> (for [p (subvec pages lo (inc hi)), t (:tables p), e (table-entries opts p t)] e)
       (reduce (fn [[seen out] e] (if (seen (:id e)) [seen out] [(conj seen (:id e)) (conj out e)])) [#{} []])
       second))
