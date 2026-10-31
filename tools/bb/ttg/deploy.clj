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
(def apex-domain "tt-galaxy.com")
(def domain-stack "ttg-web-domain")
(def site-pages ["character-builder.html" "character-sheet.html" "dice-roller.html" "gm-dashboard.html"])
;; The real app (apps/web release build) goes under roller/. CloudFront has no directory index there, so links say roller/index.html.
(def web-public "apps/web/public")
(def web-files ["index.html" "css/app.css" "css/game.css" "js/main.js" "js/dice3d.js"])
;; Prod is for players: the address opens the app, and the hash goes along so a shared #/join/<code> link still lands.
(def prod-landing
  (str "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
       "<meta name=\"robots\" content=\"noindex, nofollow\">\n"
       "<title>Table Top Galaxy</title>\n"
       "<meta http-equiv=\"refresh\" content=\"0; url=roller/index.html\">\n"
       "<script>location.replace('roller/index.html' + location.hash);</script>\n"
       "</head>\n<body><a href=\"roller/index.html\">Table Top Galaxy</a></body>\n</html>\n"))

(defn- secrets []
  (when-not (fs/exists? secrets-file)
    (throw (ex-info (str "Missing " secrets-file ". Copy secrets.example.edn and fill it in.") {})))
  (let [s (edn/read-string (slurp secrets-file))]
    (when (< (count (get-in s [:web :password] "")) 12)
      (throw (ex-info "secrets.edn :web :password must be at least 12 characters" {})))
    s))

