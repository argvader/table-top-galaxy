---
name: publish-chronicle
description: >-
  Publish the Manifest Destiny Chronicle into the campaign in Table Top Galaxy, so its members can read new sessions and
  wiki pages. Use when the user says "publish chronicle", "publish the session", or similar.
---

# Publish the Chronicle

Publishing makes pages readable by every member of the campaign. Git isn't involved, and this skill never commits or
pushes.

## Steps

1. **Check the tool exists.** Run `bb tasks | grep chronicle:publish` from the repo root. If it isn't listed, the
   Chronicle hasn't been built yet: stop and tell the user. The pages stay safely in `campaigns/manifest-destiny/chronicle/`.

2. **Preview.** From the repo root, run `bb chronicle:publish [dev|prod] <campaign-id>`. It prints what will change and
   asks before writing. Show the user the list.

3. **Publish** once the user has seen it, and report what went live.

To take a page down: `bb chronicle:publish [dev|prod] <campaign-id> --unpublish <slug>`.
