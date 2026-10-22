(ns ttg.dynamo
  "DynamoDB Local: install, health, and table creation over its HTTP API."
  (:require [babashka.fs :as fs]
            [babashka.http-client :as http]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def port 8000)
(def endpoint (str "http://localhost:" port))
(def install-dir ".local/dynamodb")
(def data-dir ".local/dynamodb/data")
(def download-url "https://d1ni2b6xgvw0s0.cloudfront.net/v2.x/dynamodb_local_latest.tar.gz")

(defn- jar [] (str (fs/path install-dir "DynamoDBLocal.jar")))

(defn install!
  "Downloads DynamoDB Local once. Needs only Java 17+, no Docker."
  []
  (when-not (fs/exists? (jar))
    (println "Downloading DynamoDB Local...")
    (fs/create-dirs install-dir)
    (let [tgz (str (fs/path install-dir "dynamodb.tar.gz"))]
      (io/copy (:body (http/get download-url {:as :stream})) (io/file tgz))
      (p/shell {:dir install-dir} "tar" "xzf" "dynamodb.tar.gz")
      (fs/delete tgz))))

(defn command []
  (fs/create-dirs data-dir)
  ["java" (str "-Djava.library.path=" (fs/path install-dir "DynamoDBLocal_lib"))
   "-jar" (jar) "-sharedDb" "-dbPath" data-dir "-port" (str port)])

;; DynamoDB Local accepts any credentials but still expects a SigV4-shaped header.
(def ^:private auth
  "AWS4-HMAC-SHA256 Credential=local/20260101/us-east-1/dynamodb/aws4_request, SignedHeaders=host, Signature=0")

(defn call [op body]
  (let [resp (http/post endpoint {:headers {"Content-Type"  "application/x-amz-json-1.0"
                                            "X-Amz-Target"  (str "DynamoDB_20120810." op)
                                            "Authorization" auth}
                                  :body    (json/generate-string body)
                                  :throw   false})]
    {:status (:status resp) :body (json/parse-string (:body resp) true)}))

(defn healthy? []
  (try (= 200 (:status (call "ListTables" {})))
       (catch Exception _ false)))

(defn wait-healthy! [timeout-ms]
  (loop [waited 0]
    (cond (healthy?)           true
          (>= waited timeout-ms) false
          :else                (do (Thread/sleep 250) (recur (+ waited 250))))))

(defn table-names [] (set (get-in (call "ListTables" {}) [:body :TableNames])))

(defn ensure-tables!
  "Creates every table in tables.edn that does not exist yet. Returns the created names."
  []
  (let [existing (table-names)
        tables   (edn/read-string (slurp (io/resource "ttg/tables.edn")))]
    (vec (for [t tables :when (not (existing (:TableName t)))]
           (do (call "CreateTable" t) (:TableName t))))))
