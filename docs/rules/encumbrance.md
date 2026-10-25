# Encumbrance (Genesys Core Rulebook, p.84–85, 92)

This summary paraphrases the rules. Page numbers are CRB printed pages. Item encumbrance values are in `content/*/weapons.edn`,
`armor.edn` and `gear.edn`. The **App** column shows what the sheet prototype does today.

| Rule | Page | App |
|---|---|---|
| Typical values: small items 1–2, medium 3–4, large 5–6. Ten loose small items count as 1, and twenty stowed small items count as 1 | 84 | Item values only |
| A carried person counts as 5 + their Brawn (7 for a typical human) | 84 | Not yet |
| **Threshold** is 5 + Brawn | 85 | Yes |
| **Worn armor** counts **3 less** (never below 0). Armor that's carried counts in full | 92 | **No.** The sheet counts worn armor in full |
| **Encumbered** (over the threshold): add **1 Setback die for each point over** to all Agility and Brawn checks | 85 | Shows the bar only |
| Over the threshold by **Brawn or more**: no free maneuver. You can still take 2 maneuvers, but **each costs 2 strain** | 85 | Not yet |
| **Lifting** something heavier than your threshold takes Athletics. Difficulty is Easy at 1 over, +1 for each further point, up to Daunting at 4 over | 85 | n/a |
| **Helpers** add their raw Brawn to the threshold for that one lift or carry | 85 | n/a |
| **Concealing:** items of 1 or less hide without a check. A searcher rolls opposed Perception vs Stealth, or Vigilance vs Stealth if they're only watching. Add a Boost die for each point the biggest hidden item has over 1 | 85 | Not yet |
| Gravity doesn't change encumbrance | 111 | n/a |

## For M2 (`ttg.genesys.rules`)
- `encumbrance` should return all of these:
  - the total, using worn armor at −3
  - the threshold
  - the number of points over
  - the Setback dice to add to Agility and Brawn pools
  - whether the free maneuver is lost
- The sheet's skill roller should add those Setback dice automatically, and show the reason ("Encumbered +2").
