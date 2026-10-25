# Wounds, strain, critical injuries and healing (Genesys Core Rulebook, p.112–117)

This summary paraphrases the rules. Page numbers are CRB printed pages. The crit table (Table I.6–10, p.115) is in
`content/crb/crits.edn`, with a d100 range and a severity for each entry. The **App** column shows what the sheet prototype does today.

## Wounds and strain

| Rule | Page | App |
|---|---|---|
| Each point of damage after soak is 1 wound | 112 | Yes |
| **Wounds over the threshold:** the PC is knocked out and suffers **one crit** right away. They stay out until wounds are healed back below the threshold | 112–113 | Shows alert and a crit button |
| Track wounds past the threshold up to **twice the threshold** | 113 | Not yet |
| NPCs are defeated when wounds pass their threshold, unless they're a nemesis. Non-nemesis NPCs take strain as wounds | 112–113 | n/a (GM side) |
| **Strain over the threshold:** the character is incapacitated until strain is back down to the threshold | 113 | Shows alert |
| You can choose to suffer 2 strain to get a second maneuver | 113 | Not yet |
| **States of health:** unwounded; wounded (below the threshold); critically injured (any crit); incapacitated | 113 | Partly |

## Critical injuries (p.114–116)
- To take a crit, roll d100 on Table I.6–10 and add **+10 for each crit the character already has**.
  Crits stay until they are healed, even after their effect has worn off.
- **Death:** a total of **151 or more** kills the character, and 141 can kill. A destroyed android or robot can't be brought back.
- Strain damage (Stun Damage) can still cause crits. A minion hit by one is just knocked out.
  Either side can decline a crit that makes no sense in the story.
- **App:** `rollCrit` already adds +10 per active crit. It doesn't yet handle multi-trigger +10s, weapon modifiers like Vicious, or the death check.

## Status effects (p.114–116)

| Status | Effect | Default duration |
|---|---|---|
| Staggered | No actions, not even downgraded to maneuvers | To the end of the character's next turn |
| Immobilized | No maneuvers, including bought ones | To the end of the character's next turn |
| Disoriented | Add a Setback die to every check | To the end of the character's next turn |

If the same status hits again, the durations add together, but never past the end of the encounter.

## Healing (p.116–117)
- **Natural rest:**
  - Each full night heals 1 wound.
  - After each full week, roll Resilience against one crit, with difficulty equal to its severity.
    Success heals the crit, and a triumph heals one more. Failure still heals 1 wound.
- **Medicine check (first aid):** each target can get one per encounter. Difficulty depends on the target's wounds:

  | Target's wounds | Difficulty |
  |---|---|
  | Half the threshold or less | Easy |
  | More than half | Average |
  | Over the threshold | Hard |
  | Treating a crit | Difficulty = the crit's severity. One try per crit per week |

  - Each success heals 1 wound, and each advantage heals 1 strain.
  - Treating yourself adds **+2 difficulty**. Working without medical gear adds **+1**.
- **Painkillers:** in Android, these are slap-patches (SotB p.100).
  - Using one takes a maneuver while engaged with the patient, or on yourself if you have a free hand.
  - The first heals 5 wounds, then 4, 3, 2, 1, and the sixth does nothing.
  - Painkillers never heal crits.
  - The book doesn't say when the count resets. The sheet resets it per day. That's our call, and it's already in the sheet.
- **Strain recovery:**
  - At the end of an encounter, roll a Simple Discipline or Cool check. Each success recovers 1 strain.
  - A night's rest clears all strain.
  - **App:** not yet a button. It goes with the end-of-encounter flow on the GM dashboard (`ttg-ej0.4`).

## House rule interaction
- When a Secret or Obligation comes due, it lowers the strain threshold for the session: −2 for that character and −1 for everyone else
  (see [../house-rules.md](../house-rules.md)).
- The thresholds in this file are the ones **after** that session change.
