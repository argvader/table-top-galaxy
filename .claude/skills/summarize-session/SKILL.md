---
name: summarize-session
description: >-
  Turn the latest recorded Manifest Destiny session into Chronicle content. Use when the
  user says "summarize latest session", "summarize the session", "run the session
  summarizer", or similar. Reads the newest sessions-raw/<DATE>/ and follows the
  SESSION_SUMMARIZER.md prompt to write the session summary, wiki updates, and scene and
  location images, keeping GM secrets out of what players read.
---

# Summarize the latest session

All paths here are relative to `campaigns/manifest-destiny/`, so work from that folder.

Run the campaign's session summarizer over the newest raw session. Everything it writes is read by all the players.

## Steps

1. **Load API keys**, needed for scene and location images:

   ```bash
   set -a; source .env; set +a
   ```

2. **Find the newest session**: the most recent folder under `sessions-raw/<DATE>/`. It holds a `transcript.md` or
   written notes, or both, plus any handouts and an optional title card.

3. **Check the inputs.** `roster.json` should exist, and for Session 0 so should `sessions-raw/<DATE>/manager.json`. If
   either is missing, tell the user the command from `README.md` rather than carrying on without it.

4. **Read `SESSION_SUMMARIZER.md` and follow it exactly.** It's the single source of truth. Its privacy and canon rules
   come first and override everything else. It will:
   - update wiki pages under `chronicle/wiki/{pcs,npcs,factions,locations}/`, generating an image for each new location,
   - write `chronicle/sessions/<DATE>.md`, copying any title card to `chronicle/images/sessions/<DATE>-title.png`,
   - offer three scenes, then add the chosen one with a generated image under `## The Scene`.

5. **Check for secrets before you finish.** Reread what you wrote against the privacy list, and tell the user anything
   you held back so they can decide.

6. Tell the user they can publish with the **publish-chronicle** skill.

> This skill starts from whatever text is already in `sessions-raw/<DATE>/`. When the recording failed, notes alone are
> enough. To turn a `session.deepgram.json` into a transcript, use **build-speaker-mapping** and **translate-deepgram**
> first.
