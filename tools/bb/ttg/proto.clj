(ns ttg.proto
  "Assembles prototypes/src/<page>.html + <page>.js + shared dice code into prototypes/<page>.html."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [ttg.ingest :as ingest]))

(def src "prototypes/src")

(defn- ascii-escape
  "Escapes non-ASCII so pages render under any charset: \\uXXXX in JS, &#xXXXX; in HTML."
  [s fmt]
  (str/join (map (fn [c] (if (< (int c) 128) (str c) (format fmt (int c)))) s)))

(defn- read-src [& parts] (slurp (str (apply fs/path src parts))))

;; What each page reads; pages not listed get the builder and sheet set. The rest of the library (Network, vehicles,
;; tones) waits for the pages that use it, which keeps the pages small.
(def ^:private sheet-types
  [:archetypes :careers :skills :talents :weapons :armor :gear :motivations :obligation-types :crits :spend-results :item-qualities])

(def ^:private page-types
  {"gm-dashboard" [:archetypes :careers :skills :adversaries :factions :obligation-types :crits :spend-results]})

;; Fields a page never shows: factions keep their name, adversaries drop settings outside the Android table.
(def ^:private android-adversary-settings #{"general" "modern" "science-fiction" "space-opera"})

(defn- slim-type [type entries]
  (case type
    :factions (mapv #(select-keys % [:id :name :source]) entries)
    :adversaries (filterv #(android-adversary-settings (:setting %)) entries)
    entries))

(defn- content-js
  "The Android library as a JS constant. :sources chains are dropped; :source (book + page) stays for citations."
  [page]
  (let [content (select-keys (:content (ingest/setting-library "android")) (get page-types page sheet-types))
        slim (into {} (map (fn [[t es]] [t (slim-type t (mapv #(dissoc % :sources) es))])) content)]
    (str "const CONTENT = " (json/generate-string slim) ";")))

(defn- page-js [page]
  (-> (read-src (str page ".js"))
      (str/replace "/*CONTENT*/" (if (str/includes? (read-src (str page ".js")) "/*CONTENT*/") (content-js page) ""))
      (str/replace "/*DICE_CORE*/" (read-src "shared" "dice-core.js"))
      (str/replace "/*DICE_SVG*/" (read-src "shared" "dice-svg.js"))
      (ascii-escape "\\u%04X")))

(defn build-page! [page]
  (let [html (-> (read-src (str page ".html"))
                 (ascii-escape "&#x%X;")
                 (str/replace "{{SYMBOLS}}" (read-src "shared" "symbols.html"))
                 (str/replace "{{SCRIPT}}" (page-js page)))
        out  (str (fs/path "prototypes" (str page ".html")))]
    (spit out html)
    (println "Built" out)))

(defn build! []
  (doseq [f (fs/glob src "*.html")]
    (build-page! (str/replace (str (fs/file-name f)) #"\.html$" ""))))
