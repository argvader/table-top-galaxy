(ns ttg.api.pglite
  "The Db protocol over PGlite, Postgres compiled to WebAssembly, so tests and bb dev run the real SQL with no server.
   Dev-only: nothing the Lambda loads requires this namespace. Schema comes from the same files bb db:migrate applies."
  (:require ["@electric-sql/pglite" :refer [PGlite]]
            ["fs" :as fs]
            ["path" :as path]
            [clojure.string :as str]
            [goog.object :as gobj]
            [ttg.api.async :refer [go-try <p?]]
            [ttg.api.db :as db]))

(def ^:private named-param #"(?<![:\w]):([a-z][a-z0-9_]*)")

(defn- positional
  "\"... :a ... :b ... :a\" -> [\"... $1 ... $2 ... $1\" [:a :b]]."
  [sql]
  (let [names (vec (distinct (map (comp keyword second) (re-seq named-param sql))))
        index (zipmap names (map inc (range)))]
    [(str/replace sql named-param (fn [[_ n]] (str "$" (index (keyword n))))) names]))

(defrecord Pglite [^js pg]
  db/Db
  (query [_ sql params]
    (go-try
      (let [[text names] (positional sql)
            res (<p? (.query pg text (clj->js (mapv params names))))]
        (mapv #(js->clj % :keywordize-keys true) (gobj/get res "rows"))))))

(defn- migrations [dir]
  (->> (fs/readdirSync dir) (filter #(str/ends-with? % ".sql")) sort
       (map #(fs/readFileSync (path/join dir %) "utf8"))))

(defn pglite
  "A migrated database on a channel. :migrations-dir is the folder of .sql files; :data-dir keeps the data between runs,
   and without it the database lives in memory."
  [{:keys [migrations-dir data-dir] :or {migrations-dir "migrations"}}]
  (go-try
    (let [pg (if data-dir (PGlite. data-dir) (PGlite.))]
      (doseq [sql (migrations migrations-dir)] (<p? (.exec pg sql)))
      (->Pglite pg))))