(defn parse-args
  "[\"prod\" \"--profile\" \"x\"] -> {:stage \"prod\" :profile \"x\"}. Flags can go anywhere; bb data:infra also takes --min-capacity."
  [args]
  (loop [[a & more] args, out {:stage "dev"}]
    (cond (nil? a) out
          (= "--profile" a) (recur (rest more) (assoc out :profile (first more)))
          (= "--min-capacity" a) (recur (rest more) (assoc out :min-capacity (first more)))
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

(defn aws!
  "Runs an aws command, streaming its output."
  [cfg & args]
  (apply p/shell {:env (aws-env cfg)} "aws" args))

(defn aws-out
  "Runs an aws command and returns its stdout."
  [cfg & args]
  (-> (apply p/sh {:env (aws-env cfg)} "aws" args)
      (#(if (zero? (:exit %)) (:out %) (throw (ex-info (str/trim (:err %)) {:args args}))))))

(defn- stack-name [stage] (str "ttg-web-" stage))

(defn site-domain
  "prod is the apex; any other stage is a subdomain of it."
  [stage]
  (if (= "prod" stage) apex-domain (str stage "." apex-domain)))

(defn- identity! [cfg]
  (let [arn (str/trim (aws-out cfg "sts" "get-caller-identity" "--query" "Arn" "--output" "text"))]
    (println (format "AWS profile %s -> %s" (get-in cfg [:aws :profile]) arn))
    arn))

(defn setup
  "Parses the task args and returns [stage cfg], after printing which AWS identity will be used."
  [args]
  (let [{:keys [stage] :as opts} (parse-args args)
        cfg (do (when-not (#{"dev" "prod"} stage)
                  (throw (ex-info (str "Unknown stage \"" stage "\". Use dev or prod.") {})))
                (require-aws!)
                (with-profile (secrets) opts))]
    (identity! cfg)
    [stage cfg]))

(defn stack-outputs
  "{:OutputKey value} for a stack, or nil when it doesn't exist."
  [cfg stack]
  (let [{:keys [exit out]} (p/sh {:env (aws-env cfg)} "aws" "cloudformation" "describe-stacks" "--stack-name" stack
                                 "--query" "Stacks[0].Outputs" "--output" "json")]
    (when (zero? exit)
      (into {} (map (juxt (comp keyword :OutputKey) :OutputValue)) (json/parse-string out true)))))

(defn- outputs [cfg stage]
  (or (stack-outputs cfg (stack-name stage))
      (throw (ex-info (str "No " (stack-name stage) " stack. Run bb web:infra " stage " first.") {}))))

(defn site-url [cfg stage] (:SiteUrl (outputs cfg stage)))

(defn- web-defines
  "The release build's API and sign-in settings, from the ttg-api-<stage> stack when it exists."
  [cfg stage]
  (when-let [{:keys [ApiUrl AuthDomain ClientId]} (stack-outputs cfg (str "ttg-api-" stage))]
    {'ttg.config/api-url ApiUrl 'ttg.config/auth-domain AuthDomain 'ttg.config/client-id ClientId 'ttg.config/dev-owner ""}))

(defn- zone-id!
  "The hosted zone's id, without the /hostedzone/ prefix Route 53 lists it with."
  [cfg]
  (let [id (str/trim (aws-out cfg "route53" "list-hosted-zones-by-name" "--dns-name" apex-domain
                              "--query" (format "HostedZones[?Name=='%s.'].Id | [0]" apex-domain) "--output" "text"))]
    (when (= "None" id)
      (throw (ex-info (str "No hosted zone for " apex-domain ". See infra/README.md: The custom domain.") {})))
    (fs/file-name id)))

(defn domain-infra!
  "bb domain:infra [--profile p]: create or update the certificate every stage shares. The domain's nameservers must be set first."
  [& args]
  (let [[_ cfg] (setup args)]
    (aws! cfg "cloudformation" "deploy" "--template-file" "infra/domain.yml" "--stack-name" domain-stack
          "--parameter-overrides" (str "ZoneName=" apex-domain) (str "HostedZoneId=" (zone-id! cfg))
          "--no-fail-on-empty-changeset" "--tags" "app=table-top-galaxy")
    (println "Certificate:" (:CertificateArn (stack-outputs cfg domain-stack)))))

(defn infra!
  "bb web:infra [stage] [--profile p]: create or update the bucket, distribution and prototype password, on the custom domain once its stack exists."
  [& args]
  (let [[stage cfg] (setup args)
        {:keys [HostedZoneId CertificateArn]} (stack-outputs cfg domain-stack)
        ;; Parameters go through an owner-only temp file, not the command line, so the password stays out of `ps`.
        params (fs/create-temp-file {:prefix "ttg-web-params-" :suffix ".json" :posix-file-permissions "rw-------"})
        param (fn [k v] {:ParameterKey k :ParameterValue v})]
    (spit (str params) (json/generate-string [(param "Stage" stage)
                                              (param "BasicAuthUser" (get-in cfg [:web :user]))
                                              (param "BasicAuthPassword" (get-in cfg [:web :password]))
                                              (param "SiteDomain" (if CertificateArn (site-domain stage) ""))
                                              (param "HostedZoneId" (or HostedZoneId ""))
                                              (param "CertificateArn" (or CertificateArn ""))]))
    (try
      (aws! cfg "cloudformation" "deploy" "--template-file" template "--stack-name" (stack-name stage)
            "--parameter-overrides" (str "file://" params) "--no-fail-on-empty-changeset"
            "--tags" "app=table-top-galaxy" (str "stage=" stage))
      (finally (fs/delete-if-exists params)))
    (println "Site:" (:SiteUrl (outputs cfg stage)))))

(defn- prototypes?
  "Only dev carries the prototypes. Prod's sync runs with --delete, so anything left out is removed from the site."
  [stage]
  (not= "prod" stage))

(defn- stage-site!
  "Copies the landing and error pages and the app into .local/dist/<stage>, plus the built prototypes on dev."
  [stage]
  (let [out (fs/path ".local" "dist" stage)]
    (fs/delete-tree out)
    (fs/create-dirs out)
    (when (prototypes? stage)
      (doseq [page site-pages] (fs/copy (fs/path "prototypes" page) (fs/path out page))))
    (doseq [f (fs/list-dir "prototypes/site")] (fs/copy f (fs/path out (fs/file-name f))))
    (when-not (prototypes? stage) (spit (str (fs/path out "index.html")) prod-landing))
    (doseq [f web-files :let [to (fs/path out "roller" f)]]
      (fs/create-dirs (fs/parent to))
      (fs/copy (fs/path web-public f) to))
    out))

(defn deploy!
  "bb web:deploy [stage] [--profile p]: rebuild the apps/web app and, on dev, the prototypes, upload them, and clear CloudFront's cache."
  [& args]
  (let [[stage cfg] (setup args)
        {:keys [BucketName DistributionId SiteUrl]} (outputs cfg stage)
        defines (web-defines cfg stage)
        dir (do (when (prototypes? stage) (proto/build!)) (quality/build-web! defines) (stage-site! stage))]
    ;; no-cache: browsers and CloudFront revalidate each load, so a redeploy shows up at once.
    (aws! cfg "s3" "sync" (str dir) (str "s3://" BucketName) "--delete" "--cache-control" "no-cache")
    (aws! cfg "cloudfront" "create-invalidation" "--distribution-id" DistributionId "--paths" "/*"
          "--query" "Invalidation.Id" "--output" "text")
    (println "Deployed" (if (prototypes? stage) (str (count site-pages) " prototypes and the app") "the app") "to" SiteUrl
             (if defines "(signed-in app, API from ttg-api-" "(no API stack yet: the library page says so)") (when defines (str stage ")")))
    (println "App:" (str SiteUrl "/roller/index.html"))))

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
