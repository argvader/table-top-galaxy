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
- **IaC (decided 2026-10-27):** plain CloudFormation driven by bb and the AWS CLI, like the site (`infra/web.yml`), instead of the Serverless Framework (v4 needs an account). `infra/api.yml` holds the table, Cognito, the Lambda and the HTTP API; the WebSocket API joins it at M6. Locally, `bb dev` runs the same handler behind a small Node server.
- **Persistence (2026-10-27):** characters (owner CRUD, GM of their campaign can read), campaigns (GM-only `:gm` data stripped server-side), membership, and 7-day invite links are in the API now. M4/M5 add the screens.
- **Aurora (decided 2026-10-30, for Session 0):** characters and campaigns moved to Aurora PostgreSQL Serverless v2 through the RDS Data API (`infra/data.yml`, `apps/serverless/migrations`), so the Lambda stays outside the VPC. It pauses at 0 ACU when idle. Tests and `bb dev` run the same SQL on PGlite. The library stays on DynamoDB until after Session 0 (ttg-7b9). Saves use optimistic locking on `version`, and a stale save gets a 409.
- **Client-side dice (decided 2026-10-24):** the table is a group of friends, so each browser rolls its own dice and posts the result to the shared feed. The server relays and stores rolls but doesn't make or verify them.
  - **The engine picks each face** using the platform's cryptographic RNG (exact 1/n odds per face, each roll independent). The 3D dice only act the result out: the throw is simulated offscreen (cannon-es) until every die rests flat, each die is repainted so the face it landed on carries the engine's face, then the recorded throw plays back (three.js, loaded lazily on the first roll). Physics is presentation, so a simulation quirk can never skew the odds. Reduced motion, no WebGL, or a load failure fall back to the 2D dice.
  - GM secret checks are rolled in the GM's browser. The server sends them only to the GM, so they're redacted server-side like other GM-only data.
  - The seeded RNG in `ttg.dice` stays for tests and replaying a logged roll. It is not a trust mechanism.

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
libs/                       # deps.edn {:paths ["src"]}, consumed via :local/root; tests in libs/test
  src/ttg/dice.cljc (+ dice/{rng,odds,model})  src/ttg/system.cljc  src/ttg/genesys/{model,rules,android}.cljc
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
| `bb proto-build` | Assemble `prototypes/src` pages with the shared dice code | done |
| `bb dev` | DynamoDB Local + the library API on :3000 (shadow watch + a Node server that feeds the Lambda handler API Gateway v2 events, no `sls`) + apps/web on :8280 | done (M3) |
| `bb api` | Only the library API on :3000 | done (M3) |
| `bb test` / `bb lint` | libs tests on the JVM and Node, clj-kondo | done |
| `bb seed` | Load a setting's book library into the running dev API (Manifest Destiny campaign joins at M7) | library done (M3) |
| `bb library:export` | Write a setting's library as an importable JSON file in `.local/library/` | done (M3) |
| `bb build` | Release builds for web and the library Lambda | done |
| `bb api:deploy [stage]` | CloudFormation (`infra/api.yml`): table, Cognito, Lambda, HTTP API; no Serverless Framework | done, not yet run |
| `bb web:deploy [stage]` | App to S3, CloudFront invalidation, plus the prototypes on dev only (prod's address opens the app); the app is built against the stage's API when it exists | done |
| `bb api:logs` / `bb api:remove` / `bb user:add` | Tail the Lambda log, tear down the API (data kept), create an invite-only account | done |

The re-frame app validates app-db with spec after each event when `goog.DEBUG` is on, following the `validate-spec` pattern in the user's CLAUDE.md.

**Session pipeline:** recording, transcription and summaries stay local Claude skills, in `campaigns/manifest-destiny/` and `.claude/skills/`. The results get published into the app as the campaign's Chronicle, one chronicle per campaign, read by all its members. This replaces the earlier idea of feeding argvader-tools `apps/ttrpg_session` summaries into the manager's session log.

## Milestones (vertical slices; each one gets a bd issue)
1. **Phase 0 prototypes** approved: dice, sheet, builder, GM dashboard, campaign manager.
2. **Dice engine (cljc) and a standalone roller page.** No backend yet. Fully tested.
3. **Content library.** The GM creates and edits talents, items, qualities, archetypes and careers, with JSON/CSV import.
   - Done 2026-10-27. The library lives in DynamoDB (`LIBRARY#owner` / `<TYPE>#id`, entry stored as EDN) behind `apps/serverless` (handler -> service -> gateway). Until Cognito (M4) the dev server alone may name the owner in an `x-ttg-owner` header.
   - Book content reaches a library only through `bb library:export` + Import (or `bb seed`), so no book text ships in the bundle.
   - One field schema (`ttg.library.fields`) drives the editor form, CSV import and CSV export; the browser and the Lambda validate with the same `ttg.library` code.
   - The web app routes with hashes (`#/library/weapons/knife`) so it runs under `/roller/` on the prototype site without rewrites.
4. **Character builder and sheet.** Saved locally first, then synced through the API with Cognito auth.
   - Built 2026-10-30, not yet deployed. `ttg.genesys.rules` holds the rules, shared with the API. The builder keeps a browser draft until the character is created, and the sheet autosaves. Portraits go to their own retained bucket; the 8 palettes in `ttg.theme` have no picker for now, so every sheet uses the default (ttg-8ax).
   - Encumbrance and defense follow the book in both screens: worn armor counts 3 less, and defense caps at 4. The two prototypes disagreed on this.
   - The roll drawer: tapping a skill, characteristic or weapon opens it on the sheet, a side panel on desktop and a bottom sheet on a phone. It shares the Dice roller's pool, spending and log, shows damage on a hit, and adds any Setback from encumbrance.
   - The sheet follows the prototype's layout: on a desktop a Skills panel on the left and the tabs on the right, merged into one set of tabs below 1000px. The Crits tab rolls and heals critical injuries, and they save on the character. The prototype's History tab is dropped.
   - Not ported for Session 0: story points, and spends that change the sheet (Recover 1 strain).
5. **Campaigns.** Invites, roles, player sheets visible to the GM, and server-side redaction of GM-only data.
   - Screens built 2026-10-30: campaign list and detail, invite links, `#/join/<code>`, and a read-only sheet for the GM.
6. **Realtime table.** Shared roll feed, Story Point pool, initiative, and live wound/strain on the GM dashboard.
7. **Campaign manager.** Acts and sessions, NPCs, factions, betrayal ledger, Session 0 questions, and session log. Built into the app at `#/campaigns/<id>/manager` as a re-frame port of the prototype, GM only. Its written prep is seeded per campaign from `content/manifest-destiny/manager.json` and kept out of the web bundle, and its party comes from the campaign's members. Session 0 has a run sheet and a Debt step.
   - Manifest Destiny gets seeded as the first real campaign.
   - A save that fails stays in the browser and sends on the next visit.
   - The Chronicle (sessions and wiki pages, all members) lives at `#/campaigns/<id>/chronicle`. `bb chronicle:publish` writes it from `campaigns/manifest-destiny/chronicle`, showing what will change first; the API only reads it. Pages render through a small Markdown reader that builds elements rather than HTML, so nothing in a page can run.
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
