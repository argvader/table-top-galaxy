(ns ttg.api.db
  "The Postgres database, as the services see it. aurora.cljs implements this over the RDS Data API; pglite.cljs runs
   the same SQL on an in-process Postgres for tests and bb dev.

   SQL is written so both give the same rows: params are :name, values go through CAST(:x AS type), and jsonb and
   timestamp columns are selected as text (see json-col, ts-col), because the two drivers decode those differently.
   Every statement is a single statement: the Data API refuses more, and a CTE covers writes that must happen together."
  (:require [ttg.api.async :refer [go-try <?]]))

(defprotocol Db
  (query [db sql params] "Rows as maps with keyword keys, on a channel. params is {:name value}."))

(defn one
  "The first row, or nil."
  [db sql params]
  (go-try (first (<? (query db sql params)))))

(defn json-col [col] (str "CAST(" col " AS text) AS " col))

(defn ts-col [col]
  (str "to_char(" col " AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') AS " col))

(defn parse-json
  "JSON text to Clojure, with keyword keys."
  [s]
  (js->clj (js/JSON.parse s) :keywordize-keys true))

(defn ->json [x] (js/JSON.stringify (clj->js x)))
