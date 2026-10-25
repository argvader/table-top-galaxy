(ns ttg.quality
  "Quality gates and app builds: the cljc tests on the JVM and on Node, the web app's tests, clj-kondo,
   and the shadow-cljs watch and release builds for apps/web."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]))

(def libs "libs")
(def web "apps/web")

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
  (shadow! web "compile" "test"))

(def ^:private kondo '{:deps {clj-kondo/clj-kondo {:mvn/version "2024.11.14"}}})

(defn lint!
  "clj-kondo over libs, the web app and the bb tools, run from Maven so it needs no separate install."
  []
  (p/shell "clojure" "-Sdeps" (pr-str kondo) "-M" "-m" "clj-kondo.main"
           "--lint" "libs/src" "libs/test" "apps/web/src" "apps/web/test" "tools/bb"
           "--parallel" "--fail-level" "error"))

(defn dev!
  "Watches apps/web with hot reload on http://localhost:8280. The API and DynamoDB join this task at M4."
  []
  (shadow! web "watch" "app"))

(defn build!
  "Release (advanced) build of apps/web into apps/web/public/js."
  []
  (shadow! web "release" "app"))
