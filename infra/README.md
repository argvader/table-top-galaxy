# Deploy identity

`bb web:*` runs the AWS CLI as an IAM user named `ttg-deployer`. It has one customer managed policy, `ttg-web-deploy`
(from `deployer-policy.json`). A managed policy is used because inline user policies are capped at 2,048 characters. The policy allows only:

- the `ttg-web-*` CloudFormation stacks
- the `ttg-web-*` S3 buckets and the files in them
- the CloudFront resources the stack creates. CloudFront's create calls don't accept a resource ARN, so this part is `"*"`.
- the `ttg-api-*` stacks and bucket, the `ttg-*` DynamoDB table, the `ttg-api-*` Lambda, its role and log group,
  HTTP APIs, and Cognito user pools (see "The API" below). Cognito pool ARNs carry ids, not names, so those are
  scoped to the account's pools rather than by name.

Create the user once, as an account admin. AWS CloudShell is already signed in, so it's the easiest place. Upload
`deployer-policy.json` to CloudShell first (Actions, then Upload file):

```bash
aws iam create-user --user-name ttg-deployer --tags Key=app,Value=table-top-galaxy
aws iam create-policy --policy-name ttg-web-deploy --policy-document file://deployer-policy.json \
  --description "Table Top Galaxy: deploy ttg-web-* stacks" --tags Key=app,Value=table-top-galaxy
aws iam attach-user-policy --user-name ttg-deployer \
  --policy-arn arn:aws:iam::108359914472:policy/ttg-web-deploy
aws iam create-access-key --user-name ttg-deployer   # prints AccessKeyId + SecretAccessKey once
```

Then, on your machine:

```bash
aws configure --profile ttg-deployer     # paste the key pair; region us-east-1; output json
aws sts get-caller-identity --profile ttg-deployer
```

`secrets.edn` points at the profile with `:aws {:profile "ttg-deployer" :region "us-east-1"}`.

Later, when deploys move to CI or the Lambdas arrive, `bb web:infra` will need more actions. Add them to `deployer-policy.json`, then publish
a new default version:

```bash
aws iam create-policy-version --policy-arn arn:aws:iam::108359914472:policy/ttg-web-deploy \
  --policy-document file://deployer-policy.json --set-as-default
```

A managed policy keeps at most 5 versions, so delete old ones with `aws iam delete-policy-version` when that limit is reached.

To retire the user: `aws iam delete-access-key`, `aws iam detach-user-policy`, `aws iam delete-user`, then `aws iam delete-policy`.

## The API (characters, campaigns, library, sign-in)

`infra/api.yml` is the backend: the `ttg-<stage>` DynamoDB table, a Cognito user pool with a hosted sign-in page,
the `ttg-api-<stage>` Lambda, and an HTTP API that checks the Cognito token before the Lambda runs.
`infra/api-artifacts.yml` is the private bucket the Lambda's code is uploaded to. The table and user pool are
retained if the stack is deleted, so characters, campaigns and accounts survive `bb api:remove`.

The deploy user needed new actions for this. Publish the updated `deployer-policy.json` once, as an admin
(CloudShell is easiest):

```bash
aws iam create-policy-version --policy-arn arn:aws:iam::108359914472:policy/ttg-web-deploy \
  --policy-document file://deployer-policy.json --set-as-default
```

The `HttpApi` statement uses `apigateway:*` on purpose. Creating the HTTP API's stage is checked against
`apigateway:TagResource` (CloudFormation tags every resource it makes), but the IAM console rejects that action by
name. The wildcard is accepted, and the resources stay limited to HTTP APIs and their tags.

Then, on your machine:

```bash
bb api:deploy dev                         # table, Cognito, Lambda, HTTP API (needs the ttg-web-dev site for its URL)
bb user:add you@example.com dev --name "Gary"   # Cognito emails a temporary password; sign-up is invite-only
bb web:deploy dev                         # rebuilds the app pointing at the API and sign-in
bb api:logs dev                           # follow the Lambda's log
```

Sign in from the app's top bar at `<site>/roller/index.html`. The app opens without the site password, and its data
needs a Cognito sign-in. The password only guards what's outside the app: on dev, the prototype pages, which carry book
content. Portraits and images are open to anyone with their random address.

## The custom domain (tt-galaxy.com)

| Stage | Site | Sign-in |
| ----- | ---- | ------- |
| dev | `dev.tt-galaxy.com` | `auth.dev.tt-galaxy.com` |
| prod | `tt-galaxy.com` | `auth.tt-galaxy.com` |

`infra/domain.yml` (stack `ttg-web-domain`) holds the one certificate all stages share. Until that stack exists, the
site and sign-in stay on their AWS names.

Two steps need an admin, once (CloudShell is easiest). Upload `deployer-domain-policy.json` first. The first block
reuses the zone Route 53 made at registration if there is one, and points the registration at it:

