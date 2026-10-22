(ns ttg.local
  "bb up / down / status / reset for the local dev stack."
  (:require [babashka.fs :as fs]
            [org.httpkit.server :as httpkit]
            [ttg.dynamo :as dynamo]
            [ttg.proc :as proc]))

(defn- start-dynamo! []
  (if (proc/running? :dynamodb)
    (println "DynamoDB Local already running on" dynamo/endpoint)
    (do (dynamo/install!)
        (proc/start! :dynamodb (dynamo/command))
        (when-not (dynamo/wait-healthy! 20000)
          (throw (ex-info "DynamoDB Local did not start" {:log (proc/log-file :dynamodb)})))
        (println "DynamoDB Local up on" dynamo/endpoint))))

(defn up! []
  (start-dynamo!)
  (let [created (dynamo/ensure-tables!)]
    (println (if (seq created) (str "Created tables: " created) "Tables ready"))))

(defn down! []
  (if-let [n (proc/stop! :dynamodb)]
    (println "Stopped DynamoDB Local (pid" (str n ")"))
    (println "Nothing running")))

(defn status! []
  (if (proc/running? :dynamodb)
    (println "dynamodb  up    " dynamo/endpoint " tables:" (sort (dynamo/table-names)))
    (println "dynamodb  down")))

(defn reset-data! []
  (down!)
  (fs/delete-tree dynamo/data-dir)
  (println "Deleted local data in" dynamo/data-dir))

(def ^:private content-types
  {"html" "text/html; charset=utf-8" "js" "text/javascript; charset=utf-8"
   "css" "text/css; charset=utf-8" "svg" "image/svg+xml" "png" "image/png" "json" "application/json"})

(defn- static-handler [root]
  (fn [{:keys [uri]}]
    (let [path (fs/path root (subs (if (= "/" uri) "/index.html" uri) 1))]
      (if (and (fs/regular-file? path) (fs/starts-with? (fs/normalize path) (fs/normalize root)))
        {:status 200 :headers {"Content-Type" (get content-types (fs/extension path) "application/octet-stream")}
         :body (fs/file path)}
        {:status 404 :body "Not found"}))))

(defn serve-prototypes! []
  (httpkit/run-server (static-handler "prototypes") {:port 8090})
  (println "Serving prototypes/ on http://localhost:8090/dice-roller.html (Ctrl-C to stop)")
  @(promise))
