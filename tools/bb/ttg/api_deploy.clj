(ns ttg.api-deploy
  "bb api:* and user:add: the API stack (infra/api.yml) and its code bucket (infra/api-artifacts.yml), deployed with
   the AWS CLI as ttg-deployer. The Lambda's code goes up as api-<hash>.zip, so a deploy only changes it when it changed."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [ttg.deploy :as deploy]
            [ttg.quality :as quality])
  (:import (java.security MessageDigest)))

(def bundle "apps/serverless/serverless/api.js")

(defn- artifacts-stack [stage] (str "ttg-api-" stage "-artifacts"))
(defn- api-stack [stage] (str "ttg-api-" stage))

;; --disable-rollback: a failed create stops where it failed instead of rolling back, so the retained table and user
;; pool aren't orphaned (a rerun would then trip over the table's name) and the next deploy picks up from there.
(defn- cfn-deploy! [cfg template stack & params]
  (apply deploy/aws! cfg "cloudformation" "deploy" "--template-file" template "--stack-name" stack
         "--no-fail-on-empty-changeset" "--capabilities" "CAPABILITY_NAMED_IAM" "--disable-rollback"
         "--tags" "app=table-top-galaxy"
         (when (seq params) (cons "--parameter-overrides" params))))

(defn- sha12 [file]
  (let [d (.digest (MessageDigest/getInstance "SHA-256") (fs/read-all-bytes file))]
    (subs (str/join (map #(format "%02x" %) d)) 0 12)))

(defn- zip-bundle!
  "api.js at the zip's root, as the Lambda's handler (api.handler) expects. Returns [zip-path key]."
  []
  (let [dir (fs/create-dirs (fs/path ".local" "dist" "api"))
        key (str "api-" (sha12 bundle) ".zip")
        zip (str (fs/path dir key))]
    (fs/delete-if-exists zip)
    (fs/zip zip [bundle] {:root "apps/serverless/serverless"})
    [zip key]))

(defn- auth-domain-params
  "The sign-in domain's stack parameters, once the site answers on its custom domain. Cognito needs that parent to resolve."
  [cfg stage origin]
  (let [domain (deploy/site-domain stage)
        {:keys [HostedZoneId CertificateArn]} (deploy/stack-outputs cfg deploy/domain-stack)]
    (when (= origin (str "https://" domain))
      [(str "AuthDomainName=auth." domain) (str "HostedZoneId=" HostedZoneId) (str "CertificateArn=" CertificateArn)])))

(defn- data-params
  "Where the Lambda finds the database and the portrait bucket."
  [cfg stage]
  (let [{:keys [ClusterArn SecretArn]} (or (deploy/stack-outputs cfg (str "ttg-api-" stage "-data"))
                                           (throw (ex-info (str "Create the database first: bb data:infra " stage) {})))
        {:keys [PortraitBucket]} (deploy/stack-outputs cfg (str "ttg-web-" stage))]
    (when-not PortraitBucket
      (throw (ex-info (str "Update the site first: bb web:infra " stage) {})))
    [(str "DbClusterArn=" ClusterArn) (str "DbSecretArn=" SecretArn) (str "PortraitBucket=" PortraitBucket)]))

(defn deploy!
  "bb api:deploy [stage] [--profile p]: build the Lambda, upload it, create or update the API stack."
  [& args]
  (let [[stage cfg] (deploy/setup args)
        origin (or (deploy/site-url cfg stage)
                   (throw (ex-info (str "Deploy the site first: bb web:infra " stage) {})))
        data (data-params cfg stage)]
    (cfn-deploy! cfg "infra/api-artifacts.yml" (artifacts-stack stage) (str "Stage=" stage))
    (quality/build-api!)
    (let [bucket (:ArtifactBucket (deploy/stack-outputs cfg (artifacts-stack stage)))
          [zip key] (zip-bundle!)]
      (deploy/aws! cfg "s3" "cp" zip (str "s3://" bucket "/" key) "--only-show-errors")
      (apply cfn-deploy! cfg "infra/api.yml" (api-stack stage)
             (str "Stage=" stage) (str "CodeBucket=" bucket) (str "CodeKey=" key) (str "WebOrigin=" origin)
             (concat data (auth-domain-params cfg stage origin))))
    (let [{:keys [ApiUrl AuthDomain]} (deploy/stack-outputs cfg (api-stack stage))]
      (println "API:" ApiUrl)
      (println "Sign-in:" AuthDomain)
      (println (str "Next: bb user:add you@example.com " stage ", then bb web:deploy " stage " so the app uses this API.")))))

(defn outputs!
  "bb api:url [stage]: print the API stack's outputs."
  [& args]
  (let [[stage cfg] (deploy/setup args)]
    (doseq [[k v] (sort (deploy/stack-outputs cfg (api-stack stage)))] (println (format "%-12s %s" (name k) v)))))

(defn logs!
  "bb api:logs [stage]: follow the Lambda's log."
  [& args]
  (let [[stage cfg] (deploy/setup args)]
    (deploy/aws! cfg "logs" "tail" (str "/aws/lambda/ttg-api-" stage) "--follow" "--since" "15m")))

(defn add-user!
  "bb user:add <email> [stage] [--name \"Display name\"]: create an account; Cognito emails a temporary password."
  [& args]
  (let [email (first (filter #(str/includes? % "@") args))
        display (second (drop-while #(not= "--name" %) args))
        rest-args (remove #{email "--name" display} args)
        [stage cfg] (deploy/setup rest-args)
        pool (:UserPoolId (deploy/stack-outputs cfg (api-stack stage)))]
    (when-not email (throw (ex-info "Usage: bb user:add someone@example.com [stage] [--name \"Name\"]" {})))
    (when-not pool (throw (ex-info (str "No API stack for " stage ". Run bb api:deploy " stage " first.") {})))
    (deploy/aws! cfg "cognito-idp" "admin-create-user" "--user-pool-id" pool "--username" email
                 "--user-attributes" (str "Name=email,Value=" email) "Name=email_verified,Value=true"
                 (str "Name=name,Value=" (or display (first (str/split email #"@"))))
                 "--desired-delivery-mediums" "EMAIL" "--query" "User.UserStatus" "--output" "text")
    (println "Cognito has emailed" email "a temporary password.")))

(defn remove!
  "bb api:remove <stage>: delete the API and artifact stacks. The table and user pool are kept (Retain)."
  [& args]
  (when-not (some #{"dev" "prod"} args)
    (throw (ex-info "Name the stage to remove: bb api:remove dev" {})))
  (let [[stage cfg] (deploy/setup args)
        bucket (:ArtifactBucket (deploy/stack-outputs cfg (artifacts-stack stage)))]
    (doseq [stack [(api-stack stage) (artifacts-stack stage)]]
      (when (and (= stack (artifacts-stack stage)) bucket)
        (deploy/aws! cfg "s3" "rm" (str "s3://" bucket) "--recursive" "--only-show-errors"))
      (deploy/aws! cfg "cloudformation" "delete-stack" "--stack-name" stack)
      (deploy/aws! cfg "cloudformation" "wait" "stack-delete-complete" "--stack-name" stack))
    (println (str "Removed the API stacks. Table ttg-" stage " and the user pool were kept; delete them in the console if you mean it."))))
