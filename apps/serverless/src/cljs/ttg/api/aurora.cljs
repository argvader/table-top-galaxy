(ns ttg.api.aurora
  "The Db protocol over the RDS Data API, so the Lambda needs no VPC or connection pool. A paused cluster answers
   DatabaseResumingException while it wakes, about 15 seconds, so those calls wait and try again."
  (:require ["@aws-sdk/client-rds-data" :as rds]
            [cljs.core.async :refer [<! timeout]]
            [goog.object :as gobj]
            [ttg.api.async :refer [go-try promise->chan]]
            [ttg.api.db :as db]))

(def ^:private max-wait-ms 24000)   ; inside the Lambda's 29 seconds
(def ^:private wait-step-ms 2000)

(defn- ->value [v]
  (cond (nil? v) {:isNull true}
        (integer? v) {:longValue v}
        :else {:stringValue (str v)}))

(defn- ->params [params]
  (mapv (fn [[k v]] {:name (name k) :value (->value v)}) params))

(defn- resuming? [e] (= "DatabaseResumingException" (gobj/get e "name")))

(defn- rows [resp]
  (let [text (gobj/get resp "formattedRecords")]
    (if (seq text) (db/parse-json text) [])))

(defn- execute [^js client command]
  (promise->chan (.send client command)))

(defrecord Aurora [client config]
  db/Db
  (query [_ sql params]
    (go-try
      (let [input (clj->js (assoc config :sql sql :parameters (->params params) :formatRecordsAs "JSON"))]
        (loop [waited 0]
          (let [res (<! (execute client (rds/ExecuteStatementCommand. input)))]
            (cond (not (instance? js/Error res)) (rows res)
                  (and (resuming? res) (< waited max-wait-ms)) (do (<! (timeout wait-step-ms)) (recur (+ waited wait-step-ms)))
                  (resuming? res) (throw (ex-info "The game database is waking up. Try again in a moment." {:status 503}))
                  :else (throw res))))))))

(defn- env [k] (aget js/process.env k))

(defn aurora
  "Reads DB_CLUSTER_ARN, DB_SECRET_ARN and DB_NAME."
  []
  (->Aurora (rds/RDSDataClient. (clj->js {:region (or (env "AWS_REGION") "us-east-1")}))
            {:resourceArn (env "DB_CLUSTER_ARN") :secretArn (env "DB_SECRET_ARN") :database (env "DB_NAME")}))
