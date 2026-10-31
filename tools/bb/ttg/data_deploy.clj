(ns ttg.data-deploy
  "bb data:infra and db:migrate: the Aurora cluster (infra/data.yml) and its schema, through the AWS CLI as ttg-deployer.
   Migrations go over the Data API one statement at a time, because it refuses multi-statement calls."
  (:require [babashka.fs :as fs]
            [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]
            [ttg.deploy :as deploy]))

;; 16.3 is the oldest 16.x that can scale to 0 ACUs.
(def engine-version "16.15")
(def migrations-dir "apps/serverless/migrations")
(def ^:private resume-tries 12)
(def ^:private resume-wait-ms 5000)

(defn- data-stack [stage] (str "ttg-api-" stage "-data"))

(defn- default-subnets
  "The default VPC's subnets, comma-separated."
  [cfg]
  (let [vpc (str/trim (deploy/aws-out cfg "ec2" "describe-vpcs" "--filters" "Name=is-default,Values=true"
                                      "--query" "Vpcs[0].VpcId" "--output" "text"))]
    (when (= "None" vpc)
      (throw (ex-info "This account has no default VPC. Create one with: aws ec2 create-default-vpc" {})))
    (-> (deploy/aws-out cfg "ec2" "describe-subnets" "--filters" (str "Name=vpc-id,Values=" vpc)
                        "--query" "Subnets[].SubnetId" "--output" "text")
        str/trim
        (str/replace #"\s+" ","))))

(defn infra!
  "bb data:infra [stage] [--min-capacity n] [--profile p]: create or update the cluster. A first run takes about 10 minutes.
   --min-capacity 0.5 keeps it awake for game night; 0 lets it pause again. Left out, the current value stays."
  [& args]
  (let [[stage cfg] (deploy/setup args)
        {:keys [min-capacity]} (deploy/parse-args args)]
    (apply deploy/aws! cfg "cloudformation" "deploy" "--template-file" "infra/data.yml" "--stack-name" (data-stack stage)
           "--no-fail-on-empty-changeset" "--tags" "app=table-top-galaxy" (str "stage=" stage)
           "--parameter-overrides" (str "Stage=" stage) (str "SubnetIds=" (default-subnets cfg)) (str "EngineVersion=" engine-version)
           (when min-capacity [(str "MinCapacity=" min-capacity)]))
    (println "Next: bb db:migrate" stage)))

(defn statements
  "The SQL statements in a migration: comments dropped, split where a line ends with a semicolon."
  [text]
  (->> (str/split-lines text)
       (remove #(str/starts-with? (str/trim %) "--"))
       (str/join "\n")
       (#(str/split % #";[ \t]*(\n|$)"))
       (map str/trim)
       (remove str/blank?)))

(defn- resuming? [e] (str/includes? (ex-message e) "DatabaseResumingException"))

(defn execute!
  "Runs one statement, waiting out a paused cluster. Returns the rows."
  [cfg {:keys [ClusterArn SecretArn DatabaseName]} sql & [parameters]]
  (loop [tries resume-tries]
    (let [result (try
                   (apply deploy/aws-out cfg "rds-data" "execute-statement" "--resource-arn" ClusterArn "--secret-arn" SecretArn
                          "--database" DatabaseName "--format-records-as" "JSON" "--sql" sql
                          (when parameters ["--parameters" (json/generate-string parameters)]))
                   (catch clojure.lang.ExceptionInfo e
                     (when-not (and (resuming? e) (pos? tries)) (throw e))
                     ::resuming))]
      (if (= ::resuming result)
        (do (println "Waiting for the database to wake...")
            (Thread/sleep resume-wait-ms)
            (recur (dec tries)))
        (some-> (:formattedRecords (json/parse-string result true)) not-empty (json/parse-string true))))))

(defn database
  "The stage's database stack outputs, for execute!."
  [cfg stage]
  (or (deploy/stack-outputs cfg (data-stack stage))
      (throw (ex-info (str "No database yet. Run bb data:infra " stage " first.") {}))))

(defn- applied [cfg db]
  (execute! cfg db "CREATE TABLE IF NOT EXISTS schema_migrations (name text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now())")
  (set (map :name (execute! cfg db "SELECT name FROM schema_migrations"))))

(defn- apply-file! [cfg db file]
  (let [file-name (str (fs/file-name file))]
    (println "Applying" file-name)
    (doseq [sql (statements (slurp (str file)))] (execute! cfg db sql))
    (execute! cfg db "INSERT INTO schema_migrations (name) VALUES (:name)"
              [{:name "name" :value {:stringValue file-name}}])))

(defn migrate!
  "bb db:migrate [stage] [--profile p]: apply the .sql files that haven't run, in name order."
  [& args]
  (let [[stage cfg] (deploy/setup args)
        db (database cfg stage)
        done (applied cfg db)
        pending (remove #(done (str (fs/file-name %))) (sort (map str (fs/glob migrations-dir "*.sql"))))]
    (run! #(apply-file! cfg db %) pending)
    (println (if (seq pending) (str "Applied " (count pending) " migration(s) to " stage) (str stage " is up to date")))))

;; ---------- A campaign's written prep ----------

(def manager-file "content/manifest-destiny/manager.json")
(def ^:private dev-api "http://localhost:3000")

(defn- seed-local!
  "Into the running dev API (bb dev), as that campaign's GM."
  [id owner]
  (let [resp (http/put (str dev-api "/campaigns/" id "/manager/content")
                       {:headers {"content-type" "application/json" "x-ttg-owner" owner}
                        :body (slurp manager-file) :throw false})]
    (when-not (= 200 (:status resp))
      (throw (ex-info (str "Seed failed: " (:body resp)) {})))
    (println "Loaded the prep into" id)))

(defn- seed-stage!
  "Through the Data API, like a migration."
  [args id]
  (let [[stage cfg] (deploy/setup args)
        db (database cfg stage)
        rows (execute! cfg db "UPDATE campaigns SET manager_content = CAST(:m AS jsonb) WHERE id = CAST(:id AS uuid) RETURNING name"
                       [{:name "m" :value {:stringValue (slurp manager-file)}} {:name "id" :value {:stringValue id}}])]
    (when (empty? rows) (throw (ex-info (str "No campaign " id " on " stage) {})))
    (println "Loaded the prep into" (:name (first rows)) "on" stage)))

(defn seed-manager!
  "bb manager:seed [local|dev|prod] <campaign-id> [--profile p]: load the campaign's written prep. It's GM-only data, so
   it lives in the database and never in the web bundle."
  [& args]
  (let [id (first (filter #(re-matches #"[0-9a-fA-F-]{36}" %) args))
        others (remove #{id} args)]
    (when-not id (throw (ex-info "Usage: bb manager:seed [local|dev|prod] <campaign-id>" {})))
    (if (some #{"local"} others)
      (seed-local! id "local-gm")
      (seed-stage! others id))))
