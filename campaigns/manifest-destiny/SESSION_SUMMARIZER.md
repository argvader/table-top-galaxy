# Session Summarizer — Manifest Destiny

## Task Description

This is the session summarizer for **Manifest Destiny**, a **Genesys** campaign in the **Android** setting, using
*Shadow of the Beanstalk*. It's corporate frontier sci-fi: debt, megacorps and a gate nobody built. The crew are
outriders on the *Concord*, a town-sized consortium ship carrying 3,000 colonists toward the gate beyond Neptune.

The output goes into the campaign's **Chronicle** in the Table Top Galaxy app, where **every member of the campaign
reads it**. Treat each page as something all the players will see.

We capture audio, then transcribe with Deepgram (diarization on) to get per-speaker transcripts. When a recording fails,
the GM's written notes stand in for the transcript. See **When there is no transcript** under *Input Sources*.

Using the materials below, generate the following outputs as plain **Markdown**. Don't use MkDocs features.

All paths are relative to `campaigns/manifest-destiny/`.

---

## Rules that come first

### Privacy

Everything you write is published to all the players. **Never publish:**

- Lines and veils, or anything else from the safety discussion (`s0.lv`).
- GM truth answers (`s0.truth`), such as what's really in the sealed compartment or which rumor is true.
- Anything from the betrayal ledger: `truth`, unrevealed `clues`, or any secret whose `state` isn't `revealed`.
- NPC `gm` fields, `arc` fields or GM `notes`, or the twists and seeds.
- Prep notes on sessions (`prep`) and which act an answer pays off in (`act`).
- Anything from `content/Manifest Destiny — Campaign Arc.md`.

**A player's Session 0 answers may be published only if they were said aloud at the table**, which means they appear in
the transcript or the GM's notes. An answer that exists only in `manager.json` was never shared with the group, so leave
it out, even if the GM typed it in. When the recording failed and there's no way to tell, leave it out and list it for
the GM at the end of your reply.

If a transcript contains the GM thinking aloud, table talk about a secret, or a player stepping away to discuss something
privately, leave that out too. A safety discussion on the recording stays out even though it was said aloud.

When you finish, list anything you held back so the GM can decide. Never write it into the Chronicle.

### Canon

- There is **exactly one gate**, beyond Neptune. Nobody aboard has been through it. Never write "gates" in the plural, or
  suggest a network of them.
- The *Meridian* went through it fifteen years ago, sent one garbled transmission back, and fell silent.
- The four corps aboard are Weyland, Haas-Bioroid, Jinteki and NBN.
- Campaign acts are never named on published pages. The players don't know the structure.
- The dice and rules are Genesys. Say **Debt** for what a character owes: it's this campaign's name for Obligation.

---

## Output Required

### 1. Wiki Updates

Update or create pages in `chronicle/wiki/`, one folder per kind. Each page starts with front matter, then an H1:

```markdown
---
title: "Imre Vasko"
---

# Imre Vasko
```

The filename without `.md` is the page's slug. Use lowercase words joined by hyphens.

#### Player Characters (`chronicle/wiki/pcs/`)

Name the file after the character. Add what this session showed: abilities used, character development, relationships
formed. `roster.json` says who's who, and the app shows each PC's portrait itself, so don't generate or embed one.

#### NPCs (`chronicle/wiki/npcs/`)

For each NPC the crew met or heard about at the table, new or returning, create or update a page:

- **Name and role**
- **Appearance** and distinguishing traits
- **Personality** and behavior observed
- **Goals and motivations**, only as the crew knows or would infer them
- **Relationships** with PCs and other NPCs
- **Session history**: key interactions

> **Canonical names don't come from the transcript.** Deepgram writes what it hears, so invented names get split, merged
> or swapped for ordinary words. `roster.json` has the correct PC names. The `name` fields under `npcs` in
> `../../content/manifest-destiny/manager.json` have the correct NPC names. When someone plausibly matches one of these,
> use that spelling for the display name and the slug. To correct an existing page, use
> `bin/rename-npc.sh <old-slug> <new-slug> "<Old Name>" "<New Name>"`.
>
> Only the `name`, `role` and `pub` fields of an NPC are public, and only once that NPC has appeared at the table.

#### Factions (`chronicle/wiki/factions/`)

What the crew has learned: leadership and members, goals and activities, and how they stand with the crew. For the
corps, use the public `pub` and `share` fields, never the ledger.

#### Locations (`chronicle/wiki/locations/`)

For significant places visited or mentioned: description and notable features, who or what is there, and what happened
there.

**Location image.** Each location page opens with a summary paragraph right after its H1. When you create a location
page, or when `chronicle/images/locations/<slug>.png` doesn't exist yet, generate an image from that summary:

```bash
set -a; source .env; set +a
python3 bin/gen-image.py \
  --prompt "<the location's summary paragraph>" \
  --out chronicle/images/locations/<slug>.png \
  --size 1536x1024
```

Don't embed it. The Chronicle shows `chronicle/images/locations/<slug>.png` at the top of the page by itself. Skip
generation if the file exists, to avoid the cost. If the generator exits non-zero, report the error and continue without
the image.

---

### 2. Session Summary

Create `chronicle/sessions/<DATE>.md`, named by the date played (for example, `2026-11-04.md`):

```markdown
---
title: "A Debt Worth Keeping"
number: 0
played_on: 2026-11-04
---

# A Debt Worth Keeping

*Session zero · 2026-11-04*
```

