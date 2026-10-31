# Raw Session Materials

Drop the inputs for each session into a dated folder here. The summarizer reads the **newest** date folder as the latest
session. Everything in these folders is git-ignored.

```
sessions-raw/
  2026-11-04/
    session.m4a             # the recording
    session.deepgram.json   # Deepgram's result
    speaker-map.json        # Deepgram speaker number -> person, for this recording
    transcript.md           # written by translate-deepgram
    manager.json            # written by bb chronicle:manager. Holds GM secrets
    Session Notes.md        # the GM's written account (optional)
    <player>-notes.md       # a player's notes (optional)
    *-handout.md            # handouts given at the table (optional)
    title.png               # title card (optional)
```

**Every `.md` except `transcript.md` is treated as notes**, so filenames don't matter. At least one text source
(transcript or notes) must be present. If the recording failed, written notes alone are enough.

`speaker-map.json` is kept per session because Deepgram's numbering changes with every recording.

These files are inputs only. Nothing here is published.
