(ns ttg.ingest.lines
  "Reads .local/books/<slug>/pages.jsonl into a flat, reading-order seq of lines tagged with their page."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn pages [slug]
  (with-open [r (io/reader (str (fs/path ".local/books" slug "pages.jsonl")))]
    (mapv #(json/parse-string % true) (line-seq r))))

(defn- page-chrome?
  "Running headers and footers: book name, part title, page number."
  [{:keys [text size y]}]
  (or (< size 8.5)
      ;; Page numbers sit in the top or bottom margin; stat-block digits elsewhere on the page are kept.
      (and (or (< y 45) (> y 715)) (re-matches #"\d{1,3}" text))
      (str/starts-with? text "Part I")))

(defn- gutter
  "x where the right column starts: the widest gap between body-text line starts in the middle of the page."
  [lines]
  ;; Only x positions shared by 3+ body lines count as column starts, so a stray indented line can't split a column.
  (let [xs (->> lines (filter #(<= 9.5 (:size %) 10.5)) (map #(Math/round (double (:x %)))) (filter #(< 150 % 450))
                frequencies (filter #(>= (val %) 3)) (map key) sort)
        lefts (->> lines (filter #(<= 9.5 (:size %) 10.5)) (map :x) (filter #(< % 150)))
        start (if (seq lefts) (apply min lefts) 0)
        gaps (map (fn [a b] [(- b a) b]) (cons start xs) xs)]
    (if (seq gaps) (second (apply max-key first gaps)) 300)))

(defn- reorder
  "Re-derives the column of each line from the page's own gutter, then sorts into reading order.
   A full-width banner (20pt+) splits the page into bands; each band reads left column, then right."
  [lines]
  ;; Bullets and hanging indents start a little left of the column's body text, hence the 20pt allowance.
  (let [g (- (gutter lines) 20)
        breaks (->> lines (filter #(>= (:size %) 20)) (map :y) sort)
        band (fn [y] (count (filter #(<= % y) breaks)))]
    (->> lines
         (map #(assoc % :col (if (>= (:x %) g) 1 0) :band (band (:y %))))
         (sort-by (juxt :band :col :y :x)))))

(defn page-number
  "The printed page number, trusted only when it is near the PDF index: stat-block digits at the page edge
   can be mistaken for it (EPG adversary pages read as \"5\")."
  [{:keys [printed index]}]
  (if (and printed (<= (abs (- printed index)) 3)) printed index))

(defn page-lines
  "Lines from page indexes lo..hi inclusive, chrome removed, each tagged with :page."
  [pages lo hi]
  (for [p (subvec pages lo (inc hi))
        l (reorder (:lines p))
        :when (not (page-chrome? l))]
    (assoc l :page (page-number p))))

(defn join-text
  "Joins wrapped lines into one string, healing words hyphenated across a line break."
  [lines]
  ;; A break after "word-" usually splits one word ("char-acter", "non-vehi-cle"); the hyphen stays only when the
  ;; piece before it is a joining word inside a compound ("out-of-" + "turn", "up-to-" + "date").
  (-> (reduce (fn [acc t]
                (if (str/ends-with? acc "-")
                  (let [piece (last (str/split (subs acc 0 (dec (count acc))) #"[\s-]"))
                        compound? (and (re-find #"-[^\s-]+-$" acc) (#{"of" "to" "in" "on" "the" "a" "and" "by" "at" "for"} piece))]
                    (if compound? (str acc t) (str (subs acc 0 (dec (count acc))) t)))
                  (str acc " " t)))
              "" (map :text lines))
      (str/replace #"\s+" " ")
      ;; A dropped page number can leave "pool. ." behind.
      (str/replace #"\.\s+\.$" ".")
      str/trim))

(defn slug [s]
  (-> s str/lower-case (str/replace #"[’'!]" "") (str/replace #"[^a-z0-9]+" "-") (str/replace #"^-|-$" "") keyword))
