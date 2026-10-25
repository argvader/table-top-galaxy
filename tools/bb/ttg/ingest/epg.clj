(ns ttg.ingest.epg
  "Parsers for the Genesys Expanded Player's Guide. Same fonts and layout as the Core Rulebook.
   Only the parts the Android table uses: adversary creation and samples, vehicles, and the Heist and Noir tones."
  (:require [clojure.string :as str]
            [ttg.ingest.entries :as e]
            [ttg.ingest.lines :as l]))

(def ^:private sample-adversary-pages [86 94])

(defn adversaries [pages]
  (mapv #(assoc % :setting "general") (e/adversaries "epg" pages (first sample-adversary-pages) (second sample-adversary-pages))))

;; ---------- Sample vehicles (pp.64-74): Bebas 11.2 names, Bebas Bold 13 stats, Bebas 10 small-caps abilities ----------
(def ^:private vehicle-pages [63 74])
(def ^:private vehicle-layout
  {:stat? #(and (str/starts-with? (:font %) "BebasNeueBold") (= 13.0 (:size %)) (re-matches #"[+-]?\d+" (:text %)))
   :ability? #(and (str/starts-with? (:font %) "BebasNeueRegular") (= 10.0 (:size %)))
   :body-font "MinionPro"})

(defn vehicles [pages]
  (let [[lo hi] vehicle-pages]
    (->> (l/page-lines pages lo hi)
         (e/split-at-headings #(and (str/starts-with? (:font %) "BebasNeueRegular") (>= (:size %) 11.2)))
         (map (fn [[h b]] [[(last h)] b]))
         (keep #(e/vehicle "epg" vehicle-layout %))
         vec)))

;; ---------- New tones (pp.110-113): Heist and Noir, each with themed sections and a new rule (Heat, Internal Monologue) ----------
(def ^:private tone-pages [109 112])
(defn- bebas [ln] (str/starts-with? (:font ln) "BebasNeue"))

(defn tones [pages]
  (let [[lo hi] tone-pages]
    (->> (l/page-lines pages lo hi)
         (e/split-at-headings #(and (bebas %) (= 24.0 (:size %))))
         (mapv (fn [[[h] body]]
                 {:id (l/slug (:text h)) :name (:text h) :kind :tone
                  :text (l/join-text (take-while #(not (and (bebas %) (>= (:size %) 10.4))) (filter #(or (bebas %) (<= 9.5 (:size %) 10.5)) body)))
                  ;; Sections under a "New Rules: X" heading (until the next 11.2pt heading) are part of that rule.
                  :sections (->> (e/split-at-headings #(and (bebas %) (<= 10.4 (:size %) 12.8)) body)
                                 (reduce (fn [{:keys [rule out]} [hs b]]
                                           (let [h (first hs)
                                                 rule (cond (re-find #"^New Rules?:" (:text h)) (:text h)
                                                            (= 10.4 (:size h)) rule
                                                            (= 11.2 (:size h)) nil
                                                            :else rule)
                                                 t (l/join-text (filter #(and (not (bebas %)) (<= 9.5 (:size %) 10.5)) b))]
                                             {:rule rule
                                              :out (cond-> out (seq t) (conj (cond-> {:title (str/join " " (map :text hs)) :text t}
                                                                               rule (assoc :rule rule))))}))
                                         {:rule nil :out []})
                                 :out)
                  :source {:book "epg" :page (:page h)}})))))
