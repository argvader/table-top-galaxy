(ns ttg.api.gateway
  "The Table protocol over DynamoDB (AWS SDK v3 document client). Items go in and out as Clojure maps;
   anything that needs to keep keywords (entries, characters, campaigns) is stored as an EDN string by its service."
  (:require ["@aws-sdk/client-dynamodb" :as ddb]
            ["@aws-sdk/lib-dynamodb" :as lib-ddb]
            [cljs.core.async :refer [<! timeout]]
            [goog.object :as gobj]
            [ttg.api.async :refer [go-try <? <p?]]
            [ttg.api.table :as table]))

(def ^:private batch-size 25)    ; BatchWriteItem's limit
(def ^:private max-retries 6)

;; Response fields are read by name: go blocks drop ^js hints, and advanced compilation would rename .-Items.
(defn- items [resp] (mapv #(js->clj % :keywordize-keys true) (gobj/get resp "Items")))

(defn- exec [^js client command] (.send client command))

(defn- query-all
  "Every page of a query. params is the QueryCommand input as a Clojure map."
  [client params]
  (go-try
    (loop [start nil, acc []]
      (let [resp (<p? (exec client (lib-ddb/QueryCommand. (clj->js (cond-> params start (assoc :ExclusiveStartKey start))))))
            acc (into acc (items resp))]
        (if-let [next-key (gobj/get resp "LastEvaluatedKey")]
          (recur next-key acc)
          acc)))))

(defn- write-batch
  "Up to 25 put or delete requests, retrying whatever DynamoDB hands back unprocessed."
  [client table-name requests]
  (go-try
    (loop [pending (clj->js {table-name requests}), attempt 0]
      (let [resp (<p? (exec client (lib-ddb/BatchWriteCommand. #js {:RequestItems pending})))
            left (gobj/get resp "UnprocessedItems")]
        (cond (zero? (count (js/Object.keys (or left #js {})))) (count requests)
              (>= attempt max-retries) (throw (ex-info "DynamoDB left items unwritten" {:status 503}))
              :else (do (<! (timeout (* 50 (js/Math.pow 2 attempt)))) (recur left (inc attempt))))))))

(defn- write-requests [client table-name requests]
  (go-try
    (loop [[batch & more] (partition-all batch-size requests), n 0]
      (if batch (recur more (+ n (<? (write-batch client table-name (vec batch))))) n))))

(defn- key-condition
  "Query params for hash = value, and range begins_with prefix unless the prefix is empty (DynamoDB rejects \"\")."
  [params hash-attr range-attr value prefix]
  (if (seq prefix)
    (assoc params :KeyConditionExpression (str hash-attr " = :pk AND begins_with(" range-attr ", :p)")
           :ExpressionAttributeValues {":pk" value ":p" prefix})
    (assoc params :KeyConditionExpression (str hash-attr " = :pk") :ExpressionAttributeValues {":pk" value})))

(defn- conditional-failed? [e] (= "ConditionalCheckFailedException" (gobj/get e "name")))

(defrecord DynamoTable [client table-name]
  table/Table
  (get-item [_ pk sk]
    (go-try (some-> (gobj/get (<p? (exec client (lib-ddb/GetCommand. (clj->js {:TableName table-name :Key {:pk pk :sk sk}}))))
                              "Item")
                    (js->clj :keywordize-keys true))))
  (query [_ pk prefix]
    (query-all client (key-condition {:TableName table-name} "pk" "sk" pk prefix)))
  (query-index [_ gpk prefix]
    (query-all client (key-condition {:TableName table-name :IndexName "gsi1"} "gsi1pk" "gsi1sk" gpk prefix)))
  (put-item! [_ item {:keys [new?]}]
    (go-try
      (try (<p? (exec client (lib-ddb/PutCommand. (clj->js (cond-> {:TableName table-name :Item item}
                                                             new? (assoc :ConditionExpression "attribute_not_exists(pk)"))))))
           true
           (catch :default e
             (throw (if (conditional-failed? e) (ex-info "That already exists" {:status 409}) e))))))
  (put-items! [_ items]
    (write-requests client table-name (mapv (fn [i] {:PutRequest {:Item i}}) items)))
  (delete-items! [_ ks]
    (write-requests client table-name (mapv (fn [k] {:DeleteRequest {:Key (select-keys k [:pk :sk])}}) ks))))

(defn- env [k] (aget js/process.env k))

(defn dynamo-table
  "Reads TABLE_NAME, and DYNAMODB_ENDPOINT when pointing at DynamoDB Local."
  []
  (let [endpoint (env "DYNAMODB_ENDPOINT")
        client (ddb/DynamoDBClient. (clj->js (cond-> {:region (or (env "AWS_REGION") "us-east-1")}
                                               endpoint (assoc :endpoint endpoint))))]
    (->DynamoTable (.from lib-ddb/DynamoDBDocumentClient client #js {:marshallOptions #js {:removeUndefinedValues true}})
                   (or (env "TABLE_NAME") "ttg-local"))))