```bash
ZONE_ID=$(aws route53 list-hosted-zones-by-name --dns-name tt-galaxy.com \
  --query "HostedZones[?Name=='tt-galaxy.com.'].Id | [0]" --output text | cut -d/ -f3)
[ -z "$ZONE_ID" ] || [ "$ZONE_ID" = "None" ] && ZONE_ID=$(aws route53 create-hosted-zone --name tt-galaxy.com \
  --caller-reference "ttg-$(date +%s)" --query HostedZone.Id --output text | cut -d/ -f3)
NS=$(aws route53 get-hosted-zone --id "$ZONE_ID" --query DelegationSet.NameServers --output text)
aws route53domains update-domain-nameservers --region us-east-1 --domain-name tt-galaxy.com \
  --nameservers $(printf 'Name=%s ' $NS)

aws iam create-policy --policy-name ttg-domain-deploy --policy-document file://deployer-domain-policy.json \
  --description "Table Top Galaxy: certificate and DNS records" --tags Key=app,Value=table-top-galaxy
aws iam attach-user-policy --user-name ttg-deployer \
  --policy-arn arn:aws:iam::108359914472:policy/ttg-domain-deploy
```

These actions are a second policy because `ttg-web-deploy` is near the 6,144-character limit on managed policies.
`ttg-deployer` can only change records named `tt-galaxy.com` or under it.

Wait until `dig +short NS tt-galaxy.com` lists the nameservers, or ACM can't validate and `bb domain:infra` hangs. Then,
in this order:

```bash
bb domain:infra          # certificate (a few minutes)
bb web:infra dev         # dev.tt-galaxy.com on the distribution; SiteUrl becomes the custom domain
bb api:deploy dev        # Cognito callbacks and CORS move to it, and auth.dev.tt-galaxy.com is created
bb web:deploy dev        # rebuilds the app against the new sign-in domain
```

`bb api:deploy` moves the callbacks and CORS over, so sign-in stops working at the `cloudfront.net` address. The old
Cognito prefix domain stays, unused.

## The database (Aurora PostgreSQL)

Characters and campaigns live in Aurora Serverless v2 (`infra/data.yml`, stack `ttg-api-<stage>-data`). The Lambda uses
the RDS Data API, so it stays outside the VPC, and the cluster takes no inbound connections. The library is still on DynamoDB.

The deploy user needs one more policy, once, as an admin. Upload `deployer-data-policy.json` to CloudShell first:

```bash
aws iam create-policy --policy-name ttg-data-deploy --policy-document file://deployer-data-policy.json \
  --description "Table Top Galaxy: Aurora cluster and migrations" --tags Key=app,Value=table-top-galaxy
aws iam attach-user-policy --user-name ttg-deployer \
  --policy-arn arn:aws:iam::108359914472:policy/ttg-data-deploy
```

Then, in this order:

```bash
bb data:infra dev        # the cluster (about 10 minutes the first time)
bb db:migrate dev        # applies new files from apps/serverless/migrations
bb web:infra dev         # the portrait bucket and its CloudFront path
bb api:deploy dev        # points the Lambda at the database and bucket
bb web:deploy dev
```

The cluster pauses after an hour idle, and the first request afterwards waits about 15 seconds. For a game night, keep it
awake with `bb data:infra prod --min-capacity 0.5`, and let it pause again afterwards with `--min-capacity 0`. It costs
very little beyond storage while paused and roughly $44 a month if left awake.

Deleting the stack takes a final snapshot, and the cluster's deletion protection has to be turned off first.
Portraits are in their own retained bucket, because `bb web:deploy` syncs the site bucket with `--delete`.

### Loading the library on a deployed stage

Book content never ships in the bundle, and players build from the GM's library, so a new stage's campaigns are empty
until the GM loads it:

```bash
bb library:export android .local/library.json
```

Then sign in as the GM, open Library, choose Import and pick that file. The app sends it in 100-entry chunks, which keeps
each request inside API Gateway's 29-second limit.

### The campaign manager

The GM opens it from the Campaign manager button on a campaign's page. Its written prep (arc, NPCs, the betrayal ledger)
holds the GM's secrets, so it isn't in the web bundle, which every player downloads. It lives in the database, and each
campaign loads it once from `content/manifest-destiny/manager.json`:

```bash
bb manager:seed dev <campaign-id>     # the id is in the campaign page's address; use local under bb dev
```

`bb build` and `bb web:deploy` fail if any of that text reaches the bundle. The party on the page comes from the
campaign's members, and each edit is kept in the browser until the server has it, so a failed save shows a banner and
arrives on the next visit.

### Going live for a game

```bash
bb data:infra prod && bb db:migrate prod
bb web:infra prod && bb api:deploy prod && bb web:deploy prod
bb manager:seed prod <campaign-id>                  # after the GM creates the campaign
bb user:add player@example.com prod --name "Name"   # once per player
bb data:infra prod --min-capacity 0.5               # before the session; set back to 0 afterwards
```

On prod, `tt-galaxy.com` opens the app and the prototypes aren't uploaded. The upload deletes anything it doesn't carry,
so a first deploy removes prototype pages already there. They stay on dev.

Check `aws rds describe-db-cluster-snapshots` or the console for a snapshot before the session.
