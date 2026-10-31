---
name: build-world
description: >-
  Rebuild the Manifest Destiny Chronicle home page (chronicle/wiki/world/home.md). Use when the user says "build world
  data", "build the world page", "rebuild the home page", or similar. Follows the WORLD_PAGE.md prompt, using only the
  campaign's public text, to regenerate the page and its hero image.
---

# Build the world page

All paths here are relative to `campaigns/manifest-destiny/`, so work from that folder.

## Steps

1. **Load API keys**, needed for the hero image:

   ```bash
   set -a; source .env; set +a
   ```

2. **Read `WORLD_PAGE.md` and follow it exactly.** It lists the only sources you may use, because the rest of the
   campaign's prep is full of GM secrets. It writes `chronicle/wiki/world/home.md` and generates
   `chronicle/images/world/home.png`.

3. Tell the user they can publish with the **publish-chronicle** skill.
