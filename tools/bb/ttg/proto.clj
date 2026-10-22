(ns ttg.proto
  "Assembles prototypes/src/<page>.html + <page>.js + shared dice code into prototypes/<page>.html."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(def src "prototypes/src")

(defn- ascii-escape
  "Escapes non-ASCII so pages render under any charset: \\uXXXX in JS, &#xXXXX; in HTML."
  [s fmt]
  (str/join (map (fn [c] (if (< (int c) 128) (str c) (format fmt (int c)))) s)))

(defn- read-src [& parts] (slurp (str (apply fs/path src parts))))

(defn- page-js [page]
  (-> (read-src (str page ".js"))
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
