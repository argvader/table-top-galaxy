# Table Top Galaxy: Genesys-first character sheet, campaign manager and dice roller

## Context

D&D Beyond and Demiplane don't support Genesys or its Android setting, and the user is now running **Manifest Destiny**, an Android/Genesys campaign. Its campaign doc relies on several Android-specific things:
- **Debt** owed to a creditor corp.
- **Session 0 "unsettling questions"**, each tied to a specific act.
- Four corps, each with a public role and a secret agenda.
- A **betrayal ledger** of which secrets the players have found.
- Named townsfolk who matter in the final act.
- Four Kishōtenketsu acts (Ki, Shō, Ten, Ketsu).

The goal is a tool in the style of Demiplane, built Genesys-first, where:
- players manage their characters online,
- dice rules are built into the app,
- the GM runs the campaign,
- the code is shaped so other systems can plug in later.

UX quality is the top priority.

**Decisions made:**
- Serverless AWS.
- Mixed in-person and online play, with a live shared roll feed.
- Phone and desktop layouts get equal weight.
- The GM enters book content into a private library. The app ships rules logic only, no copyrighted text.

The repo `/mnt/w/projects/skunkwerx/table-top-galaxy` is empty, with no commits yet.

## Approach: design the UX first, then build in thin vertical slices

### Phase 0: UX prototypes (before any production code)
Build clickable HTML prototypes and publish them as private artifacts the user can review and comment on. Each one gets a phone layout and a desktop layout.
1. **Dice roller.**
   - Build a pool from the six Genesys dice: Ability/Proficiency, Difficulty/Challenge, Boost/Setback.
   - Tap to upgrade or downgrade dice.
   - Show the result with automatic cancellation and the net outcome.
   - Show a "spend Advantage/Triumph" helper.
   - Dice faces use custom SVG symbols. The FFG font can't be used.
2. **Character sheet.**
   - Header: wounds and strain as tappable trackers, soak, melee/ranged defense, XP.
   - Characteristics, and skills with their pool preview. Tapping a skill opens the roller with that pool preloaded.
   - Tabs for Talents, Gear, Crits, Debt, Notes and History.
   - Inspiration: the D&D Beyond sheet layout, and Demiplane's clean panels and roll toasts.
3. **Character builder wizard.** Steps, with live validation of the XP budget and the talent pyramid:
   - Archetype
   - Career
   - Spend XP on characteristics, skills and talents
   - Gear
   - Motivations
   - Debt and creditor corp
   - Review
4. **GM campaign dashboard.**
   - Party at a glance, with live wounds and strain.
   - Shared Story Point pool.
   - Initiative slots.
   - Live roll feed.
   - Quick links to NPCs, factions and the session log.
5. **Campaign manager.**
   - Acts and sessions.
   - NPC roster (the townsfolk).
   - Factions with a public role, a secret agenda and a "when it breaks" act.
   - Betrayal ledger, where each secret has its own reveal state.
   - Session 0 questions linked to acts.
   - GM-only fields vs player-visible fields are clearly marked.

We iterate on each prototype until the user signs off. That signed-off design becomes the spec for the next phases.

### Architecture (serverless AWS)
- **Frontend:** ClojureScript with re-frame and Reagent, using shadow-cljs with `:target :browser` and output to `public/js`.
  - Hosted on S3 + CloudFront.
  - Installable PWA so it works well on phones at the table.
- **API:** API Gateway HTTP API calling Lambda functions. These are ClojureScript `:node-library` builds that export `handler`, with output to `serverless/`.
- **Realtime:** API Gateway WebSocket API. There is a connections table, and broadcasts go out per campaign channel for:
  - rolls,
  - sheet changes (wounds, strain),
  - the Story Point pool,
  - initiative.
