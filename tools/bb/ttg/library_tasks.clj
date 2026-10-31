(ns ttg.library-tasks
  "bb library:export and bb seed: get the ingested books into a GM's private library without shipping them.
   The export file lands in .local/ (gitignored); the GM imports it in the app, or bb seed posts it to the dev API."
  (:require [babashka.fs :as fs]
            [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [ttg.ingest :as ingest]
            [ttg.library :as lib]))

(def api "http://localhost:3000")
(def dev-owner "local-gm")

(defn- library-entries [setting]
  (update-vals (:content (ingest/setting-library setting)) #(mapv lib/clean %)))

(defn export-path [setting] (str (fs/path ".local" "library" (str setting ".json"))))

(defn export!
  "bb library:export [setting] [out.json]: the setting's merged library as a ttg-library JSON file."
  [& [setting out]]
  (let [setting (or setting "android")
        out (or out (export-path setting))
        entries (library-entries setting)]
    (fs/create-dirs (fs/parent (fs/absolutize out)))
    (spit out (json/generate-string {:format "ttg-library" :version 1 :setting setting :entries entries}))
    (println "Wrote" out (str "(" (reduce + (map count (vals entries))) " entries)"))
    (println "Import it from the Library page, or run bb seed to load it into the dev API.")))

(defn seed!
  "bb seed [setting] [owner]: loads the setting's library into the running dev API (bb dev) as owner."
  [& [setting owner]]
  (let [entries (library-entries (or setting "android"))
        resp (try (http/post (str api "/library/import")
                             {:headers {"content-type" "application/edn" "x-ttg-owner" (or owner dev-owner)}
                              :body (pr-str {:entries entries}) :throw false})
                  (catch java.net.ConnectException _
                    (throw (ex-info (str "The library API isn't running on " api ". Start it with bb dev.") {}))))
        body (edn/read-string (:body resp))]
    (when-not (= 200 (:status resp))
      (throw (ex-info (str "Seed failed: " (:error body)) body)))
    (println "Seeded" (:saved body) "entries for" (or owner dev-owner))
    (doseq [{:keys [type id problems]} (:rejected body)]
      (println "  rejected" type id problems))))
