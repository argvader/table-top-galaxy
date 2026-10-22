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
```

Local state (DynamoDB Local install, data, pids, logs) lives in `.local/`, which is gitignored.

Layout follows the argvader-tools monorepo: `apps/` (web, serverless), `libs/` (shared cljc domain), `tools/` (env, build, bb tasks).