- **Data:** a single DynamoDB table.
  - Keys:
    - `CAMPAIGN#id` / `CHAR#id`, `NPC#id`, `FACTION#id`, `SECRET#id`, `SESSION#id`
    - `CAMPAIGN#id#ROLLS` / `ts` for the roll log
    - `LIBRARY#ownerId` / `TALENT#…`, `ITEM#…`, `QUALITY#…` for content
    - `USER#id` / `CAMPAIGN#id` for membership
  - A GSI handles "my characters" and "my campaigns."
- **Auth:** Cognito with Hosted UI and Google sign-in. The GM shares a campaign invite link. Roles are `gm` and `player` per campaign.
- **Visibility:** GM-only fields are stripped on the server, inside the Lambda, for player requests. They are never just hidden in the UI.
- **IaC:** Serverless Framework, matching argvader-tools. `apps/serverless/serverless.yml` defines the Lambdas, the HTTP and WebSocket APIs, DynamoDB, Cognito, and the S3 bucket plus CloudFront distribution as CloudFormation resources. `serverless-offline` runs the API locally.
- **Server-authoritative dice:** rolls happen in the Lambda so players can trust the shared feed. A local "private roll" mode exists for GM secrets.

### Shared domain code (cljc, used by the client and the Lambdas)
- `ttg.dice`: the dice engine. It holds die face definitions, pool construction, rolling with an injectable RNG, cancellation, and a probability calculator. The calculator powers a "chance of success" preview, which is better than what Demiplane offers.
- `ttg.system`: a protocol or multimethod `RuleSystem` for extensibility. Each system implements:
  - `characteristics`
  - `derived-stats`
  - `build-pool`
  - `validate-character`
  - `creation-steps`

  Genesys is the first implementation. Content stays data. Rules are code, one namespace per system. A fully generic rules engine is deliberately out of scope for v1.
- `ttg.genesys.model`: specs for character, skill, talent (tier, ranked, activation), item (encumbrance, hard points, qualities), crit injury, and Debt. This follows the `model.clj` spec convention.
- `ttg.genesys.rules`:
  - XP costs
  - talent pyramid validation
  - wound and strain thresholds
  - soak and defense
  - encumbrance
  - pool building: the higher of characteristic and skill ranks gives Ability dice, and the lower number of those get upgraded to Proficiency
- `ttg.genesys.android`: an Android setting layer with Debt and creditor corps, hacking and cybernetics hooks, and archetype data shape.

### Repo layout (argvader-tools monorepo pattern, own repo)
```
bb.edn                      # every local + devops command (bb tasks lists them)
apps/web/                   # re-frame PWA: deps.edn, shadow-cljs.edn (:target :browser), resources/config.edn
  src/cljs/ttg/{app,routes,events,effects,http}.cljs + feature folders (sheet/, roller/, builder/, campaign/)
apps/serverless/            # Lambdas: deps.edn, shadow-cljs.edn (:node-library), serverless.yml, package.json
  src/cljs/ttg/api/{handler,service,gateway}.cljs   # handler -> service -> gateway (DynamoDB), as in acronym
  src/cljs/ttg/realtime/{handler,service}.cljs
libs/                       # deps.edn {:paths ["."]}, consumed via :local/root
  ttg/dice.cljc  ttg/system.cljc  ttg/genesys/{model,rules,android}.cljc
  router/                   # bidi + pushy re-frame router, from argvader-tools libs/router
tools/                      # deps.edn {:paths ["."]}
  env/{core.clj,config.cljc}            # aero config + shadow load-env hook, from argvader-tools
  build/cljs/{shadow,watch,minified}.clj # from argvader-tools
  build/{s3,cloudfront}/core.clj         # web deploy, from argvader-tools
  bb/ttg/*.clj              # babashka task namespaces (local stack, checks, deploy orchestration)
prototypes/  docs/
```
Patterns carried over from argvader-tools:
- Each app's `deps.edn` pulls `local/libs` and `local/tools` via `:local/root "../../libs"` and exposes `:watch`, `:build`, `:release` and `:deploy` aliases.
- Config comes from `resources/config.edn` with aero `#profile` and `#include "secrets.edn"`. Secrets are gitignored. The web build injects config through the `build.cljs.shadow/load-env` build hook.
- The web app routes with `defmulti views` on the route handler and uses http-fx helpers in `http.cljs`. Each feature folder has its own `events/subs/views`.
- Lambdas use `core.async` with `<p!` around AWS SDK v3 calls, and export a single `handler` per function.

