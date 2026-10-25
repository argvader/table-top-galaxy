# Hacking and the Network (Shadow of the Beanstalk, Chapter 3)

This summary paraphrases the rules. Page numbers are SotB pages. The data is in `content/sotb/network.edn`: 8 actions and
maneuvers, 16 ice, 12 icebreakers and 2 rules sections. The spending and trace tables are in `content/sotb/network-results.edn`.

## Structure (p.125–128)
- Network encounters use the normal structured turns and Initiative (CRB p.95–96). If a run happens during a fight,
  everyone shares one Initiative order.
- The **runner** breaks in. A **sysop** defends. A **system** has a difficulty to access (Table 3–1, p.126), which runs
  from Simple (a ticket dispenser) to Formidable (Beanstalk control).
- A system holds **sub-systems** such as cameras, doors and stored footage. A sub-system may sit behind one or more pieces of **ice**.
- **Attempt access** is an incidental. It "moves" the runner to a sub-system. If ice protects it, the runner meets that ice now.
  Sysops and other permitted users never meet ice.

## Actions and maneuvers (p.129–132)

| Name | Type | Who | Check |
|---|---|---|---|
| Access System | action | runner | Computers (Hacking) against the system's difficulty |
| Break Ice | action | runner | Average Computers (Hacking). Strength = successes + the active icebreaker's strength. Must **exceed** the ice's strength |
| Burn System | action | runner, sysop | Daunting Computers (Hacking). The system is damaged one step, plus one more for every 2 extra successes (CRB p.89) |
| Trace User | action | sysop | Opposed Computers (Sysops) vs (Hacking). On a win, +1 trace (see the trace table) |
| Lockout | action | sysop | Formidable Computers (Sysops), one step easier per trace, down to Easy. Boots the runner |
| Sweep | action | sysop | Hard Computers (Sysops). Finds one intruder, plus one more for each extra success |
| Enact Command | maneuver | runner, sysop | No check. Does one thing in an accessed (sub-)system |
| Activate Program | maneuver | runner, sysop | Turns an icebreaker or ice on or off. Only one icebreaker can be active at a time, unless a talent says otherwise (e.g. Codeslinger) |

- If a break fails, the ice's own **Effects** apply. Barriers block the sub-system, code gates boot the runner, and sentries alert the sysop.
- A broken ice stays down until the end of the runner's next turn. Spending results can keep it down longer (p.130).
- An icebreaker only works on the ice types listed in its "Able to Affect".

## Spending results and traces (p.130–131)
- `network-results` holds both tables. **Spend**: 22 options keyed by their cost in symbols, some marked Runner Only or
  Sysop Only. **Trace**: 1 trace finds the country, 2 the region, 3 the city and 4 the exact address.

## Builder and sheet
- Runners need Computers (Hacking) and the Network gear (cyberdeck, skulljack) from `content/sotb/gear.edn`.
  Icebreakers have a rarity and price, so they can be bought as gear.
- Tracked as `ttg-iiz` (hacking support on the character sheet): a Network tab with the active icebreaker, owned
  programs, and a Break Ice roll that adds the icebreaker's strength.
