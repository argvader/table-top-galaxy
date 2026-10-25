(ns ttg.deploy
  "bb web:* tasks: S3 + CloudFront hosting for the prototypes (infra/web.yml), driven through the AWS CLI.
   Credentials come from a named AWS profile; the site password from secrets.edn (gitignored)."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [ttg.proto :as proto]
            [ttg.quality :as quality]))

(def secrets-file "secrets.edn")
(def template "infra/web.yml")
(def site-pages ["character-builder.html" "character-sheet.html" "dice-roller.html" "gm-dashboard.html"])
;; The real app (apps/web release build) goes under roller/. CloudFront has no directory index there, so links say roller/index.html.
(def web-public "apps/web/public")
(def web-files ["index.html" "css/app.css" "js/main.js" "js/dice3d.js"])

(defn- secrets []
  (when-not (fs/exists? secrets-file)
    (throw (ex-info (str "Missing " secrets-file ". Copy secrets.example.edn and fill it in.") {})))
  (let [s (edn/read-string (slurp secrets-file))]
    (when (< (count (get-in s [:web :password] "")) 12)
      (throw (ex-info "secrets.edn :web :password must be at least 12 characters" {})))
    s))

(defn- parse-args
  "[\"prod\" \"--profile\" \"x\"] -> {:stage \"prod\" :profile \"x\"}. Flags can go anywhere."
  [args]
  (loop [[a & more] args, out {:stage "dev"}]
    (cond (nil? a) out
          (= "--profile" a) (recur (rest more) (assoc out :profile (first more)))
          :else (recur more (assoc out :stage a)))))

(defn- with-profile
  "Picks the AWS profile: --profile flag, then TTG_AWS_PROFILE, then secrets.edn :aws :profile."
  [cfg {:keys [profile]}]
  (assoc-in cfg [:aws :profile] (or profile (System/getenv "TTG_AWS_PROFILE") (get-in cfg [:aws :profile]) "ttg-deployer")))

