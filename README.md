# Table Top Galaxy

Character sheets, campaign manager and dice roller, starting with Genesys and its Android setting. The plan is in [docs/plan.md](docs/plan.md).

## Local commands

Install [babashka](https://babashka.org) with `brew install borkdude/brew/babashka`. It's a prebuilt binary, so you don't need GraalVM.

```bash
bb tasks     # list every command
bb setup     # check java, clojure, node, aws, sls
bb up        # DynamoDB Local on :8000 with the ttg tables (Java, no Docker)
bb status
bb down
bb reset     # stop and wipe local data
bb proto     # serve prototypes/ on http://localhost:8090
bb proto-build  # rebuild prototypes from prototypes/src (shared dice code)
bb dev       # watch apps/web (the dice roller) with hot reload on http://localhost:8280
bb build     # release build of apps/web into apps/web/public/js
bb test      # libs tests on the JVM and Node, apps/web tests on Node
bb lint      # clj-kondo over libs, apps/web and tools/bb
```

Local state (DynamoDB Local install, data, pids, logs) lives in `.local/`, which is gitignored.

Layout follows the argvader-tools monorepo: `apps/` (web, serverless), `libs/` (shared cljc domain), `tools/` (env, build, bb tasks).

## Deploying the prototypes (S3 + CloudFront)

The app is served from a private S3 bucket through CloudFront (`infra/web.yml`). Dev also carries the prototypes and a
landing page; on prod the address opens the app. The app signs in through Cognito and carries no book content. The
prototypes do, so anything outside the app needs a password (HTTP basic auth, done in a CloudFront Function).

One-time setup:

1. Install AWS CLI v2 without sudo:
   `curl -sSo /tmp/awscliv2.zip https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip && unzip -q -o /tmp/awscliv2.zip -d /tmp && /tmp/aws/install -i ~/.local/aws-cli -b ~/.local/bin`
2. Create a deploy identity (one time, as an account admin, e.g. in AWS CloudShell) with the policy in
   `infra/deployer-policy.json`, then `aws configure --profile ttg-deployer` with its access key and region `us-east-1`.
   The commands are in `infra/README.md`.
3. `cp secrets.example.edn secrets.edn` and set the profile, region and site password (12+ characters). `secrets.edn` is gitignored.

Then:

```bash
bb web:infra          # create/update bucket, distribution, prototype password (first run takes ~5-10 min)
bb web:deploy         # proto-build + apps/web release build (at /roller/index.html), upload, invalidate the CloudFront cache
bb web:url            # print https://dxxxx.cloudfront.net
bb web:remove dev     # empty the bucket and delete everything
```

Each task takes a stage, `dev` (the default) or `prod`, and each stage is its own stack: `ttg-web-dev` or `ttg-web-prod`.

Choosing the AWS identity. Each task prints the profile and the identity it is using before it does anything:

```bash
bb web:whoami                          # profile from secrets.edn
bb web:infra --profile ttg-deployer    # one run with another profile
TTG_AWS_PROFILE=ttg-deployer bb web:deploy prod
```

The order is the `--profile` flag, then `TTG_AWS_PROFILE`, then `secrets.edn :aws :profile`. If AWS keys are exported in your
shell, these tasks ignore them, so the profile you chose is the one used.
