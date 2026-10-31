(ns ttg.library.io
  "Strings in, entries out (and back): the editor form, CSV import/export and JSON import share these parsers.
   Nothing here touches the network or the file system."
  (:require [clojure.string :as str]
            [ttg.library.fields :as fields]))

;; ---------- One field <-> one string ----------

(def ^:private truthy #{"true" "yes" "y" "1" "x"})
(def ^:private falsy #{"false" "no" "n" "0" ""})

(defn- int-error [{:keys [min max]}]
  (cond (and min max) (str "Must be a whole number from " min " to " max)
        min (str "Must be a whole number, " min " or more")
        :else "Must be a whole number"))

(defn- parse-int [field s]
  (let [n (when (re-matches #"-?\d+" s) (parse-long s))
        {:keys [min max]} field]
    (if (and n (or (nil? min) (>= n min)) (or (nil? max) (<= n max)))
      {:ok n}
      {:error (int-error field)})))

(defn- split-list [s] (into [] (comp (map str/trim) (remove str/blank?)) (str/split s #";")))

(defn- parse-quality [s]
  (if-let [[_ nm r] (re-matches #"(.*?)\s+(\d+)" s)]
    {:name nm :rating (parse-long r)}
    {:name s}))

(defn- parse-ability [s]
  (let [[nm text] (str/split s #":\s*" 2)]
    (if text {:name (str/trim nm) :text (str/trim text)} {:name "Ability" :text (str/trim nm)})))

(defn- parse-list [{:keys [count]} s]
  (let [xs (split-list s)]
    (if (and count (not= count (clojure.core/count xs)))
      {:error (str "Needs exactly " count ", separated by ; (has " (clojure.core/count xs) ")")}
      {:ok xs})))

(defn- parse-choice [{:keys [options]} s]
  (if-let [o (first (filter #(= (str/lower-case %) (str/lower-case s)) options))]
    {:ok o}
    {:error (str "One of: " (str/join ", " options))}))

(defn- parse-filled [{:keys [kind] :as field} s]
  (case kind
    (:text :long) {:ok s}
    :int (parse-int field s)
    :bool (cond (truthy (str/lower-case s)) {:ok true}
                (falsy (str/lower-case s)) {:ok false}
                :else {:error "Yes or no"})
    :choice (parse-choice field s)
    :list (parse-list field s)
    :qualities {:ok (mapv parse-quality (split-list s))}
    :abilities {:ok (mapv parse-ability (remove str/blank? (map str/trim (str/split s #"\|"))))}))

(defn parse-cell
  "{:ok value}, {:omit true} or {:error message}. The abilities editor hands over a vector, which passes through."
  [{:keys [kind blank] :as field} v]
  (let [s (if (string? v) (str/trim v) v)]
    (cond
      (vector? s) {:ok s}
      (boolean? s) {:ok s}
      (and (str/blank? s) (= :bool kind)) (if (= :required blank) {:ok false} {:omit true})
      (and (str/blank? s) (= :qualities kind)) {:ok []}     ; weapons always carry a (maybe empty) qualities list
      (str/blank? s) (case blank :omit {:omit true} :nil {:ok nil} :required {:error "Required"})
      :else (parse-filled field s))))

(defn- quality-str [{:keys [name rating]}] (if rating (str name " " rating) name))

(defn cell
  "The string a field shows for an entry: the inverse of parse-cell."
  [{:keys [kind path]} entry]
  (let [v (get-in entry path)]
    (cond
      (nil? v) ""
      (= :list kind) (str/join "; " v)
      (= :qualities kind) (str/join "; " (map quality-str v))
      (= :abilities kind) (str/join " | " (map #(str (:name %) ": " (:text %)) v))
      (= :bool kind) (if v "yes" "no")
      :else (str v))))

;; ---------- A whole form ----------

(defn- dissoc-in [m [k & ks]]
  (if ks (update m k dissoc-in ks) (dissoc m k)))

(defn apply-strings
  "Applies {path string} onto base, one field at a time. Paths not in strings keep base's value.
   Returns {:entry e :errors {path message}}."
  [type base strings]
  (reduce (fn [acc {:keys [path] :as field}]
            (if-not (contains? strings path)
              acc
              (let [{:keys [ok omit error]} (parse-cell field (get strings path))]
                (cond error (assoc-in acc [:errors path] error)
                      omit (update acc :entry dissoc-in path)
                      :else (assoc-in acc (into [:entry] path) ok)))))
          {:entry base :errors {}}
          (fields/by-type type)))

(defn strings
  "{path string} for every field of type: what the editor form starts from."
  [type entry]
  (into {} (map (juxt :path #(cell % entry))) (fields/by-type type)))

;; ---------- CSV ----------

(defn read-csv
  "RFC 4180: quoted cells may hold commas, quotes (doubled) and newlines. Returns a vector of row vectors."
  [text]
  (let [n (count text)
        end-cell (fn [cell row] (conj row (apply str cell)))]
    (loop [i 0, cell [], row [], rows [], quoted? false]
      (if (>= i n)
        (let [row (end-cell cell row)]
          (if (= [""] row) rows (conj rows row)))
        (let [c (nth text i)]
          (cond
            (and quoted? (= c \") (< (inc i) n) (= \" (nth text (inc i)))) (recur (+ i 2) (conj cell c) row rows true)
            (= c \") (recur (inc i) cell row rows (not quoted?))
            quoted? (recur (inc i) (conj cell c) row rows true)
            (= c \,) (recur (inc i) [] (end-cell cell row) rows false)
            (= c \return) (recur (inc i) cell row rows false)
            (= c \newline) (recur (inc i) [] [] (conj rows (end-cell cell row)) false)
            :else (recur (inc i) (conj cell c) row rows false)))))))

(defn- csv-cell [s]
  (if (re-find #"[\",\r\n]" s) (str "\"" (str/replace s "\"" "\"\"") "\"") s))

(defn write-csv [rows]
  (str (str/join "\r\n" (map #(str/join "," (map csv-cell %)) rows)) "\r\n"))

(def ^:private extra-headers ["id" "book" "page"])

(defn- norm [s] (-> s str/lower-case (str/replace #"[^a-z0-9]+" "")))

(defn- header->field
  "Matches a CSV header to a field by its label (\"Hard points\") or key (\"hard-points\")."
  [type h]
  (let [k (norm h)]
    (first (filter #(or (= k (norm (:label %))) (= k (norm (name (last (:path %)))))) (fields/by-type type)))))

(defn to-csv
  "Entries of one type as CSV text; with no entries it is a header-only template."
  [type entries]
  (let [fs (fields/by-type type)]
    (write-csv (cons (into (mapv :label fs) extra-headers)
                     (for [e entries]
                       (into (mapv #(cell % e) fs)
                             [(name (:id e)) (get-in e [:source :book] "") (str (get-in e [:source :page] ""))]))))))

(defn slug
  "\"Heavy Gauss Rifle\" -> :heavy-gauss-rifle"
  [s]
  (let [k (-> (str s) str/lower-case (str/replace #"[^a-z0-9]+" "-") (str/replace #"^-+|-+$" ""))]
    (keyword (if (str/blank? k) "entry" k))))

(defn- row-source [{:strs [book page]}]
  (let [p (some-> page str/trim not-empty parse-long)]
    (if (and (not (str/blank? book)) p) {:book (str/trim book) :page p} {:book "custom"})))

(defn- csv-row [type columns defaults line row]
  (let [cells (zipmap (map :header columns) (concat row (repeat "")))
        strings (into {} (keep (fn [{:keys [header field]}] (when field [(:path field) (cells header)]))) columns)
        extra (into {} (keep (fn [{:keys [header extra]}] (when extra [extra (cells header)]))) columns)
        {:keys [entry errors]} (apply-strings type defaults strings)
        id (or (some-> (get extra "id") str/trim not-empty keyword) (slug (:name entry)))]
    {:line line
     :entry (assoc entry :id id :source (row-source extra))
     :errors errors}))

(defn from-csv
  "CSV text for one type -> {:rows [{:line n :entry e :errors {path msg}}] :ignored [header ...]}.
   Columns the file leaves out keep the type's defaults; blank rows are skipped."
  [type text]
  (let [[headers & rows] (read-csv text)
        columns (mapv (fn [h] (let [x (some #{(norm h)} extra-headers)]
                                {:header h :field (when-not x (header->field type h)) :extra x}))
                      headers)]
    {:ignored (vec (keep #(when-not (or (:field %) (:extra %)) (:header %)) columns))
     :rows (vec (for [[i row] (map-indexed vector rows) :when (some (complement str/blank?) row)]
                  (csv-row type columns (fields/defaults type {}) (+ 2 i) row)))}))

;; ---------- JSON ----------

;; JSON has no keywords: these keys hold keyword values in the content specs.
(def ^:private keyword-valued #{:id :table :side :kind :characteristic :variant-of})

(defn- json->clj
  "Keywordizes map keys (except under string-key-field) and the values of keyword-valued keys."
  [v string-key-field]
  (cond (map? v) (into {} (map (fn [[k x]]
                                 (let [k (keyword k)]
                                   [k (cond (and (keyword-valued k) (string? x)) (keyword x)
                                            (= k string-key-field) x
                                            :else (json->clj x nil))])))
                       v)
        (sequential? v) (mapv #(json->clj % nil) v)
        :else v))

(defn from-json-entry
  "One entry parsed from JSON with string keys. Adversary characteristics keep string keys, as their spec wants."
  [type m]
  (json->clj m (when (= :adversaries type) :characteristics)))

(defn from-json
  "Parsed JSON (string keys) -> {:entries {type [entry]} :unknown [type-name]}.
   Takes our export ({\"format\": \"ttg-library\", \"entries\": {...}}) or a bare {\"talents\": [...]} map."
  [known-types data]
  (let [by-type (if (map? (get data "entries")) (get data "entries") data)]
    (when-not (map? by-type)
      (throw (ex-info "Expected a JSON object of {type: [entries]}" {:reason :shape})))
    (reduce-kv (fn [acc t es]
                 (let [type (keyword t)]
                   (if (and (known-types type) (sequential? es))
                     (assoc-in acc [:entries type] (mapv #(from-json-entry type %) es))
                     (update acc :unknown conj t))))
               {:entries {} :unknown []}
               (dissoc by-type "format" "version" "setting"))))
