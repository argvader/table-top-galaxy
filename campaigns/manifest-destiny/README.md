# Manifest Destiny: session pipeline

Recording to published Chronicle, run locally with Claude skills. The skills live in the repo's `.claude/skills/`, and
every path below is relative to this folder.

```
record → sessions-raw/<DATE>/ → translate-deepgram → build-speaker-mapping → translate-deepgram
       → summarize-session → chronicle/ → publish-chronicle
```

If the recording fails, drop written notes into `sessions-raw/<DATE>/` and go straight to **summarize-session**.

## Setup, once

```bash
cp .env.example .env                  # then copy the keys from another campaign's .env
pip install -r requirements-dev.txt
```

Keep `DEEPGRAM_PREFIX=manifest-destiny`. This campaign uses the same `ttrpg-deepgram-bot` user and shared bucket as the
others, under its own prefix, so it needs no AWS changes. See `DEEPGRAM_AWS.md`.

Then, from the repo root:

```bash
bb chronicle:roster dev <campaign-id>
```

The campaign id is in the campaign page's address. Run it again whenever someone joins or builds a character.

## 1. Record the audio

**Remote, with OBS:** capture Mic/Aux and Desktop Audio, and start recording before play. Multi-track `.mkv` gives cleaner
speaker separation.

**In person:** one room mic can't be reliably split per player. Put the recorder in the middle of the table, and expect to
correct the speaker map by hand or rely on the GM's notes for who said what.

Deepgram takes common audio and video formats, up to 2 GB. Strip the video from a large recording first:

```bash
ffmpeg -i session.mp4 -vn -acodec copy session.m4a
# If the codec errors, re-encode. 16 kHz mono is plenty for speech:
ffmpeg -i session.mp4 -vn -ac 1 -ar 16000 -c:a flac session.flac
```

## 2. Transcribe

Put the audio in `sessions-raw/<DATE>/`, then run **translate-deepgram**. It stops once `session.deepgram.json` exists.

The default is `--diarize-model v1`. Use `--diarize-model latest` only if `v1` is visibly mis-splitting speakers.

## 3. Map the speakers

Run **build-speaker-mapping**, check the result yourself, then run **translate-deepgram** again to write `transcript.md`.
Deepgram's speaker numbers change with every recording, so a map from one session doesn't apply to the next.

## 4. Summarize

From the repo root, copy the session's manager document in, then run **summarize-session**:

```bash
bb chronicle:manager dev <campaign-id>
```

`manager.json` holds GM secrets and is git-ignored along with everything else in the dated folders. The summarizer
follows `SESSION_SUMMARIZER.md`, which lists what may and may not be published.

## 5. Publish

Run **publish-chronicle**. Until `bb chronicle:publish` exists, that skill stops and says so.
