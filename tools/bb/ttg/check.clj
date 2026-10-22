(ns ttg.check
  "bb setup: report which local tools are installed."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]))

(def tools
  [{:cmd "java"    :args ["-version"]  :need "JDK 17+ (DynamoDB Local, shadow-cljs)" :required true}
   {:cmd "clojure" :args ["--version"] :need "Clojure CLI (shadow-cljs builds)"      :required true}
   {:cmd "node"    :args ["--version"] :need "Node 20+ (Lambda runtime, shadow-cljs)" :required true}
   {:cmd "npm"     :args ["--version"] :need "npm"                                    :required true}
   {:cmd "aws"     :args ["--version"] :need "AWS CLI v2 (deploy only)"               :required false}
   {:cmd "sls"     :args ["--version"] :need "Serverless Framework (offline + deploy)" :required false}
   {:cmd "docker"  :args ["--version"] :need "Docker (optional, not used by bb up)"   :required false}])

(defn- version [{:keys [cmd args]}]
  (when (fs/which cmd)
    (try (let [r (apply p/sh cmd args)]
           (when (zero? (:exit r))
             (-> (str (:out r) (:err r)) str/trim str/split-lines first)))
         (catch Exception _ nil))))

(defn report! []
  (doseq [t tools]
    (let [v (version t)]
      (println (format "%-8s %-4s %s" (:cmd t) (if v "ok" (if (:required t) "MISS" "--")) (or v (:need t))))))
  (when (some #(and (:required %) (not (version %))) tools)
    (throw (ex-info "Missing required tools" {}))))
