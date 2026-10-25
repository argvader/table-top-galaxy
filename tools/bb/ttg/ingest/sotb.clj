(ns ttg.ingest.sotb
  "Parsers for Android: Shadow of the Beanstalk. Page indexes match printed page numbers in this book.
   Headings are set in Monkirta (all caps); body text in Minion; archetype stats in Futura."
  (:require [clojure.string :as str]
            [ttg.genesys.model :as model]
            [ttg.ingest.entries :as e]
            [ttg.ingest.lines :as l]
            [ttg.ingest.tables :as t]))

(def ^:private archetype-pages [25 30])
(def ^:private career-pages [32 41])
(def ^:private skill-pages [42 44])
(def ^:private talent-pages [44 52])
(def ^:private talent-table-pages [46 47])
(def ^:private skill-table-page 42)

(defn- monkirta? [line size] (and (str/starts-with? (:font line) "Monkirta") (= size (:size line))))
(defn- source [line] {:book "sotb" :page (:page line)})

(def ^:private small-words #{"a" "an" "and" "of" "the" "on" "in" "to" "for" "or"})
(def ^:private acronyms #{"nbn" "napd" "ai" "un" "bmi" "pad"})
(defn- title-case
  "BOUNTY HUNTER -> Bounty Hunter; keeps small words lower-case after the first, and acronyms (NBN, NAPD) upper-case."
  [s]
  (->> (str/split (str/lower-case s) #" ")
       (map-indexed (fn [i w]
                      (cond (acronyms (str/replace w #"[^a-z]" "")) (str/upper-case w)
                            (and (pos? i) (small-words w)) w
                            :else (str/replace w #"^[^a-z0-9]*[a-z]|(?<=-)[a-z]" str/upper-case))))
       (str/join " ")))

;; ---------- Table 1-2: every talent allowed in the Android setting, with where it is defined ----------
(defn- talent-ref [tier [nm ranked src]]
  (let [page (some-> (re-find #"\d+" (str src)) parse-long)
        book (if (str/includes? (str src) "Core Rulebook") "crb" "sotb")
        nm (str/replace nm #"\s*\n\s*" " ")]
    {:id (l/slug nm) :name nm :tier tier :ranked (= "Yes" ranked) :defined-in {:book book :page page}}))

(defn talent-list [pages]
  (let [[lo hi] talent-table-pages
        rows (for [i (range lo (inc hi)), t (:tables (nth pages i)), r (rest t)] [i r])]
    (loop [[[i r] & more] rows, tier 1, out []]
      (cond
        (nil? r) out
        (re-matches #"Tier \d" (str (first r))) (recur more (parse-long (subs (first r) 5)) out)
        (str/blank? (first r)) (recur more tier out)
        :else (recur more tier (conj out (assoc (talent-ref tier r) :source {:book "sotb" :page i})))))))

;; ---------- Table 1-1: the skills used in the Android setting ----------
(defn skill-list [pages]
  (let [page (nth pages skill-table-page)]
    (vec (for [t (:tables page) :when (= "Skill" (ffirst t))
               [nm ch typ src] (rest t)]
           {:id (l/slug nm) :name nm :characteristic (keyword (str/lower-case ch)) :category typ
            :defined-in (cond-> {:book (if (str/includes? (str src) "Core Rulebook") "crb" "sotb")}
                          (re-find #"\d+" (str src)) (assoc :page (parse-long (re-find #"\d+" src))))
            :source {:book "sotb" :page skill-table-page}}))))

;; ---------- Talents defined in this book ----------
(defn talents [pages]
  (let [[lo hi] talent-pages
        proper (into {} (map (juxt (comp str/upper-case :name) :name)) (talent-list pages))
        boundary? #(and (str/starts-with? (:font %) "Monkirta") (>= (:size %) 11.0))]
    (->> (l/page-lines pages lo hi)
         (e/split-at-headings boundary?)
         (map (fn [[h b]] [[(last h)] b]))
         (filter (fn [[[h] b]] (and (monkirta? h 11.0) (e/field b "Tier"))))
         (map #(e/talent "sotb" (fn [t] (proper (str/upper-case t) (title-case t))) %))
         vec)))

;; ---------- Archetypes: one per page; Futura 16pt stat line, bulleted thresholds and abilities ----------
(defn- archetype [pages i]
  (let [lines (vec (l/page-lines pages i i))
        head (first (filter #(monkirta? % 19.0) lines))
        wound-line (first (filter #(re-find #"Starting Wound Threshold" (:text %)) lines))
        ;; The stat block can start at the foot of one column and continue at the top of the next,
        ;; so read it in reading order from the wound line to the next heading.
        block (->> lines (drop-while #(not= % wound-line))
                   (take-while #(not (str/starts-with? (:font %) "Monkirta"))))
        block-text (l/join-text block)
        stats (->> lines (filter #(and (str/starts-with? (:font %) "Futura") (= 16.0 (:size %)))) (keep #(parse-long (:text %))) (take 6))
        num (fn [label] (some-> (re-find (re-pattern (str label ":\\s*(\\d+)")) block-text) second parse-long))
        abil (->> (e/abilities (str/replace-first block-text #"^.*?Starting Experience:\s*\d+\s*" ""))
                  (remove #(#{"Starting Wound Threshold" "Starting Strain Threshold" "Starting Experience"} (:name %))))]
    (when (and head wound-line)
      (let [nm (title-case (:text head))
            starting (some #(when (= "Starting Skills" (:name %)) (:text %)) abil)]
        (cond-> {:id (l/slug nm) :name nm
                 :characteristics (zipmap model/characteristics stats)
                 :wounds (num "Starting Wound Threshold") :strain (num "Starting Strain Threshold") :xp (num "Starting Experience")
                 :abilities (vec (remove #(= "Starting Skills" (:name %)) abil))
                 :source (source head)}
          starting (assoc :starting-skills starting))))))

(defn archetypes [pages]
  (let [[lo hi] archetype-pages]
    (vec (keep #(some-> (archetype pages %) e/with-grants) (range lo (inc hi))))))

;; ---------- Careers: one per page, with a "Useful Talents" sidebar ----------
(defn- useful-talents [lines]
  (->> (re-seq #"Tier (\d): ([^(]+?) \(" (l/join-text (filter #(= 8.8 (:size %)) lines)))
       (mapv (fn [[_ t nm]] {:tier (parse-long t) :name (str/trim nm) :id (l/slug nm)}))))

(defn- career [pages i]
  (let [lines (vec (l/page-lines pages i i))
        head (first (remove #(= "USEFUL TALENTS" (:text %)) (filter #(monkirta? % 19.0) lines)))
                ;; The career-skills sentence is set in bold; reading only bold lines keeps image-wrapped text out of it.
        ;; The bold run plus one line either side: the sentence starts and ends mid-line.
        ;; Lines must start at the bold text's margin (or a paragraph indent); pull quotes wrapped around art sit elsewhere.
        bold-run (let [b (first (filter #(and (= 10.0 (:size %)) (:bold %)) lines))
                       body (vec (filter #(and (= 10.0 (:size %)) b (= (:col b) (:col %)) (<= (- (:x b) 2) (:x %) (+ (:x b) 12))) lines))
                       idx (keep-indexed #(when (:bold %2) %1) body)]
                   (when (seq idx) (subvec body (max 0 (dec (first idx))) (min (count body) (+ 2 (last idx))))))
        skills (or (e/career-skills (l/join-text (remove nil? bold-run)))
                   (e/career-skills (l/join-text (filter #(= 10.0 (:size %)) lines))))]
    (when (and head skills)
      (let [nm (title-case (:text head))]
        {:id (l/slug nm) :name nm :skills skills :useful-talents (useful-talents lines) :source (source head)}))))

(defn careers [pages]
  (let [[lo hi] career-pages]
    (vec (keep #(career pages %) (range lo (inc hi))))))

;; ---------- New skills: Knowledge (Science/Society/The Net), Computers (Hacking/Sysops) ----------
(def ^:private skill-groups {"KNOWLEDGE SKILLS" ["Knowledge" "Knowledge"] "COMPUTERS SKILLS" ["Computers" "General"]})
(def ^:private skill-head-re #"^(.+?) \((BRAWN|AGILITY|INTELLECT|CUNNING|WILLPOWER|PRESENCE)\)$")

(defn- list-mode [text]
  (cond (re-find #"SHOULD NOT|^USE THIS SKILL" text) :avoid
        (re-find #"SHOULD USE|^THIS SKILL IF" text) :use))

(defn- bullets [lines]
  (->> (str/split (l/join-text lines) #"\s*•\s*") (map str/trim) (remove str/blank?) vec))

(defn- finish-skill [{:keys [entry body use avoid]}]
  (cond-> (assoc entry :text (->> (re-seq #"[^.!?]+[.!?]+" (l/join-text body)) (take 2) (apply str) str/trim))
    (seq use) (assoc :use-when (bullets use))
    (seq avoid) (assoc :avoid-when (bullets avoid))))

(defn- new-skill [group ln]
  (let [[_ sub ch] (re-find skill-head-re (:text ln))
        [parent category] group
        nm (str parent " (" (title-case sub) ")")]
    {:entry {:id (l/slug nm) :name nm :category category :characteristic (keyword (str/lower-case ch))
             :variant-of (l/slug parent) :source (source ln)}
     :mode :body}))

(defn skills [pages]
  (let [[lo hi] skill-pages]
    (loop [[ln & more] (l/page-lines pages lo hi), group nil, cur nil, out []]
      (let [flush #(if cur (conj out (finish-skill cur)) out)
            monk? (str/starts-with? (str (:font ln)) "Monkirta")]
        (cond
          (nil? ln) (flush)
          (skill-groups (:text ln)) (recur more (skill-groups (:text ln)) nil (flush))
          (and group monk? (re-find skill-head-re (:text ln))) (recur more group (new-skill group ln) (flush))
          (and cur monk? (list-mode (:text ln))) (recur more group (assoc cur :mode (list-mode (:text ln))) out)
          monk? (recur more nil nil (flush))
          cur (recur more group (update cur (:mode cur) (fnil conj []) ln) out)
          :else (recur more group cur out))))))

;; ---------- Equipment (Chapter 2) and the Gun Locker supplement ----------
(def ^:private equipment-pages [82 117])
(def ^:private android (constantly "android"))

(defn equipment [pages] (t/book-entries {:book "sotb" :setting-of android} pages (first equipment-pages) (second equipment-pages)))
(defn- of-kind [k pages] (filterv #(= k (:kind %)) (equipment pages)))
(def weapons (partial of-kind :weapon))
(def armor (partial of-kind :armor))
(def gear (partial of-kind :gear))
(def attachments (partial of-kind :attachment))

;; Summary tables (pp.3-6) list every gun; the per-gun cards that follow repeat them, so first copy wins.
(defn gun-locker [pages]
  (filterv #(= :weapon (:kind %)) (t/book-entries {:book "sotb-guns" :setting-of android} pages 0 (dec (count pages)))))

;; ---------- Chapter 3: The Network (pp.129-138) ----------
;; Headings set the context: "NETWORK ACTIONS"/"NETWORK MANEUVERS" (19pt) hold 14pt action entries; the ice section
;; has 14pt ice-type headings over 11pt programs; "ICEBREAKERS" (19pt) holds 11pt programs. Other 19pt headings are sidebars.
(def ^:private network-pages [128 138])
(def ^:private ice-types {"BARRIER ICE" "Barrier" "SENTRY ICE" "Sentry" "CODE-GATE ICE" "Code Gate"})
(def ^:private sections {"NETWORK ACTIONS" :action "NETWORK MANEUVERS" :maneuver "ICEBREAKERS" :icebreaker})

(defn- labeled
  "Splits 'intro Label: value Label: value' into {:intro .. label value}."
  [text labels]
  (let [re (re-pattern (str "(" (str/join "|" labels) "):\\s*"))
        names (map second (re-seq re text))
        parts (str/split text re)]
    (into {:intro (str/trim (first parts))} (map (fn [n v] [n (str/trim v)]) names (rest parts)))))

(defn- re-find* [s re] (re-find re s))

(defn- network-blocks [pages]
  (let [[lo hi] network-pages]
    (->> (l/page-lines pages lo hi)
         (e/split-at-headings #(str/starts-with? (:font %) "Monkirta"))
         (map (fn [[h b]] {:head (last h) :heads h :body b})))))

(defn- network-entry [{:keys [mode ice-type]} {:keys [head body]}]
  ;; A summary table printed at the end of a block (Tables 3-2 to 3-5) is not part of the entry's text.
  (let [text (str/replace (l/join-text body) #"\s*TABLE \d+–\d+:.*$" "")
        nm (title-case (:text head))
        base {:id (l/slug nm) :name nm :source (source head)}]
    (case mode
      (:action :maneuver)
      (let [f (labeled text ["Difficulty" "Available To" "Description"])]
        (cond-> (assoc base :kind mode :text (or (f "Description") (:intro f))
                       :available-to (some-> (f "Available To") (str/split #",\s*") vec))
          (f "Difficulty") (assoc :difficulty (f "Difficulty"))))
      :rule
      (assoc base :kind mode :text text)
      :ice
      (let [f (labeled text ["Program Strength" "Effects" "Additional Rules"])]
        (assoc base :kind :ice :ice-type ice-type :text (:intro f) :strength (some-> (f "Program Strength") (re-find* #"\d+") parse-long)
               :effects (f "Effects") :rules (f "Additional Rules")))
      :icebreaker
      (let [f (labeled text ["Able to Affect" "Override Strength" "Additional Rules"])]
        (assoc base :kind :icebreaker :text (:intro f) :strength (some-> (f "Override Strength") (re-find* #"\d+") parse-long)
               :affects (some->> (f "Able to Affect") (#(str/split % #",\s*")) (mapv #({"Barriers" "Barrier" "Sentries" "Sentry" "Code Gates" "Code Gate"} % %)))
               :rules (f "Additional Rules"))))))

(defn- network-step [{:keys [mode] :as st} {:keys [head heads] :as block}]
  (let [t (:text head), size (:size head)]
    (cond
      ;; A chapter banner (30pt, often followed by a drop cap) opens a rules section such as "Anatomy of Ice".
      (some #(= 30.0 (:size %)) heads) (assoc st :mode :rule)
      (sections t) (assoc st :mode (sections t))
      (and (= 14.0 size) (ice-types t)) (assoc st :mode :ice :ice-type (ice-types t))
      (and (#{:action :maneuver :rule} mode) (= 14.0 size)) (update st :out conj (network-entry st block))
      (and (#{:ice :icebreaker} mode) (= 11.0 size)) (update st :out conj (network-entry st block))
      ;; An entry that stops mid-sentence carries on after a column or page heading; join the next block's text to it.
      (and (seq (:out st)) (not (re-find #"[.!?)”]$" (:text (peek (:out st))))))
      (update st :out (fn [out] (conj (pop out) (update (peek out) :text #(str % " " (l/join-text (:body block)))))))
      :else st)))

(defn- program-prices
  "Ice (p.136) and icebreaker (p.138) tables: name -> {:rarity :price :restricted}."
  [pages]
  (into {} (for [i [136 138], t (:tables (nth pages i)), row (rest t)
                 :let [rp (last row)] :when (and rp (re-find #"/" rp))
                 :let [[r p] (str/split rp #"/")]]
             [(l/slug (first row)) (cond-> {:rarity (parse-long (str/trim r)) :price (parse-long (str/replace p #"[^\d]" ""))}
                                     (str/includes? p "(R)") (assoc :restricted true))])))

(defn network [pages]
  (let [prices (program-prices pages)]
    (->> (reduce network-step {:out []} (network-blocks pages))
         :out
         (mapv #(merge % (prices (:id %)))))))

(defn- results-table [page header]
  (first (filter #(= header (ffirst %)) (:tables page))))

(defn network-results
  "Table 3-1 (spending results in a network encounter, p.130) and the trace table (p.131)."
  [pages]
  (vec (concat
        (for [[sym opts] (rest (results-table (nth pages 130) "Symbols"))
              [i opt] (map-indexed vector (str/split opts #"(?<=\.)\s+(?=[A-Z][^.:]{2,40}:)"))]
          {:id (l/slug (str "network " sym " " i)) :name opt :table :spend :cost sym :source {:book "sotb" :page 130}})
        (for [[traces result] (rest (results-table (nth pages 131) "Traces"))]
          {:id (l/slug (str "trace " traces)) :name result :table :trace :cost traces :source {:book "sotb" :page 131}}))))

;; ---------- Factions (pp.56-77): one "FACTION: X" banner per faction, themed sections under 19pt/14pt headings ----------
;; Each faction's Favors table is left out: Favors are replaced by the Secrets and Obligations house rule.
(def ^:private faction-pages [56 78])

(defn- faction-body? [ln] (and (<= 9.5 (:size ln) 10.5) (not (str/starts-with? (:font ln) "Monkirta"))))

(defn- faction-sections [blocks]
  (->> blocks
       (keep (fn [[h b]]
               (let [text (l/join-text (filter faction-body? b)), title (title-case (l/join-text h))]
                 ;; Favor sections go too: the house rule replaces Favors.
                 (when (and (seq text) (not (re-find #"Favor" title))) {:title title :text text}))))
       vec))

(defn- faction [[[banner] body]]
  (let [nm (title-case (str/replace (:text banner) #"^FACTION:\s*" ""))
        runs (e/split-at-headings #(and (str/starts-with? (:font %) "Monkirta") (#{14.0 19.0} (:size %))) body)
        intro (l/join-text (filter faction-body? (take-while #(not (and (str/starts-with? (:font %) "Monkirta") (#{14.0 19.0} (:size %)))) body)))]
    {:id (l/slug nm) :name nm :kind :faction
     :text intro
     :sections (faction-sections runs)
     :source (source banner)}))

(defn factions [pages]
  (let [[lo hi] faction-pages]
    (->> (l/page-lines pages lo hi)
         (e/split-at-headings #(= 30.0 (:size %)))
         ;; Keep every banner line: "FACTION: ACTIVIST AND" wraps onto "TERRORIST GROUPS"; the drop cap is not a banner line.
         (map (fn [[h b]] [[(assoc (first h) :text (l/join-text (filter #(= 30.0 (:size %)) h)))] b]))
         (filter (fn [[[h] _]] (str/starts-with? (:text h) "FACTION:")))
         (mapv faction))))

;; ---------- Vehicles (pp.114-118): Monkirta 14pt type headings, Futura stats; model names are 19pt sidebars ----------
(def ^:private vehicle-pages [113 118])
(def ^:private vehicle-layout
  {:stat? #(and (str/starts-with? (:font %) "Futura-Condens") (#{12.0 16.0} (:size %)) (re-matches #"[+-]?\d+" (:text %)))
   :ability? (constantly false)
   :body-font "MinionPro"})

(defn vehicles [pages]
  (let [[lo hi] vehicle-pages]
    (->> (l/page-lines pages lo hi)
         (e/split-at-headings #(and (str/starts-with? (:font %) "Monkirta") (#{14.0 19.0} (:size %))))
         (map (fn [[h b]] [[(update (last h) :text title-case)] b]))
         (keep #(e/vehicle "sotb" vehicle-layout %))
         (mapv #(assoc % :setting "android")))))
