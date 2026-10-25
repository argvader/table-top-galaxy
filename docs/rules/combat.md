# Combat encounters (Genesys Core Rulebook, Chapter 6)

This summary paraphrases the rules. Page numbers are CRB printed pages. The spending tables are in `content/crb/spend-results.edn`.
Shadow of the Beanstalk splits the skills into Ranged (Light), Ranged (Heavy), Melee, Brawl and Gunnery (SotB p.42). Wherever
the CRB says "Ranged", that means either Ranged skill.

## Structure (p.95–96)
1. **Initiative:** everyone makes a Simple Cool check if they're ready, or a Simple Vigilance check if they're surprised. When in doubt, use Vigilance.
   Rank the results by successes, then by advantage. A PC wins a tie with an NPC.
2. **Slots:** each result becomes a PC slot or an NPC slot. Any PC who hasn't acted yet can fill a PC slot, so the players choose who goes.
3. **Turns** happen in slot order. The same order repeats every round.
4. **End of round:** effects that last "until the end of the round" stop.
5. **End of encounter:**
   - "Once per encounter" abilities come back.
   - Strain recovery happens (see [criticals-and-health.md](criticals-and-health.md)).

## The turn (p.96–101)

| Part | Limit | Notes |
|---|---|---|
| Incidentals | No set limit (GM's call) | Talk, drop an item, peek. Off your turn, incidentals are usually all you can do |
| Maneuvers | 1 free, **2 at most** | Get a 2nd by suffering 2 strain, spending 2 advantage, or giving up your action. Maneuvers granted off-turn don't count toward the 2 |
| Action | 1 | A skill check, a combat check, activating an ability, or swapping it for a maneuver |

**Maneuvers (p.98–100):**
- **Aim:** add a Boost die to your next combat check, or two Boost dice for two aim maneuvers in a row. Alternatively, aim at a specific part or item, which adds two Setback dice, or one Setback die after two aims.
  Aim is lost if you move, act, or take damage past your soak.
- **Assist:** an engaged ally adds a Boost die to their next check.
- **Guarded stance:** you get melee defense 1 until the end of your next turn, but add a Setback die to your own combat checks.
- **Interact:** open a door, move a big item, or **take cover** (ranged defense 1).
- **Manage gear:** draw, holster, reload, or get something out of a pack.
- **Mount or dismount:** includes getting into a vehicle or a gunnery station.
- **Move:**
  - Short ↔ medium takes 1 maneuver.
  - Medium ↔ long takes 2.
  - Long ↔ extreme takes 2.
  - Engaging or disengaging takes 1, and so does moving within short range.
- **Drop prone or stand up:** while you're prone, ranged attacks against you add a Setback die and melee attacks against you add a Boost die.
- **Preparation:** needed by some talents.

## The combat check (p.101–103)
1. **Declare the attack:** pick the skill, the weapon and the target.
2. **Build the pool:**
   - Melee and Brawl are always **Average**.
   - Ranged difficulty depends on range: engaged Easy (plus Table I.6–4), short Easy, medium Average, long Hard, extreme Daunting.
3. **Hit and damage:** on a hit, damage is the weapon's damage plus 1 per net success. That bonus applies to every target.
4. **Advantage and triumph:**
   - Weapon Crit rating in advantage inflicts a Critical Injury, but only on a hit that deals damage past soak.
   - Usually 2 advantage activates a quality.
   - 1 triumph does either one, whatever the cost.
   - If an attack triggers the crit more than once, roll **one** crit and add +10 for each extra trigger.
5. **Threat and despair:** the GM spends these (Table I.6–3).
6. **Soak:** subtract soak from **each hit** separately. What's left becomes wounds, and zero or less means no wounds.

**Spending in combat (Table I.6–2 and I.6–3, p.104):** these are in `spend-results.edn`, and the sheet already prices them.
Highlights:

| Cost | Advantage or triumph | Threat or despair |
|---|---|---|
| 1 | Recover 1 strain. Add a Boost die to the next ally's check. Crit or quality | Suffer 1 strain. Lose the benefit of a prior maneuver |
| 2 | A free maneuver (still max 2). Add a Setback die to the target's next check | An enemy gets a free maneuver. Add a Setback die to your next action |
| 3 | Negate the target's defense this round. Disable instead of damaging. +1 defense. Disarm | Fall prone. Hand the enemy a big edge |
| Triumph or despair | Upgrade an ally's next check, or upgrade the difficulty of the target's next check. Do something vital | Out of ammo for the rest of the encounter. The weapon is damaged |
| 2 triumph | Destroy the target's gear | |

## Defense and soak (p.104–105)
- Each point of defense adds a **Setback die** to attacks against you. Defense can't go above **4**.
- Defense can be **general**, **melee** or **ranged**.
- **Sources that provide defense don't stack**, so take the best one. That includes armor, cover, guarded stance and some talents.
  Sources marked **"+N" or "increase"** stack with everything, up to the cap of 4.
- **Soak** is Brawn plus armor. Soak from **different** kinds of source stacks (armor plus cyberware). The same source never stacks with itself.
  Soak doesn't reduce strain, except against Stun Damage.
- **Only one worn armor counts** (p.92).
- **App gaps (sheet prototype):** it adds up the soak and defense of every worn armor, and it doesn't cap defense at 4. M2 rules should do both correctly.

## Modifiers (p.107–110)

| Situation | Effect |
|---|---|
| Ranged attack at a target engaged with your ally | Upgrade the difficulty once. If the attack hits and rolls a despair, it hits someone else in that engagement (GM picks) |
| Ranged attack while you're engaged | Your opponent gets a Boost die on their next melee attack against you. Ranged (Light) adds +1 difficulty, Ranged (Heavy) adds +2, and Gunnery can't be used. The book lists these by weapon (one-handed, two-handed, heavy) and lets settings map them by skill. The mapping to skills is ours |
| Attacking a prone target | Melee gets a Boost die. Ranged adds a Setback die. Melee attacks *from* prone add a Setback die |
| Two weapons | Use your lower skill and lower characteristic of the two, and the harder difficulty +1. A hit uses the primary weapon. Spend 2 advantage or a triumph to hit with the second weapon too |
| Unarmed (Brawl) | Damage = Brawn, Crit 5, engaged range, Knockdown. You can choose to deal strain instead of wounds (soak still applies). Brawl weapons add to this |
| Improvised weapons | Small +1, medium +2, large +3 damage, all Crit 5. The weapon has 1 automatic threat. It breaks on a despair or 2 threat |
| Silhouette | If the target is 2+ bigger than you, decrease the difficulty by 1. If it's 2+ smaller, increase it by 1. People are silhouette 1 |
| Concealment | +1, +2 or +3 Setback dice on ranged, Perception and Vigilance checks against the target, and the same number of Boost dice on Stealth |
| Cover | Ranged defense 1 and a Setback die on Perception and similar checks. Prepared positions give more |
| Difficult terrain | Moving costs twice the maneuvers. Water counts as difficult terrain. Impassable terrain takes a check and at least an action |
| Gravity | Heavy gravity adds up to 3 Setback dice to Brawn checks (but not Resilience) and to Coordination. Light gravity adds Boost dice the same way. In zero-g all movement is difficult terrain |

## Hazards (p.111–112)
- **Fire, acid and corrosive atmosphere:**
  - Each turn the character suffers wounds equal to the hazard's rating (1–10+).
  - Putting out a fire takes Coordination: Average on hard ground, Easy on soft ground.
- **Holding breath:** a character can hold their breath for Brawn rounds. After that they are suffocating, and suffer 3 strain each turn.
  Once strain passes their threshold, they suffer a crit every turn.
- **Vacuum:** suffocation plus 3 wounds a round. Once wounds pass the threshold, they suffer a crit every round.
- **Falling:**

  | Range | Damage | Strain |
  |---|---|---|
  | Short | 10 | 10 |
  | Medium | 30 | 20 |
  | Long | Wound threshold +1, crit at +50 | 30 |
  | Extreme | Wound threshold +1, crit at +75 (or death) | 40 |

  Soak reduces falling damage but not falling strain. An Average Athletics or Coordination check helps:
  - Each success removes 1 damage.
  - Each advantage removes 1 strain.
  - A triumph can shorten the fall by one range band.

## Ammo (p.89)
Don't count bullets. A despair can empty the weapon. Only weapons with **Limited Ammo** are tracked.
