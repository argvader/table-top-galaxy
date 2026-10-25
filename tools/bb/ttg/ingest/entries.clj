(ns ttg.ingest.entries
  "Entry shapes shared by every Genesys book: talent blocks, ability bullets, career skill lists."
  (:require [clojure.string :as str]
            [ttg.ingest.lines :as l]))

(defn split-at-headings
  "Groups lines into [heading-lines body-lines] runs, starting a run at each heading line.
   Consecutive heading lines (a name wrapped onto two lines) stay in one heading."
  [heading? lines]
  (->> (partition-by heading? (drop-while (complement heading?) lines))
       (partition-all 2)
       (map (fn [[h b]] [h (vec b)]))))

(defn field [lines label]
  (some #(second (re-find (re-pattern (str "^•?\\s*" label ":\\s*(.+)")) (:text %))) lines))

(defn talent
  "A talent block: Tier: / Activation: / Ranked: lines, rules text, and the CRB's optional settings line."
  [book display-name [[head] body]]
  (let [text (l/join-text (remove #(re-find #"^(Tier|Activation|Ranked):" (:text %)) body))
        [_ rules settings] (re-find #"^(.*?)\s*(You should use this talent in .*?settings?[^.]*\.)?\s*$" text)
        nm (display-name (:text head))]
    (cond-> {:id (l/slug nm) :name nm
             :tier (some-> (field body "Tier") (str/replace #"\D" "") parse-long)
             :activation (field body "Activation")
             :ranked (= "Yes" (field body "Ranked"))
             :text rules :source {:book book :page (:page head)}}
      settings (assoc :settings settings))))

(def ^:private ability-re #"(?:^|\s)•?\s*([A-Z][A-Za-z’'!\- ]{2,40}):\s")

(defn abilities
  "'• Name: text • Name: text' -> [{:name :text}]"
  [text]
  (let [names (map second (re-seq ability-re text))
        parts (rest (str/split text ability-re))]
    (mapv (fn [n t] {:name (str/trim n) :text (str/trim (str/replace t "•" ""))}) names parts)))

(defn skill-list
  "'Brawl, Coercion, ... and Streetwise' -> [\"Brawl\" \"Coercion\" ... \"Streetwise\"]"
  [s]
  (->> (str/split (str/replace s #",?\s+and\s+" ", ") #",\s*")
       (map str/trim) (remove str/blank?) vec))

(defn career-skills
  "The eight skills from 'counts the following skills as career skills: ...'."
  [text]
  (some-> (re-find #"(?:counts|count) the following skills as career\s+skills:\s*(.+?)\.\s" (str text " ")) second skill-list))

;; ---------- Archetype starting grants, read from the rules text ----------
(def ^:private number-words {"one" 1 "two" 2 "three" 3})

(defn free-skills
  "'starts with one rank in Athletics' -> {:ranks 1 :skills [\"Athletics\"]}
   'two ranks in each of two different skills' -> {:ranks 2 :choose 2}
   'one rank in each of two different non-career skills' -> {:ranks 1 :choose 2 :non-career true}"
  [text]
  (when-let [text (some-> text (str/replace #"\s+" " "))]
    (if-let [[_ r n nc] (re-find #"(one|two) ranks? in each of (one|two|three) different (non-career )?skills" text)]
      (cond-> {:ranks (number-words r) :choose (number-words n)} nc (assoc :non-career true))
      (when-let [[_ r sk] (re-find #"(one|two) ranks? in ([A-Z][\w ()]+?)(?: during| at|\.)" text)]
        {:ranks (number-words r) :skills [(str/trim sk)]}))))

(defn free-item
  "'begins play with one cybernetic ... that costs a total of 1,000 credits or less' -> {:group \"Cybernetics\" :max-price 1000}"
  [abilities]
  (some (fn [{:keys [text]}]
          (when-let [[_ kind price] (re-find #"begins play with one ([\w-]+) .*?costs a total of ([\d,]+) credits or less" (str text))]
            {:group ({"cybernetic" "Cybernetics" "g-mod" "G-Mods"} kind kind) :max-price (parse-long (str/replace price "," ""))}))
        abilities))

(defn with-grants [archetype]
  (let [fs (free-skills (:starting-skills archetype))
        fi (free-item (:abilities archetype))]
    (cond-> archetype fs (assoc :free-skills fs) fi (assoc :free-item fi))))

;; ---------- Adversary stat blocks (CRB and EPG layout) ----------
;; "Name [Rival]" heading; Bebas Bold 13pt numbers: optional three power levels (EPG), six characteristics, then
;; soak, wound threshold, [strain threshold], melee/ranged defense; then Skills:/Talents:/Abilities:/Equipment: fields.
(def ^:private adversary-head-re #"^(.+?)\s*[\[(](Minion|Rival|Nemesis)[\])]$")
(def ^:private adversary-fields ["Skills" "Talents" "Abilities" "Equipment" "Spells" "Skills \\(group only\\)"])

(defn- bebas-regular? [ln] (and (str/starts-with? (:font ln) "BebasNeue") (not (re-matches #"\d+" (:text ln)))))

(defn- labeled-fields [text labels]
  (let [re (re-pattern (str "(" (str/join "|" labels) "):\\s*"))
        names (map second (re-seq re text))
        parts (str/split text re)]
    (into {:intro (str/trim (first parts))} (map (fn [n v] [(str/replace n #"\s*\(.*" "") (str/trim v)]) names (rest parts)))))

(defn- skill-ranks
  "'Athletics 3, Brawl 4, Ranged (Light) 2' -> [{:name \"Athletics\" :rank 3} ...]; minion group skills have no rank."
  [s]
  (when s
    (->> (str/split (str/replace s #"\.$" "") #",\s*(?![^()]*\))")
         (map str/trim) (remove str/blank?)
         (mapv #(let [[_ n r] (re-find #"^(.*?)\s*(\d+)?$" %)] (cond-> {:name n} r (assoc :rank (parse-long r))))))))

(defn- adversary [book [[head] body]]
  (let [[_ nm kind] (re-find adversary-head-re (:text head))
        ;; Stat numbers sit in rows: [3 power levels (EPG)], 6 characteristics, then soak / wounds / [strain] / M / R.
        ;; Rows are found by position, so a block that straddles a column break still reads in the right order.
        rows (->> body
                  (filter #(and (str/starts-with? (:font %) "BebasNeue") (= 13.0 (:size %)) (re-matches #"\d+" (:text %))))
                  (group-by (juxt :page :col #(Math/round (/ (:y %) 4.0))))
                  (sort-by key)
                  (map (fn [[_ ls]] (mapv #(parse-long (:text %)) (sort-by :x ls)))))
        [before [chars & after]] (split-with #(not= 6 (count %)) rows)
        pw (last (filter #(= 3 (count %)) before))
        dv (vec (apply concat after))
        strain? (= "Nemesis" kind)
        f (labeled-fields (l/join-text (filter #(str/starts-with? (:font %) "MinionPro") body)) adversary-fields)]
    (when (and nm (= 6 (count chars)))
      (cond-> {:id (l/slug nm) :name nm :type (str/lower-case kind)
               :characteristics (zipmap ["brawn" "agility" "intellect" "cunning" "willpower" "presence"] chars)
               :soak (nth dv 0 nil) :wounds (nth dv 1 nil)
               :defense {:melee (nth dv (if strain? 3 2) nil) :ranged (nth dv (if strain? 4 3) nil)}
               :text (:intro f) :source {:book book :page (:page head)}}
        strain? (assoc :strain (nth dv 2 nil))
        pw (assoc :power {:combat (nth pw 0) :social (nth pw 1) :general (nth pw 2)})
        (f "Skills") (assoc :skills (skill-ranks (f "Skills")))
        (f "Talents") (assoc :talents (f "Talents"))
        (f "Abilities") (assoc :abilities (f "Abilities"))
        (f "Equipment") (assoc :equipment (f "Equipment"))
        (f "Spells") (assoc :spells (f "Spells"))))))

(defn adversaries
  "Every adversary stat block on page indexes lo..hi."
  [book pages lo hi]
  (->> (l/page-lines pages lo hi)
       (split-at-headings #(and (bebas-regular? %) (>= (:size %) 10.4)))
       (map (fn [[h b]] [[(last h)] b]))
       (filter (fn [[[h] _]] (re-find adversary-head-re (:text h))))
       (keep #(adversary book %))
       vec))

;; ---------- Vehicle stat blocks (EPG and SotB) ----------
;; Stat numbers come in three rows: silhouette / speed / handling, defense(s) / armor, hull trauma / system strain.
;; Rows are found by position so the block reads right whichever column it sits in.
(def ^:private vehicle-fields ["Control Skill" "Complement" "Compliment" "Passenger Capacity" "Consumables"
                               "Encumbrance Capacity" "Price/Rarity" "Weapons" "Hard Points" "Sensors"])

(defn- unspace
  "Labels are sometimes letter-spaced ('P r i c e / R a r i t y :'); close any run of 4+ single spaced characters."
  [s]
  (str/replace s #"(?<!\S)(?:\S ){3,}\S(?!\S)" #(str/replace % " " "")))

(defn- stat-rows [nums]
  (->> (sort-by (juxt :page :col :y :x) nums)
       (reduce (fn [rows n]
                 (let [r (peek rows)]
                   (if (and r (= (:page n) (:page (first r))) (= (:col n) (:col (first r))) (= (:size n) (:size (first r)))
                            (< (abs (- (:y n) (:y (first r)))) 3.5))
                     (conj (pop rows) (conj r n))
                     (conj rows [n]))))
               [])
       (mapv (fn [r] (mapv #(parse-long (str/replace (:text %) "+" "")) (sort-by :x r))))))

(defn vehicle
  "One vehicle from its heading and body. stat? picks the stat-number lines; ability? the ability headings."
  [book {:keys [stat? ability? body-font]} [[head] body]]
  (let [[[sil speed handling] dfa [ht ss]] (stat-rows (filter stat? body))
        armor (peek dfa)
        body-lines (filter #(and (str/starts-with? (:font %) body-font) (not (stat? %))) body)
        text (unspace (l/join-text body-lines))
        f (labeled-fields text vehicle-fields)
        [_ price rarity] (some->> (f "Price/Rarity") (re-find #"([\d,]+)(?:\s*\([^)]*\))?\s*(?:credits)?\s*/\s*(\d+)"))
        abilities (->> (split-at-headings ability? body)
                       (map (fn [[h b]] {:name (str/join " " (map :text h))
                                         :text (l/join-text (filter #(str/starts-with? (:font %) body-font) b))})))]
    (when (and sil (f "Control Skill"))
      (cond-> {:id (l/slug (:text head)) :name (:text head) :kind :vehicle
               :silhouette sil :speed speed :handling handling
               :defense (vec (pop dfa)) :armor armor :hull-trauma ht :system-strain ss
               :control-skill (str/replace (f "Control Skill") #"\.$" "")
               :text (:intro f) :source {:book book :page (:page head)}}
        (or (f "Complement") (f "Compliment")) (assoc :complement (or (f "Complement") (f "Compliment")))
        (f "Passenger Capacity") (assoc :passengers (f "Passenger Capacity"))
        (f "Encumbrance Capacity") (assoc :encumbrance-capacity (some->> (f "Encumbrance Capacity") (re-find #"\d+") parse-long))
        (f "Consumables") (assoc :consumables (f "Consumables"))
        (f "Weapons") (assoc :weapons (str/replace (f "Weapons") #"\s*You should use this vehicle.*$" ""))
        price (assoc :price (parse-long (str/replace price "," "")) :rarity (parse-long rarity))
        (some->> (f "Price/Rarity") (re-find #"\(R\)")) (assoc :restricted true)
        (re-find #"You should use this vehicle in" text)
        (assoc :settings-note (second (re-find #"(You should use this vehicle in [^.]+\.)" text)))
        (seq abilities) (assoc :abilities (vec abilities))))))
