# Deploy identity

`bb web:*` runs the AWS CLI as an IAM user named `ttg-deployer`. It has one customer managed policy, `ttg-web-deploy`
(from `deployer-policy.json`). A managed policy is used because inline user policies are capped at 2,048 characters. The policy allows only:

- the `ttg-web-*` CloudFormation stacks
- the `ttg-web-*` S3 buckets and the files in them
- the CloudFront resources the stack creates. CloudFront's create calls don't accept a resource ARN, so this part is `"*"`.

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
