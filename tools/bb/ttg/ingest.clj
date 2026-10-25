(ns ttg.ingest
  "bb ingest:* tasks: turn the user's Genesys/Android PDFs into structured content.
   Python (tools/ingest) does the PDF reading; results land in .local/books/<slug>/."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [clojure.spec.alpha :as s]
            [ttg.content :as content]
            [ttg.genesys.model :as model]
            [ttg.ingest.crb :as crb]
            [ttg.ingest.epg :as epg]
            [ttg.ingest.sotb :as sotb]
            [ttg.ingest.lines :as lines]))

(def default-dir "/mnt/w/argvader/games/systems/Genesys")
(def venv ".local/ingest-venv")
(def books-dir ".local/books")

;; Precedence runs low to high; :lore books feed setting content, not rules.
(def books
  [{:slug "crb"       :file "Genesys - Corerules.pdf"                                       :title "Genesys Core Rulebook"}
   {:slug "epg"       :file "Genesys_Expanded_Player_s_Guide.pdf"                           :title "Expanded Player's Guide"}
   {:slug "sotb"      :file "Android - Shadow of the Beanstalk [Genesys System] Small.pdf"  :title "Shadow of the Beanstalk"}
   {:slug "sotb-guns" :file "Genesys - Android; Shadow of the Beanstalk - Gun Locker.pdf"   :title "Shadow of the Beanstalk: Gun Locker"}
   {:slug "woa"       :file "Genesys-Worlds of Android.pdf"                                 :title "Worlds of Android" :lore true}])

(defn- python [] (str (fs/path venv "bin" "python")))

(defn setup! []
  (when-not (fs/exists? (python))
    (p/shell "python3" "-m" "venv" venv))
  (p/shell (python) "-m" "pip" "install" "-q" "-r" "tools/ingest/requirements.txt")
  (println "Ingest venv ready at" venv))

(defn- require-venv! []
  (when-not (fs/exists? (python))
    (throw (ex-info "Run bb ingest:setup first" {:venv venv}))))

(defn- probe-book! [dir {:keys [slug file title]}]
  (let [pdf (fs/path dir file)]
    (if-not (fs/exists? pdf)
      (println (format "%-10s missing  %s" slug pdf))
      (let [r (p/sh (python) "tools/ingest/probe.py" (str pdf) (str (fs/path books-dir slug)))]
        (if (zero? (:exit r))
          (let [{:keys [pages no_text toc_entries]} (json/parse-string (:out r) true)]
            (println (format "%-10s %4d pages  %4d without text  %3d TOC entries  %s"
                             slug pages no_text toc_entries title)))
          (println (format "%-10s FAILED   %s" slug (:err r))))))))

(defn probe! [& [dir]]
  (require-venv!)
  (doseq [b books] (probe-book! (or dir default-dir) b)))

(defn- book [slug]
  (or (first (filter #(= slug (:slug %)) books))
      (throw (ex-info (str "Unknown book " slug) {:known (map :slug books)}))))

(defn- extract-book! [dir {:keys [slug file]}]
  (let [out (fs/path books-dir slug)]
    (p/shell (python) "tools/ingest/extract.py" (str (fs/path dir file)) (str out) "--tables")
    (println "Extracted" slug "to" (str (fs/path out "pages.jsonl")))))

(defn extract!
  "bb ingest:extract [slug ...]: all rules books when no slug is given."
  [& slugs]
  (require-venv!)
  (doseq [b (if (seq slugs) (map book slugs) (remove :lore books))]
    (extract-book! default-dir b)))

;; ---------- Build: parsed entries -> content/<slug>/<type>.edn ----------
(def content-dir "content")

(def parsers
  {"epg" {:adversaries epg/adversaries :vehicles epg/vehicles :tones epg/tones}
   "sotb-guns" {:weapons sotb/gun-locker}
   "sotb" {:weapons sotb/weapons :armor sotb/armor :gear sotb/gear :attachments sotb/attachments
           :archetypes sotb/archetypes :careers sotb/careers :skills sotb/skills :talents sotb/talents :talent-list sotb/talent-list :skill-list sotb/skill-list :network sotb/network :network-results sotb/network-results :factions sotb/factions :vehicles sotb/vehicles}
   "crb" {:adversaries crb/adversaries :spend-results crb/spend-results :motivations crb/motivations :item-qualities crb/item-qualities :crits crb/crits :weapons crb/weapons :armor crb/armor :gear crb/gear :skills crb/skills :archetypes crb/archetypes :careers crb/careers :talents crb/talents}})

(defn- write-edn! [path entries]
  (fs/create-dirs (fs/parent path))
  (spit (str path) (with-out-str (binding [pp/*print-right-margin* 110] (pp/pprint entries)))))

(defn build!
  "bb ingest:build [slug ...]: run each book's parsers and write its content EDN."
  [& slugs]
  (doseq [slug (or (seq slugs) (keys parsers))
          :let [pages (lines/pages slug)]
          [type parse] (get parsers slug)]
    (let [entries (sort-by :name (parse pages))
          path (fs/path content-dir slug (str (name type) ".edn"))]
      (write-edn! path (vec entries))
      (println (format "%-10s %-12s %4d entries -> %s" slug (name type) (count entries) path)))))

;; ---------- Library: merged content narrowed to a setting ----------
(defn load-books
  "content/<book>/<type>.edn -> {\"crb\" {:talents [...]} ...}"
  []
  (reduce (fn [acc path]
            (let [book (str (fs/file-name (fs/parent path)))
                  type (keyword (fs/strip-ext (fs/file-name path)))]
              (assoc-in acc [book type] (edn/read-string (slurp (str path))))))
          {} (remove #(= "settings" (str (fs/file-name (fs/parent %)))) (fs/glob content-dir "*/*.edn"))))

(defn setting-library
  "The merged library for content/settings/<setting>.edn, plus the profile itself."
  [setting]
  (let [profile (edn/read-string (slurp (str (fs/path content-dir "settings" (str setting ".edn")))))
        lib (content/merge-library (load-books))]
    {:profile profile :lib lib :content (content/for-setting lib profile)}))

(defn library! [& [setting]]
  (let [{:keys [profile lib content]} (setting-library (or setting "android"))
        missing (content/unresolved lib profile)]
    (println "Setting" (:name profile))
    (doseq [[type entries] (sort-by key content)]
      (println (format "  %-18s %4d" (name type) (count entries))))
    (when (seq missing)
      (throw (ex-info (str "Unresolved references: " missing) missing)))))

;; ---------- Check: every content file validates against its spec ----------
(defn- check-file [path]
  (let [type (keyword (fs/strip-ext (fs/file-name path)))
        spec (model/entry-specs type)
        entries (edn/read-string (slurp (str path)))
        bad (remove #(s/valid? spec %) entries)]
    (println (format "%-36s %4d entries  %s" (str path) (count entries) (if (seq bad) (str (count bad) " INVALID") "ok")))
    (doseq [e (take 5 bad)] (println "   " (:id e) (s/explain-str spec e)))
    (count bad)))

(defn check! []
  (let [files (filter #(model/entry-specs (keyword (fs/strip-ext (fs/file-name %)))) (fs/glob content-dir "**.edn"))
        bad (reduce + (map check-file (sort files)))]
    (when (pos? bad) (throw (ex-info (str bad " invalid entries") {})))))
