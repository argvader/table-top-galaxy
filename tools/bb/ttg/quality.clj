(ns ttg.quality
  "Quality gates and app builds: the cljc tests on the JVM and on Node, the web app's and API's tests, clj-kondo,
   the shadow-cljs watch and release builds, and bb dev (DynamoDB Local + library API + web app)."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [ttg.local :as local]))

(def libs "libs")
(def web "apps/web")
(def api "apps/serverless")

(defn- ensure-npm! [dir]
  (when-not (fs/exists? (fs/path dir "node_modules" "shadow-cljs"))
    (p/shell {:dir dir} "npm" "install" "--no-audit" "--no-fund")))

(defn- shadow! [dir & args]
  (ensure-npm! dir)
  (apply p/shell {:dir dir} "npx" "shadow-cljs" args))

(defn test!
  "Runs the libs tests on the JVM and on Node, then the web app's tests on Node. Any failure fails the task."
  []
  (println "== libs: JVM")
  (p/shell {:dir libs} "clojure" "-M:test")
  (println "== libs: Node")
  (shadow! libs "compile" "test")
  (println "== apps/web: Node")
  (shadow! web "compile" "test")
  (println "== apps/serverless: Node")
  (shadow! api "compile" "test"))

(def ^:private kondo '{:deps {clj-kondo/clj-kondo {:mvn/version "2024.11.14"}}})

(defn lint!
  "clj-kondo over libs, the web app and the bb tools, run from Maven so it needs no separate install."
  []
  (p/shell "clojure" "-Sdeps" (pr-str kondo) "-M" "-m" "clj-kondo.main"
           "--lint" "libs/src" "libs/test" "apps/web/src" "apps/web/test" "apps/serverless/src" "apps/serverless/test" "tools/bb"
           "--parallel" "--fail-level" "error"))

(def ^:private dev-server (str (fs/path api ".shadow-cljs" "dev" "server.js")))

(defn- echo-until!
  "Copies the process's output to ours, delivering built when shadow-cljs reports the first finished build,
   or that another shadow-cljs (an editor, an earlier bb dev) is already watching it."
  [proc built]
  (future
    (with-open [r (io/reader (:out proc))]
      (doseq [line (line-seq r)]
        (println line)
        (when (or (str/includes? line "Build completed") (str/includes? line "already started"))
          (deliver built true))))
    (deliver built false)))

(defn- start-api!
  "shadow-cljs watch for the dev API, then node on the built server once the first build is done.
   Returns both processes."
  []
  (ensure-npm! api)
  (let [built (promise)
        ;; shadow-cljs reports builds on stderr, so both streams go through echo-until!.
        watch (p/process {:dir api :out :stream :err :out} "npx" "shadow-cljs" "watch" "dev")]
    (echo-until! watch built)
    (when-not @built (throw (ex-info "The API build stopped; see the output above" {})))
    [watch (p/process {:inherit true} "node" dev-server)]))

(defn api!
  "The library API on http://localhost:3000 against DynamoDB Local (started if needed), with hot reload."
  []
  (local/up!)
  (let [procs (start-api!)]
    (.addShutdownHook (Runtime/getRuntime) (Thread. #(run! p/destroy-tree procs)))
    @(last procs)))

(defn dev!
  "DynamoDB Local, the library API on :3000 and the web app on http://localhost:8280, all hot-reloading.
   Ctrl-C stops the API and web watchers; bb down stops DynamoDB Local."
  []
  (local/up!)
  (let [procs (start-api!)]
    (.addShutdownHook (Runtime/getRuntime) (Thread. #(run! p/destroy-tree procs)))
    (shadow! web "watch" "app")))

;; A release build with no API stack: no API, no dev owner, no sign-in. The library page says it isn't deployed.
(def ^:private offline-defines
  {'ttg.config/api-url "" 'ttg.config/dev-owner "" 'ttg.config/auth-domain "" 'ttg.config/client-id ""})

(defn gm-strings
  "The secrets in a campaign's written prep. Long enough that one can't turn up in the bundle by chance."
  [{:keys [ledger npcs twists]}]
  (->> (concat (map :truth ledger) (map :gm npcs) (map :text twists))
       (filter #(and (string? %) (> (count %) 30)))))

(defn check-bundle!
  "Every player downloads the web bundle, so GM-only prep must never be in it."
  []
  (let [bundle (str/join (map (comp slurp str) (fs/glob (str web "/public/js") "*.js")))
        leaked (filter #(str/includes? bundle %) (gm-strings (json/parse-string (slurp "content/manifest-destiny/manager.json") true)))]
    (when (seq leaked)
      (throw (ex-info (str "GM-only text is in the web bundle: " (first leaked)) {:leaked (count leaked)})))))

(defn build-web!
  "Release build of apps/web. defines ({'ttg.config/api-url \"...\"}) point it at a deployed API and sign-in;
   anything not given is blank, so a release never carries the localhost dev settings."
  ([] (build-web! nil))
  ([defines]
   (shadow! web "release" "app" "--config-merge" (pr-str {:closure-defines (merge offline-defines defines)}))
   (check-bundle!)))

(defn build-api! [] (shadow! api "release" "api"))

(defn build!
  "Release (advanced) builds: apps/web into apps/web/public/js, and the API Lambda into apps/serverless/serverless."
  []
  (build-web!)
  (build-api!))
