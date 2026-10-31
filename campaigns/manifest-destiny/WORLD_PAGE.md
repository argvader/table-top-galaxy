# World Page Builder — Manifest Destiny

## Task Description

This prompt builds the **home page** of the campaign's Chronicle at `chronicle/wiki/world/home.md`: an evocative
introduction to the setting, with a hero image. Every member of the campaign reads it.

Run it when you want to refresh the page. It's separate from `SESSION_SUMMARIZER.md`, which handles sessions and the wiki.

All paths are relative to `campaigns/manifest-destiny/`.

---

## Input Sources

Use **only** these. The campaign's other prep is full of GM secrets, and the privacy and canon rules at the top of
`SESSION_SUMMARIZER.md` apply here too.

From `../../content/manifest-destiny/manager.json`:

- `run.readAloud` and `run.touchstones`.
- `pub` on the first act (`acts[0]`). Later acts are spoilers.
- `pub` and `share` on `corps`, and `pub` on `powers`.

Also `chronicle/wiki/` for cross-links. Link only to pages that exist.

**Don't read `content/Manifest Destiny — Campaign Arc.md`.**

---

## Output Required

Write `chronicle/wiki/world/home.md`:

1. **Front matter:**
   ```yaml
   ---
   title: "Manifest Destiny"
   ---
   ```
2. **H1:** `# Manifest Destiny`. The Chronicle shows the hero image above it, so don't embed it.
3. **Introduction:** 2-3 short paragraphs welcoming the reader aboard the *Concord*.
4. **Curated sections:** a few `##` sections of 1-2 tight paragraphs each: the gate and the *Meridian*, the corps that
   split the ship, and the town. Link a location, faction or NPC to its wiki page on first mention.

Keep it a landing page, not a copy of the prep.

---

## Hero Image

```bash
set -a; source .env; set +a
python3 bin/gen-image.py \
  --prompt "<a sweeping establishing view of the Concord on its way to the gate beyond Neptune>" \
  --out chronicle/images/world/home.png \
  --size 1536x1024
```

The script adds the campaign's art style. If it exits non-zero, report the error and stop.

---

## Notes

Change only `chronicle/wiki/world/home.md` and `chronicle/images/world/home.png`.
