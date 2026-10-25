# Character creation (Genesys Core Rulebook)

This summary paraphrases the rules. Page numbers are CRB printed pages, and each rule has a matching test in
`ttg.genesys.rules` (M2). The **Builder** column shows what the v1 prototype (`prototypes/src/character-builder.js`) does today.

## The seven steps (p.33–34)
1. Background
2. Archetype or species
3. Career
4. Spend XP
5. Derived attributes
6. Motivation
7. Gear, appearance and personality

| Rule | Page | Builder v1 |
|---|---|---|
| The archetype sets the starting characteristics, the base wound and strain thresholds, and the starting XP | 35–39 | matches (sample values; the real ones are now in `content/crb/archetypes.edn`) |
| A career has 8 career skills. Pick 4 of them to get one free rank each, before spending XP | 40 | matches |
| Raising a characteristic costs 10 × the new rating, bought one step at a time (3→5 costs 40 + 50) | 44 | matches |
| Characteristics max out at 5, and can only be bought with XP during creation | 44 | matches |
| A career skill rank costs 5 × the new rank. A non-career rank costs that plus 5 | 44 | matches |
| No skill can be above rank 2 when creation ends, unless a rule says otherwise | 44 | matches |
| A talent costs 5 × its tier | 45 | matches |
| Talent pyramid: after buying a talent, there must be more talents in the tier just below it | 45, 72 | matches |
| Unspent XP is kept and can be spent at the end of any session | 44 | matches (it shows as a warning only) |
| Optional for experienced characters: +50 XP after creation, skills and talents only, skills capped at rank 3 | 44 | **missing**. Could be a GM campaign setting |
| Wound threshold = archetype base + Brawn **after** spending XP. Later Brawn raises don't change it, but talents do | 45 | matches |
| Strain threshold = archetype base + Willpower after spending XP, with the same rule for later raises | 45 | matches |
| Motivations come in four types: Desire, Fear, Strength, Flaw. Each type has a d100 table | 46–50 | **suggestions are invented**. Switch to `content/crb/motivations.edn` and add a roll button |
| Starting gear: 500 currency by default, and the GM can change it. You keep what's left | 51 | matches (500 credits) |
| After buying gear, roll d100 and add it as pocket money. It can't be spent on starting gear | 51 | **missing** |

## House-rule changes at this table
- **Secrets and Obligations** replace Debt and Favors (`docs/house-rules.md`). The builder's Debt step becomes a
  "Secret or Obligation" step: pick or roll on `content/house/obligation-types.edn`, then set the magnitude. Its mechanics are still
  waiting on the table's confirmation (ttg-m2w).
