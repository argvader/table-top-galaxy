# Dice and checks (Genesys Core Rulebook, Chapter 1)

This summary paraphrases the rules. Page numbers are CRB printed pages. These rules belong to `ttg.dice` and the
pool-building part of `ttg.genesys.rules` (M2, `ttg-b9p`). The **App** column shows what the prototypes
(`prototypes/src/shared/dice-core.js` and the sheet) do today.

## The dice (p.8–10)
The face table on p.10 (Table I.1–2) was checked cell by cell against `dice-core.js`, and every face matches.

| Die | Sides | Faces (s success, a advantage, r triumph, f failure, t threat, d despair) |
|---|---|---|
| Boost | d6 | –, –, s, sa, aa, a |
| Ability | d8 | –, s, s, ss, a, a, sa, aa |
| Proficiency | d12 | –, s, s, ss, ss, a, sa, sa, sa, aa, aa, r |
| Setback | d6 | –, –, f, f, t, t |
| Difficulty | d8 | –, f, ff, t, t, t, tt, ft |
| Challenge | d12 | –, f, f, ff, ff, t, t, ft, ft, tt, tt, d |

- **d10 and d100 (p.11):** a 0 on a d10 counts as 10. For a d100 roll, two d10s give the tens and ones digits, and 00 means 100.

## Building a pool

| Rule | Page | App |
|---|---|---|
| The higher of skill ranks and characteristic gives that many Ability dice. Then upgrade Ability dice to Proficiency equal to the lower value. Unskilled means nothing gets upgraded | 19 | Sheet builds this |
| The GM sets the difficulty: Simple 0, Easy 1, Average 2, Hard 3, Daunting 4, Formidable 5 Difficulty dice | 16–18 | Roller has presets |
| An **Impossible** task costs a Story Point just to attempt, with no other Story Point allowed. It rolls as Formidable | 18 | Not yet |
| Boost and Setback dice are added per circumstance. They do **not** cancel each other: both go in the pool | 20–21 | Manual |
| **Order of modifications:** basic pool, then add dice, then upgrade, then downgrade, then remove | 20, 23 | Not enforced |
| **Upgrade** turns Ability into Proficiency, or Difficulty into Challenge. If none are left to upgrade, add a new Ability (or Difficulty) die, then upgrade that one with the next upgrade | 22 | Story point upgrade only |
| **Downgrade** turns Proficiency into Ability, or Challenge into Difficulty. If none are left to downgrade, the extra downgrades are ignored | 22 | Not yet |
| **Remove** comes last. Removing more dice of a type than the pool has just removes all of them | 23 | Not yet |
| **Increasing** difficulty (adding Difficulty dice) is not the same as **upgrading** it | 22 | |

## Reading the results (p.23–25)
- Each failure cancels one success, and the check succeeds with **1 or more net successes**. A triumph also counts as a success, and a despair also counts as a failure.
- Each threat cancels one advantage. Whatever is left over is the side effect, whether the check succeeded or failed.
- Triumph and despair **never cancel each other**. Each one's special effect stays even when its success or failure part is cancelled.
- Extra net failures don't make a failure worse. Extra net successes do make a success bigger.
- **Rounding:** whenever a rule asks for a fraction, round up.
- The player who rolled spends advantage and triumph. The GM spends threat and despair.

## Other kinds of check

| Check | Page | Rule |
|---|---|---|
| Opposed | 25–26 | Difficulty comes from the opponent. The higher of their opposing skill and its characteristic gives Difficulty dice, and the lower value upgrades that many to Challenge. Written "opposed X vs Y" |
| Competitive | 26 | Everyone rolls against the same difficulty. Most net successes wins, then most triumphs, then most advantage. Otherwise it's a draw. If nobody succeeds, nobody wins |
| Skilled assistance | 26 | The pool can use one character's characteristic and the other character's skill |
| Unskilled assistance | 27 | The GM adds a Boost die. If several people help, only one gives their rating and each of the others adds a Boost die |
| Assist maneuver | 27, 98 | In an encounter, an engaged ally adds a Boost die to the helped character's next check. It expires after that character's next turn |

## Story Points (p.27–30)
- **Setup:** each session the player pool gets one point per player, and the GM pool starts with **1**.
- **Flow:** a spent player point goes to the GM pool, and a spent GM point goes to the player pool. The point moves **after** the action resolves, so it can't be spent again in the same action.
- **Limit:** one point per side per check. The roller decides first, and then the other side may answer.
- **Uses:**
  - Upgrade your own pool's ability once.
  - Upgrade the difficulty of the other side's check once.
  - Fuel a talent.
  - Add a story fact (GM approval).
- **App:** the sheet flips points between the pools and supports the upgrade use. Still missing: the one-per-check limit, and the delayed transfer.

## Characteristics, XP and derived attributes (p.13–15, 30–31)
- Characteristics run from 1 to 5, and 2 is average. They can't go above 5 (a soft cap for NPCs).
- After creation, raising Brawn or Willpower does **not** raise the wound or strain threshold. Raising Brawn **does** raise soak.
- XP costs and creation limits are covered in [character-creation.md](character-creation.md).
- The guideline is about 20 XP per session (p.30).
- Defense starts at 0 for both melee and ranged. A single "Defense" value applies to both.