### bb tasks (local + devops)
bb orchestrates and shells out to `clojure -M:<alias>`, `npx shadow-cljs` and `sls` for the JVM and Node work.

| Task | What it does | Status |
|---|---|---|
| `bb setup` | Check java, clojure, node, npm, aws, sls | done |
| `bb up` / `bb down` / `bb status` | DynamoDB Local on :8000 (Java process, no Docker) with ttg tables | done |
| `bb reset` | Stop and wipe local data | done |
| `bb proto` | Serve `prototypes/` on :8090 | done |
| `bb dev` | `bb up` + shadow watch (web + serverless) + `sls offline` | M2 |
| `bb test` / `bb lint` | cljs tests on Node + JVM, clj-kondo | M2 |
| `bb seed` | Load sample library and Manifest Destiny campaign into local DynamoDB | M3 |
| `bb build` | Release builds for web and Lambdas | M2 |
| `bb deploy [stage]` | `sls deploy --stage`, web to S3, CloudFront invalidation | Infra |
| `bb logs fn` / `bb remove stage` | Tail Lambda logs, tear down a stage | Infra |

The re-frame app validates app-db with spec after each event when `goog.DEBUG` is on, following the `validate-spec` pattern in the user's CLAUDE.md.

**Later tie-in:** argvader-tools `apps/ttrpg_session` records and summarizes Discord sessions. Its summaries could feed the campaign manager's session log in M7.

## Milestones (vertical slices; each one gets a bd issue)
1. **Phase 0 prototypes** approved: dice, sheet, builder, GM dashboard, campaign manager.
2. **Dice engine (cljc) and a standalone roller page.** No backend yet. Fully tested.
3. **Content library.** The GM creates and edits talents, items, qualities, archetypes and careers, with JSON/CSV import.
4. **Character builder and sheet.** Saved locally first, then synced through the API with Cognito auth.
5. **Campaigns.** Invites, roles, player sheets visible to the GM, and server-side redaction of GM-only data.
6. **Realtime table.** Shared roll feed, Story Point pool, initiative, and live wound/strain on the GM dashboard.
7. **Campaign manager.** Acts and sessions, NPCs, factions, betrayal ledger, Session 0 questions, and session log.
   - Manifest Destiny gets seeded as the first real campaign.
8. **Hardening.**
   - PWA/offline sheet viewing.
   - Accessibility: dice results announced, color plus symbol, never color alone.
   - Backups via DynamoDB PITR.
   - Cost alarms.

A second system comes after milestone 8. It gets added as a new `RuleSystem` implementation to prove the extension point.

## Verification
- **Dice engine:**
  - Property-based tests with `test.check`: cancellation is correct, and Triumph counts as a success.
  - Distribution checks against known face tables.
  - Seeded-RNG tests that give the same result on the JVM and in Node.
- **Rules:** spec-driven tests for the XP budget, the talent pyramid and derived stats, run against a sample character.
- **Lint and specs:** `clj-kondo` on changed namespaces. `s/valid?` at Lambda handler boundaries.
- **Local stack:** `bb dev` runs shadow-cljs watch, DynamoDB Local and `sls offline`, which covers HTTP and WebSocket routes. Two browser windows (GM and player) confirm that rolls and wound changes show up live.
- **End-to-end UX:** Playwright runs at 390px (phone) and 1440px (desktop) for:
  - building a character,
  - rolling from a skill,
  - the GM seeing the roll,
  - redaction: a player can't read faction secrets.
- **Deploy:** `bb deploy dev`, then a smoke test against the CloudFront URL.

## First step after approval
1. Initialize the repo.
2. File bd issues for the milestones.
3. Build the Phase 0 dice roller prototype and share it for review. The dice roller UX is the foundation everything else reuses.
