(ns ttg.ingest.crb
  "Parsers for the Genesys Core Rulebook layout. Page indexes match printed page numbers in this book."
  (:require [clojure.string :as str]
            [ttg.genesys.model :as model]
            [ttg.ingest.entries :as e :refer [split-at-headings field abilities]]
            [ttg.ingest.lines :as l]
            [ttg.ingest.tables :as t]))

(def ^:private archetype-pages [36 39])
(def ^:private career-pages [40 45])
(def ^:private talent-pages [72 81])

(defn- source [line] {:book "crb" :page (:page line)})
(defn- bebas? [line size] (and (str/starts-with? (:font line) "BebasNeue") (= size (:size line))))

;; ---------- Talents: Name / Tier: / Activation: / Ranked: / text / "You should use this talent in ..." ----------
(defn talents [pages]
  ;; Any display-font line ends an entry, so sidebars ("Why Dedication?") and tier banners don't
  ;; bleed into the talent above them. Only runs whose body has a Tier: line are talents.
  (let [[lo hi] talent-pages
        boundary? #(and (str/starts-with? (:font %) "BebasNeue") (>= (:size %) 10.4))]
    (->> (l/page-lines pages lo hi)
         (split-at-headings boundary?)
         ;; A tier banner ("Tier 2") directly above a talent lands in the same heading run; the name is the last line.
         (map (fn [[h b]] [[(last h)] b]))
         (filter (fn [[[h] b]] (and (bebas? h 10.4) (field b "Tier"))))
         (map #(e/talent "crb" identity %)) vec)))

;; ---------- Archetypes: stat block in whichever column holds "Starting Skills" ----------
(defn- archetype [pages page-index]
  (let [lines (vec (l/page-lines pages page-index page-index))
        head  (first (filter #(re-find #"^Archetype \d+:" (:text %)) lines))
        skills-line (first (filter #(str/starts-with? (:text %) "Starting Skills") lines))
        col   (filter #(= (:col skills-line) (:col %)) lines)
        stats (->> col (filter #(bebas? % 13.0)) (keep #(parse-long (:text %))) (take 6))
        num   (fn [label] (some #(some-> (re-find (re-pattern (str label ":\\s*(\\d+)")) (:text %)) second parse-long) col))
        after (filter #(>= (:y %) (:y skills-line)) col)
        abil  (abilities (l/join-text after))]
    (when (and head skills-line)
      {:id (l/slug (str/replace (:text head) #"^Archetype \d+:\s*(The )?" ""))
       :name (str/replace (:text head) #"^Archetype \d+:\s*(The )?" "")
       :characteristics (zipmap model/characteristics stats)
       :wounds (num "Starting Wound Threshold") :strain (num "Starting Strain Threshold") :xp (num "Starting Experience")
       :starting-skills (:text (first (filter #(= "Starting Skills" (:name %)) abil)))
       :abilities (vec (remove #(= "Starting Skills" (:name %)) abil))
       :source (source head)})))

(defn archetypes [pages]
  (let [[lo hi] archetype-pages]
    (vec (keep #(some-> (archetype pages %) e/with-grants) (range lo (inc hi))))))

;; ---------- Skills: category banners (24pt), "Name (Characteristic)" headings, sub-skills (11.2pt) ----------
(def ^:private skill-pages [54 70])
(def ^:private skill-head-re #"^(.+?) \((Brawn|Agility|Intellect|Cunning|Willpower|Presence)\)$")

(defn- first-sentences [text n]
  (->> (re-seq #"[^.!?]+[.!?]+(?:[”\"])?" text) (take n) (apply str) str/trim))

(defn- bullets [lines]
  (->> (str/split (l/join-text lines) #"\s*•\s*") (map str/trim) (remove str/blank?) vec))

(defn- skill-entry [{:keys [category parent head body use avoid]}]
  (let [[_ nm ch] (re-find skill-head-re (:text head))
        skill (or nm (:text head))]
    (cond-> {:id (l/slug skill) :name skill :category category
             :characteristic (if ch (keyword (str/lower-case ch)) (:characteristic parent))
             :text (first-sentences (l/join-text body) 2) :source (source head)}
      (seq use) (assoc :use-when (bullets use))
      (seq avoid) (assoc :avoid-when (bullets avoid))
      (and parent (not nm)) (assoc :variant-of (:id parent)))))

(defn- list-mode
  "The 11.2pt 'Your character should (not) use this skill if…' headings switch which list lines go to."
  [text]
  (cond (re-find #"should not use|^not use this skill" text) :avoid
        (re-find #"^Your character should" text) :use))

(defn- skill-step [{:keys [cur parent] :as st} ln]
  (let [close (fn [st] (cond-> (assoc st :cur nil) cur (update :out conj (skill-entry cur))))
        start (fn [st head parent] (assoc (close st) :cur {:category (:category st) :head head :parent parent :mode :body}))]
    (cond
      (bebas? ln 24.0) (-> (close st) (assoc :category (str/replace (:text ln) #" Skills$" "") :parent nil))
      (and (bebas? ln 12.8) (re-find skill-head-re (:text ln)))
      (let [st (start st ln nil)] (assoc st :parent (skill-entry (:cur st))))
      (and (bebas? ln 11.2) cur (list-mode (:text ln))) (assoc-in st [:cur :mode] (list-mode (:text ln)))
      (and (bebas? ln 11.2) parent) (start st ln parent)
      ;; Any other display heading is a sidebar: park its lines so they don't become skill text.
      (str/starts-with? (:font ln) "Bebas") (cond-> st cur (assoc-in [:cur :mode] :ignore))
      cur (update-in st [:cur (:mode cur)] (fnil conj []) ln)
      :else st)))

(defn skills [pages]
  (let [[lo hi] skill-pages
        st (reduce skill-step {:out []} (l/page-lines pages lo hi))]
    (:out (skill-step st {:font "BebasNeue" :size 0 :text ""}))))

;; ---------- Careers: "The X counts the following skills as career skills: a, b, ... and h." ----------
(defn- career [[head body]]
  (let [text (l/join-text body)
        skills (e/career-skills text)]
    (when skills
      {:id (l/slug (str/trim (first (str/split (l/join-text head) #"\(")))) :name (str/trim (first (str/split (l/join-text head) #"\(")))
       :skills skills
       :text (str/trim (str/replace (l/join-text head) #"^[^(]*" ""))
       :source (source (first head))})))

(defn careers [pages]
  (let [[lo hi] career-pages]
    (->> (l/page-lines pages lo hi) (split-at-headings #(bebas? % 11.2)) (keep career) vec)))

;; ---------- Equipment: weapon, armor and gear tables (generic chapter + each setting's gear section) ----------
(def ^:private equipment-pages [82 189])
;; Chapter starts as PDF indexes, from the book's TOC.
(def ^:private settings [[0 "general"] [138 "fantasy"] [148 "steampunk"] [158 "weird-war"] [164 "modern"] [170 "science-fiction"] [179 "space-opera"]])
(defn- setting-of [page] (second (last (filter #(<= (first %) page) settings))))

(defn equipment [pages]
  (let [[lo hi] equipment-pages]
    (t/book-entries {:book "crb" :setting-of setting-of} pages lo hi)))

(defn- of-kind [k pages] (filterv #(= k (:kind %)) (equipment pages)))
(def weapons (partial of-kind :weapon))
(def armor (partial of-kind :armor))
(def gear (partial of-kind :gear))

;; ---------- Item qualities: "Name (Passive|Active)" headings, pp.86-89 ----------
(def ^:private quality-pages [86 89])
(def ^:private quality-re #"^(.+?) \((Passive|Active)\)$")

(declare equipment)

(defn item-qualities [pages]
  ;; Ranked when the text talks about a rating, or any item in the gear tables lists it with one ("Accurate 1").
  (let [[lo hi] quality-pages
        rated (->> (equipment pages) (mapcat :qualities) (filter :rating) (map :name) set)
        boundary? #(and (str/starts-with? (:font %) "BebasNeue") (>= (:size %) 10.4))]
    (->> (l/page-lines pages lo hi)
         (split-at-headings boundary?)
         (map (fn [[h b]] [(last h) b]))
         (keep (fn [[h b]]
                 (when-let [[_ nm act] (and (bebas? h 10.4) (re-find quality-re (:text h)))]
                   {:id (l/slug nm) :name nm :activation (str/lower-case act)
                    :ranked (boolean (or (rated nm) (re-find #"\brating\b" (l/join-text b))))
                    :text (l/join-text b) :source (source h)})))
         vec)))

;; ---------- Critical injuries: the d100 table on p.115 ----------
(def ^:private crit-page 115)

(defn- d100-range [s]
  (let [[lo hi] (map parse-long (re-seq #"\d+" s))]
    [lo (if (str/ends-with? (str/trim s) "+") nil (or hi lo))]))

(defn- crit-row [page [roll severity result]]
  (let [[_ nm text] (re-find #"^([^:]+):\s*(.*)$" result)]
    {:id (l/slug nm) :name nm :roll (d100-range roll)
     :severity (count (re-seq #"\[difficulty\]" (str severity)))
     :text text :source {:book "crb" :page (or (:printed page) (:index page))}}))

(defn crits [pages]
  (let [page (nth pages crit-page)]
    (->> (:tables page)
         (filter #(= "d100" (ffirst %)))
         first rest
         (mapv #(crit-row page %)))))

;; ---------- Motivations: Tables I.2-2 to I.2-5 (d100 example Desires, Fears, Strengths, Flaws) ----------
(def ^:private motivation-pages {47 "Desire" 48 "Fear" 49 "Strength" 50 "Flaw"})

(defn motivations [pages]
  (vec (for [[i type] (sort motivation-pages)
             :let [page (nth pages i)]
             t (:tables page) :when (str/starts-with? (str (ffirst t)) "d100")
             [roll result] (rest t)
             :let [[_ nm text] (re-find #"^([^:]+):\s*(.*)$" result)]]
         {:id (l/slug (str type " " nm)) :type type :name nm :roll (let [[lo hi] (map parse-long (re-seq #"\d+" roll))] [lo (if (zero? hi) 100 hi)])
          :text text :source {:book "crb" :page (or (:printed page) i)}})))

;; ---------- Spending results: Tables I.6-2 (Advantage, Triumph) and I.6-3 (Threat, Despair), p.104 ----------
(def ^:private spend-page 104)

(defn- spend-cost
  "'[advantage] [advantage] or [triumph]' -> {:advantage 2 :or-triumph true}; '[triumph] [triumph]' -> {:triumph 2}"
  [s]
  (let [n #(count (re-seq (re-pattern (str "\\[" % "\\]")) s))
        [main alt] (str/split s #"\s+or\s+")]
    (cond-> {}
      (pos? (n "advantage")) (assoc :advantage (count (re-seq #"\[advantage\]" main)))
      (pos? (n "threat")) (assoc :threat (count (re-seq #"\[threat\]" main)))
      (and (not alt) (re-find #"triumph" main)) (assoc :triumph (count (re-seq #"\[triumph\]" main)))
      (and (not alt) (re-find #"despair" main)) (assoc :despair (count (re-seq #"\[despair\]" main)))
      (and alt (re-find #"triumph" alt)) (assoc :or-triumph true)
      (and alt (re-find #"despair" alt)) (assoc :or-despair true))))

(defn- spend-options
  "One table cell lists several options; each starts a new sentence. Follow-on sentences ('This could...') stay with theirs."
  [text]
  (reduce (fn [out s] (if (and (seq out) (re-find #"^(This|These|The effects|Although)" s)) (conj (pop out) (str (peek out) " " s)) (conj out s)))
          ;; Split only at ". " before a capital, so "Table I.6–10" stays whole.
          [] (map str/trim (str/split text #"(?<=[.)])\s+(?=[A-Z])"))))

(defn spend-results [pages]
  (let [page (nth pages spend-page)]
    (vec (for [[side t] (map vector [:positive :negative] (filter #(= "Cost" (ffirst %)) (:tables page)))
               [cost text] (rest t)
               [i opt] (map-indexed vector (spend-options text))]
           (let [c (spend-cost cost)]
             {:id (l/slug (str (name side) " " (str/join " " (mapcat (fn [[k v]] [(name k) v]) (sort c))) " " i))
              :name opt :side side :cost c
              :crit (boolean (re-find #"^Inflict a Critical Injury" opt))
              :source {:book "crb" :page spend-page}})))))

;; ---------- Adversaries: the setting chapters' sample adversaries (pp.147-189) ----------
(defn adversaries [pages]
  (mapv #(assoc % :setting (setting-of (get-in % [:source :page]))) (e/adversaries "crb" pages 120 189)))
