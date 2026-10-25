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

;; What the builder and sheet read. The rest of the library (factions, Network, adversaries, vehicles, tones) waits
;; for the pages that use it, which keeps these pages small.
(def ^:private page-types
  [:archetypes :careers :skills :talents :weapons :armor :gear :motivations :obligation-types :crits :spend-results :item-qualities])

(defn- content-js
  "The Android library as a JS constant. :sources chains are dropped; :source (book + page) stays for citations."
  []
  (let [content (select-keys (:content (ingest/setting-library "android")) page-types)
        slim (update-vals content (fn [es] (mapv #(dissoc % :sources) es)))]
    (str "const CONTENT = " (json/generate-string slim) ";")))

(defn- page-js [page]
  (-> (read-src (str page ".js"))
      (str/replace "/*CONTENT*/" (if (str/includes? (read-src (str page ".js")) "/*CONTENT*/") (content-js) ""))
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