;; Exported keys would beat AWS_PROFILE in the CLI's credential chain, so they are dropped for these commands.
(def ^:private key-vars #{"AWS_ACCESS_KEY_ID" "AWS_SECRET_ACCESS_KEY" "AWS_SESSION_TOKEN" "AWS_DEFAULT_PROFILE"})

(defn- aws-env [{:keys [aws]}]
  (-> (into {} (remove (comp key-vars key)) (System/getenv))
      (assoc "AWS_PROFILE" (:profile aws) "AWS_REGION" (:region aws "us-east-1") "AWS_PAGER" "")))

(defn- require-aws! []
  (when-not (fs/which "aws")
    (throw (ex-info "AWS CLI v2 not found. See README: Deploying the prototypes." {}))))

(defn- aws!
  "Runs an aws command, streaming its output."
  [cfg & args]
  (apply p/shell {:env (aws-env cfg)} "aws" args))

(defn- aws-out
  "Runs an aws command and returns its stdout."
  [cfg & args]
  (-> (apply p/sh {:env (aws-env cfg)} "aws" args)
      (#(if (zero? (:exit %)) (:out %) (throw (ex-info (str/trim (:err %)) {:args args}))))))

(defn- stack-name [stage] (str "ttg-web-" stage))

(defn- identity! [cfg]
  (let [arn (str/trim (aws-out cfg "sts" "get-caller-identity" "--query" "Arn" "--output" "text"))]
    (println (format "AWS profile %s -> %s" (get-in cfg [:aws :profile]) arn))
    arn))

(defn- setup
  "Parses the task args and returns [stage cfg], after printing which AWS identity will be used."
  [args]
  (require-aws!)
  (let [{:keys [stage] :as opts} (parse-args args)
        cfg (with-profile (secrets) opts)]
    (identity! cfg)
    [stage cfg]))

(defn- outputs [cfg stage]
  (->> (aws-out cfg "cloudformation" "describe-stacks" "--stack-name" (stack-name stage)
                "--query" "Stacks[0].Outputs" "--output" "json")
       (#(json/parse-string % true))
       (map (juxt (comp keyword :OutputKey) :OutputValue))
       (into {})))

(defn infra!
  "bb web:infra [stage] [--profile p]: create or update the bucket, distribution and password gate."
  [& args]
  (let [[stage cfg] (setup args)
        ;; Parameters go through an owner-only temp file, not the command line, so the password stays out of `ps`.
        params (fs/create-temp-file {:prefix "ttg-web-params-" :suffix ".json" :posix-file-permissions "rw-------"})
        param (fn [k v] {:ParameterKey k :ParameterValue v})]
    (spit (str params) (json/generate-string [(param "Stage" stage)
                                              (param "BasicAuthUser" (get-in cfg [:web :user]))
                                              (param "BasicAuthPassword" (get-in cfg [:web :password]))]))
    (try
      (aws! cfg "cloudformation" "deploy" "--template-file" template "--stack-name" (stack-name stage)
            "--parameter-overrides" (str "file://" params) "--no-fail-on-empty-changeset"
            "--tags" "app=table-top-galaxy" (str "stage=" stage))
      (finally (fs/delete-if-exists params)))
    (println "Site:" (:SiteUrl (outputs cfg stage)))))

(defn- stage-site!
  "Copies the built prototypes plus the landing and error pages into .local/dist/<stage>."
  [stage]
  (let [out (fs/path ".local" "dist" stage)]
    (fs/delete-tree out)
    (fs/create-dirs out)
    (doseq [page site-pages] (fs/copy (fs/path "prototypes" page) (fs/path out page)))
    (doseq [f (fs/list-dir "prototypes/site")] (fs/copy f (fs/path out (fs/file-name f))))
    (doseq [f web-files :let [to (fs/path out "roller" f)]]
      (fs/create-dirs (fs/parent to))
      (fs/copy (fs/path web-public f) to))
    out))

(defn deploy!
  "bb web:deploy [stage] [--profile p]: rebuild the prototypes and the apps/web roller, upload them, and clear CloudFront's cache."
  [& args]
  (let [[stage cfg] (setup args)
        {:keys [BucketName DistributionId SiteUrl]} (outputs cfg stage)
        dir (do (proto/build!) (quality/build!) (stage-site! stage))]
    ;; no-cache: browsers and CloudFront revalidate each load, so a redeploy shows up at once.
    (aws! cfg "s3" "sync" (str dir) (str "s3://" BucketName) "--delete" "--cache-control" "no-cache")
    (aws! cfg "cloudfront" "create-invalidation" "--distribution-id" DistributionId "--paths" "/*"
          "--query" "Invalidation.Id" "--output" "text")
    (println "Deployed" (count site-pages) "prototypes and the roller to" SiteUrl)
    (println "Roller:" (str SiteUrl "/roller/index.html"))))

(defn url!
  "bb web:url [stage] [--profile p]: print the site address."
  [& args]
  (let [[stage cfg] (setup args)]
    (println (:SiteUrl (outputs cfg stage)))))

(defn whoami!
  "bb web:whoami [--profile p]: show which AWS identity the web tasks would use."
  [& args]
  (setup args))

(defn remove!
  "bb web:remove <stage> [--profile p]: empty the bucket and delete the stack. The stage is required so nothing goes by accident."
  [& args]
  (when-not (some #{"dev" "prod"} args)
    (throw (ex-info "Name the stage to remove: bb web:remove dev" {})))
  (let [[stage cfg] (setup args)
        {:keys [BucketName]} (outputs cfg stage)]
    (aws! cfg "s3" "rm" (str "s3://" BucketName) "--recursive")
    (aws! cfg "cloudformation" "delete-stack" "--stack-name" (stack-name stage))
    (aws! cfg "cloudformation" "wait" "stack-delete-complete" "--stack-name" (stack-name stage))
    (println "Removed" (stack-name stage))))