#### Title

Give the session a short **thematic title**, with no "Session One" style prefix. `number` is the session's number, and
Session 0 is `0`. If a notes file opens with a `Title:` line, that line is the title. If the session folder holds a title
card, the lettering on the card wins over everything else.

#### Title card

If `sessions-raw/<DATE>/title.{png,jpg,jpeg,webp}` exists, copy it to `chronicle/images/sessions/<DATE>-title.png`. The
Chronicle shows it above the heading. Never generate a title card when there isn't one. It's a different image from the
dramatized scene at `chronicle/images/sessions/<DATE>.png`.

#### Subtitle line

Directly under the H1, put an italic line: `*Session 3 · <DATE>*`, with the in-world date if the notes give one. Session
zero uses `*Session zero · <DATE>*`.

Then structure the body as follows.

#### Overview
A 2-3 sentence summary of what happened.

#### Key Events
Detailed bullets: major decisions and their consequences, combat and its outcomes, discoveries, and significant character
moments.

#### Memorable Moments
Creative problem-solving, great roleplay, bravery, and funny, unexpected or cinematic moments.

#### Open Threads
Unresolved plot points and mysteries, promises made, and Debts owed. Only what the crew knows about.

#### Dramatized Scene (Interactive)

This runs for **every** session.

After the summary, **suggest exactly 3 scenes** that could be dramatized, each with a short title and a one-sentence
description. **Wait for the user to choose one.** Then write a **3-paragraph dramatization** with sensory detail,
character voice and tension.

**Generate a scene image** from the chosen scene: the setting, the characters present and the mood. Use each PC's sheet
or wiki page for appearance.

```bash
set -a; source .env; set +a
python3 bin/gen-image.py \
  --prompt "<vivid one-paragraph description of the chosen scene>" \
  --out chronicle/images/sessions/<DATE>.png \
  --size 1536x1024
```

The script adds the campaign's art style. If it exits non-zero, report the error and keep the prose only. Don't embed a
missing image.

Add the scene as the **last** section of the file, image first:

```markdown
## The Scene

![<scene title>](../images/sessions/<DATE>.png)

<the 3-paragraph dramatization>
```

### Session zero

Session zero is mostly the table inventing the crew, so its page differs. `/mnt/w/projects/skunkwerx/dead_ridge_tn/docs/sessions/2026-08-30.md`
in the sibling project is a good model. Cover the crew as they were introduced, the ship and its two names, who owes
what to whom in the terms the table said aloud, and the world the players made. Skip the tone dials and the safety
discussion entirely.

---

### 3. Cross-Linking

Use standard relative Markdown links, which `bb chronicle:publish` turns into links inside the app:

- **Session files** link NPC names on first mention: `[Vasko](../wiki/npcs/imre-vasko.md)`.
- **Wiki pages** link each other: `[the Concord](../locations/the-concord.md)`.

**Do not link PCs.** They appear too often. Only link to pages that exist.

---

## Input Sources

### Previous Sessions and Wiki

`chronicle/sessions/` and `chronicle/wiki/`: read them for continuity.

### Roster

`roster.json`: the players, their characters, and the GM. If it's missing, run
`bb chronicle:roster [dev|prod] <campaign-id>` from the repo root.

### Latest Session Materials

In `sessions-raw/<DATE>/`. Find the newest date folder. Nothing in it is mandatory by name:

- `transcript.md`: the diarized transcript. It exists only when the recording worked.
- `manager.json`: the campaign manager's document, written by `bb chronicle:manager`. **It holds GM secrets.** Read only
  these parts: `s0.ship`, the text of `s0.answers`, `s0.collateral`, and `sessions`' `title`, `recap` and `choices`.
  Everything else in it is covered by the privacy rules.
- **Every other `.md` file is source material**: GM notes (`Session Notes.md`, `dm-notes.md`), player notes
  (`*-notes.md`) and handouts. GM notes can hold secrets too, so apply the privacy rules to them.
- `title.{png,jpg,jpeg,webp}`: the optional title card.
- Non-text files (`session.m4a`, `session.deepgram.json`, `speaker-map.json`) are plumbing. Ignore them.

`s0.answers` keys are question ids, and each answer's `pc` is a member's `user-id` from `roster.json`.

At least one text source must exist. If the folder has neither a transcript nor notes, stop and say so.

### When there is no transcript

A failed recording is normal. The GM's written notes become the **authoritative record**. Produce the same page
structure and voice, so a reader can't tell which kind they're reading.

- **Don't invent verbatim dialogue.** Never attribute a quoted line to anyone unless the notes contain it.
- **Don't fill gaps with invention.** Where the notes are deliberately open, write the page ambiguous too and put it
  under *Open Threads*.

The **Dramatized Scene** is the one place where invention is expected: it's a dramatization, not a record.

### Participants

`roster.json` lists the GM and the players. The GM isn't a player, but their voice appears in the transcript, as they
voice all NPCs and narration. In-person recordings on a single room mic can't be reliably diarized per player, so check
`README.md` before trusting a `speaker-map.json`.

---

## Setting Context

Corporate frontier sci-fi, with touchstones *Firefly*, *The Expanse*, *Alien*, *Deadwood*, *Outland* and the Android:
Netrunner card art. Public background is in the `pub` fields of `../../content/manifest-destiny/manager.json` and the
read-aloud in `run.readAloud`. The gate-builder mystery is a slow burn in the background, not the weekly plot.
