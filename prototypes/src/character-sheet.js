(() => {
/*DICE_CORE*/
/*DICE_SVG*/
/*CONTENT*/
  // ---------- Library (injected by bb proto-build from content/, Android setting) ----------
  const KEY_OF = { brawn: "Br", agility: "Ag", intellect: "Int", cunning: "Cun", willpower: "Will", presence: "Pr" };
  const BOOK = { crb: "CRB", sotb: "SotB", "sotb-guns": "Gun Locker", epg: "EPG", house: "House rule" };
  const SKILLS = ["General", "Combat", "Social", "Knowledge"].map((g) => [g, CONTENT.skills.filter((x) => x.category === g).map((x) => [x.name, KEY_OF[x.characteristic]])]);
  const STAT_OF = Object.fromEntries(SKILLS.flatMap(([, l]) => l));
  const CHAR_NAMES = { Br: "Brawn", Ag: "Agility", Int: "Intellect", Cun: "Cunning", Will: "Willpower", Pr: "Presence" };
  const find = (type, id) => CONTENT[type].find((e) => e.id === id);
  const cite = (src) => (src ? `<span class="cite">${BOOK[src.book] || src.book}${src.page ? ` p.${src.page}` : ""}</span>` : "");
  const SYM_TOKENS = { success: "s", advantage: "a", threat: "t", failure: "f", triumph: "r", despair: "d" };
  // Book text marks dice and symbols as [ability], [advantage] ...; draw them as the roller's icons.
  const rich = (text) => String(text || "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]))
    .replace(/\[(\w+)\]/g, (m, k) => SYM_TOKENS[k] ? `<span class="glyph" title="${k}">${sym(SYM_TOKENS[k])}</span>`
      : DICE[k] ? `<span class="glyph">${dieSVG(k, null, DICE[k].name)}</span>` : m);
  // Talents whose effect the sheet applies to derived stats.
  const TALENT_MODS = { toughened: { wounds: 2 }, grit: { strain: 1 } };

  // ---------- Sample character: Prance Galavent, built with the builder's rules from the Android library ----------
  // XP: Natural 120 + Obligation magnitude 10 + 15 earned = 145. Spent 130: Agility and Cunning to 3 (60),
  // skills (40: Piloting rank 2, plus Vigilance, Cool, Streetwise as non-career), talents (30: four tier 1, one tier 2).
  const ARCH = find("archetypes", "natural"), CAREER = find("careers", "courier");
  const C = {
    name: "Prance Galavent", initials: "PG", archetype: ARCH, career: CAREER,
    tagline: "Outrider of the Concord", xp: { total: 145, spent: 130 }, credits: 340,
    chars: { Br: 2, Ag: 3, Int: 2, Cun: 3, Will: 2, Pr: 2 },
    ranks: { "Piloting": 2, "Coordination": 1, "Driving": 1, "Stealth": 1, "Athletics": 1, "Ranged (Light)": 1, "Perception": 1,
      "Vigilance": 1, "Cool": 1, "Streetwise": 1 },
    wounds: 4, strain: 3, painkillers: 0,
    talents: [["toughened", 1], ["grit", 1], ["quick-draw", 1], ["rapid-reaction", 1], ["heightened-awareness", 1]]
      .map(([id, rank]) => ({ ...find("talents", id), rank })).filter((t) => t.id),
    weapons: ["delta-arms-enforcer-android", "stun-baton-android"].map((id) => find("weapons", id)).filter(Boolean),
    armor: [{ ...find("armor", "concealed-buckyweave-android"), worn: true }],
    gear: [["slap-patch-android", 2], ["personal-comlink-android", 1], ["portable-toolkit-android", 1], ["flashlight-android", 1]]
      .map(([id, qty]) => ({ ...find("gear", id), qty })).filter((g) => g.id),
    crits: [{ ...find("crits", "minor-nick"), roll: 4, session: "Session 3" }],
    obligation: { type: find("obligation-types", "debt"), magnitude: 10, attachment: "Weyland Consortium",
      why: "Weyland paid for the berth on the Concord, the salvage rig and the scout ship's fuel. Prance wants the hull free of corp names.",
      log: [{ when: "Session 4", text: "Came due. Weyland called in a favor: escort a survey drone to the last outpost past Saturn." }] },
    motivations: [
      ["Desire", "Freedom", "Own the scout ship outright, no corp name on the hull."],
      ["Fear", "Confinement", "Being trapped, in a contract or in a sealed hull."],
      ["Strength", "Courageous", "Gets calmer as things get worse at the stick."],
      ["Flaw", "Recklessness", "Will take the risky run if someone says they can't."],
    ],
    session0: { q: "What did you hear on the comms the night the Meridian went through the gate?",
                a: "A voice they knew, calling their name. They have told no one." },
    notes: "Owes Rook (Haas-Bioroid dock chief) a favor for the hush job on the fuel log.\nThe bioroid MX-7 keeps asking whether the gate counts as 'outside'.\nNBN camera crew: Jules Okafor. Friendly. Probably not.",
    history: [
      { when: "Session 4", text: "+15 XP awarded" },
      { when: "Session 4", text: "Obligation came due: Weyland Consortium" },
      { when: "Session 3", text: "Critical injury: Minor Nick" },
      { when: "Session 3", text: "Bought Heightened Awareness (10 XP)" },
    ],
  };
  const isCareer = (skill) => C.career.skills.includes(skill);
  const CRIT_TABLE = CONTENT.crits.slice().sort((a, b) => a.roll[0] - b.roll[0]);
  const SEV = ["–", "Easy", "Average", "Hard", "Daunting"];
  const LADDER = [
    { name: "Simple", n: 0, range: "" }, { name: "Easy", n: 1, range: "Short" }, { name: "Average", n: 2, range: "Medium" },
    { name: "Hard", n: 3, range: "Long" }, { name: "Daunting", n: 4, range: "Extreme" }, { name: "Formidable", n: 5, range: "" },
  ];
  // Table I.6-2 (CRB p.104): what Advantage and Triumph buy. "Recover 1 strain" applies itself to the sheet.
  const ADV_OPTS = CONTENT["spend-results"].filter((o) => o.side === "positive")
    .sort((a, b) => ((a.cost.advantage || 9) - (b.cost.advantage || 9)) || ((a.cost.triumph || 0) - (b.cost.triumph || 0)))
    .map((o) => ({ cost: o.crit ? "crit" : o.cost.advantage || (o.cost.triumph === 2 ? "rr" : "r"), orTriumph: !!o.cost["or-triumph"],
      label: o.name, attackOnly: o.crit, effect: /^Recover 1 strain/.test(o.name) ? { strain: -1 } : null, source: o.source }));

  // ---------- Derived stats (each one says where its number comes from) ----------
  const talentMod = (k) => C.talents.reduce((n, t) => n + ((TALENT_MODS[t.id] || {})[k] || 0) * t.rank, 0);
  const worn = () => C.armor.filter((a) => a.worn);
  // Only one worn armor counts (CRB p.92); worn armor is 3 encumbrance lighter, min 0. Defense caps at 4 (p.104).
  const armorOn = () => worn()[0];
  const itemEnc = (g) => (g.worn ? Math.max(0, (g.encumbrance || 0) - 3) : (g.encumbrance || 0)) * (g.qty || 1);
  const encOver = () => Math.max(0, [...C.weapons, ...C.armor, ...C.gear].reduce((n, g) => n + itemEnc(g), 0) - (5 + C.chars.Br));
  const derived = () => {
    const tw = talentMod("wounds"), ts = talentMod("strain"), a = armorOn(), as = a ? a.soak : 0, ad = Math.min(4, a ? a.defense : 0);
    const enc = [...C.weapons, ...C.armor, ...C.gear].reduce((n, g) => n + itemEnc(g), 0), over = encOver();
    return {
      wounds: C.archetype.wounds + C.chars.Br + tw, woundsWhy: `${C.archetype.wounds} + Brawn ${C.chars.Br}${tw ? ` + Toughened ${tw}` : ""}`,
      strain: C.archetype.strain + C.chars.Will + ts, strainWhy: `${C.archetype.strain} + Willpower ${C.chars.Will}${ts ? ` + Grit ${ts}` : ""}`,
      soak: C.chars.Br + as, soakWhy: `Brawn ${C.chars.Br} + armor ${as}`,
      melee: ad, ranged: ad, defWhy: a ? a.name : "No armor",
      enc, encMax: 5 + C.chars.Br, over,
      encWhy: over ? `Encumbered: +${over} setback on Agility and Brawn checks${over >= C.chars.Br ? ", no free maneuver (each costs 2 strain)" : ""}`
        : `5 + Brawn ${C.chars.Br}${worn().length ? " · worn armor counts 3 less" : ""}`,
    };
  };
  const skillPool = (skill, stat) => {
    const ch = C.chars[stat], rk = C.ranks[skill] || 0;
    return { ...emptyPool(), proficiency: Math.min(ch, rk), ability: Math.max(ch, rk) - Math.min(ch, rk), setback: encSetback(stat) };
  };
  // Encumbered: one setback per point over the threshold on Agility and Brawn checks (CRB p.85).
  const encSetback = (stat) => (stat === "Ag" || stat === "Br" ? encOver() : 0);
  const encNote = (stat) => (encSetback(stat) ? ` · Encumbered +${encSetback(stat)} setback` : "");
  const emptyPool = () => ({ proficiency: 0, ability: 0, boost: 0, challenge: 0, difficulty: 0, setback: 0 });
  // Slap-patches are painkillers (SotB p.100): 5 wounds, then one fewer each use that day, none after the fifth (CRB p.116).
  const isPainkiller = (g) => /slap-patch|painkiller/.test(g.id);
  const painkillerHeal = () => Math.max(0, 5 - C.painkillers);
  const weaponBase = (w) => (w["adds-brawn"] ? C.chars.Br + w["damage-bonus"] : w.damage);
  const qualityText = (w) => (w.qualities || []).map((q) => q.name + (q.rating ? " " + q.rating : ""));

  // ---------- State ----------
  const isDesk = () => window.matchMedia("(min-width: 1000px)").matches;
  // Shared by the whole group; in the app it syncs live with the GM and other players (M6).
  const STORY = ["player", "player", "gm", "gm", "gm"];
  const S = { tab: isDesk() ? "combat" : "skills", q: "", hit: "", lastCrit: null, spMenu: false, flipped: -1,
    d: { open: false, check: null, pool: emptyPool(), ladder: 2, result: null } };
  const $ = (id) => document.getElementById(id);
  const esc = (t) => String(t).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
  const log = (text) => { C.history.unshift({ when: "Now", text }); if (S.tab === "history") renderTab(); };
  let toastTimer = 0;
  const toast = (text) => { const t = $("toast"); t.textContent = text; t.hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => (t.hidden = true), 2600); };
  try { const saved = localStorage.getItem("ttg-sheet-notes"); if (saved !== null) C.notes = saved; } catch (e) {}

  // ---------- Story points ----------
  const playerPoints = () => STORY.filter((x) => x === "player").length;
  const canUpgrade = () => S.d.open && !S.d.result && !Dice3D.busy;
  function renderStory() {
    $("tokens").innerHTML = STORY.map((x, i) => {
      const label = x === "player" ? "Player story point. Tap to spend." : "GM story point";
      return `<button type="button" class="tok ${x}${i === S.flipped ? " flip" : ""}" data-tok="${i}" aria-label="${label}" title="${label}"${x === "gm" ? ' aria-disabled="true"' : ""}></button>`;
    }).join("");
    $("sp-count").textContent = `${playerPoints()} player \u00b7 ${STORY.length - playerPoints()} GM`;
    const m = $("sp-menu");
    m.hidden = !S.spMenu;
    if (S.spMenu) m.innerHTML = `<p>Spend a player story point. It flips to the GM side.</p>
      <button type="button" class="btn" data-act="sp-detail">Add a story detail (tell the GM what it is)</button>
      <p>To upgrade a check, open the roll and tap the story point chip in it.</p>
      <button type="button" class="linkbtn" data-act="sp-cancel">Cancel</button>`;
    S.flipped = -1;
  }
  // Moves one point between sides. Returns false if the side has none.
  function flipPoint(from, why) {
    const i = STORY.indexOf(from);
    if (i < 0) return false;
    STORY[i] = from === "player" ? "gm" : "player";
    S.flipped = i;
    log(why);
    return true;
  }
  function spendUpgrade() {
    if (!canUpgrade() || !flipPoint("player", `Spent a story point: upgraded ${S.d.check.label}`)) return;
    const p = S.d.pool;
    if (p.ability > 0) { p.ability--; p.proficiency++; } else p.ability++;
    S.spMenu = false;
    renderStory(); renderDrawer();
    toast("Upgraded your check. The point goes to the GM.");
  }
  function spendDetail() {
    if (!flipPoint("player", "Spent a story point: added a story detail")) return;
    S.spMenu = false;
    renderStory();
    toast("Story point spent. Tell the GM the detail you're adding.");
  }

  // ---------- Header, stats, vitals ----------
  function renderHeader() {
    $("csub").innerHTML = `${C.archetype.name} ${cite(C.archetype.source)} · ${C.career.name} ${cite(C.career.source)} · ${esc(C.tagline)}`;
    $("xp").innerHTML = `<b>${C.xp.total - C.xp.spent} XP</b><span>available of ${C.xp.total} earned</span>
      <div><button type="button" class="linkbtn" data-act="spendxp">Spend XP</button></div>`;
    $("stats").innerHTML = Object.entries(C.chars).map(([k, v]) =>
      `<button type="button" class="stat" data-char="${k}" aria-label="Roll ${CHAR_NAMES[k]} ${v}"><b>${v}</b><span>${CHAR_NAMES[k]}</span></button>`).join("");
  }

  function pips(cur, max, kind) {
    const n = Math.max(max, cur);
    return `<div class="pips ${kind}" style="--n:${n}" aria-hidden="true">${Array.from({ length: n }, (_, i) =>
      `<i class="${i < cur ? (i >= max ? "over" : "on") : ""}"></i>`).join("")}</div>`;
  }
  function trackCard(kind, cur, max, why) {
    const over = cur > max;
    const title = kind === "wound" ? "Wounds" : "Strain";
    const ctl = kind === "wound"
      ? `<button type="button" class="btn step" data-adj="wounds" data-by="-1" aria-label="Heal 1 wound">&minus;</button>
         <button type="button" class="btn step" data-adj="wounds" data-by="1" aria-label="Take 1 wound">+</button>
         <span class="hit"><label class="sr" for="hit">Incoming damage</label><input id="hit" type="number" inputmode="numeric" min="0" placeholder="Dmg" value="${esc(S.hit)}">
         <button type="button" class="btn" data-act="hit">Take hit</button></span>`
      : `<button type="button" class="btn step" data-adj="strain" data-by="-1" aria-label="Recover 1 strain">&minus;</button>
         <button type="button" class="btn step" data-adj="strain" data-by="1" aria-label="Suffer 1 strain">+</button>`;
    const alert = over ? `<div class="alert">Incapacitated: ${title.toLowerCase()} exceed threshold${kind === "wound" ? ' <button type="button" class="btn danger" data-act="crit">Roll critical injury</button>' : ""}</div>` : "";
    return `<div class="card track${over ? " over" : ""}"><div class="hd"><span class="label">${title}</span><span class="val"><b>${cur}</b> / ${max}</span></div>
      ${pips(cur, max, kind)}<div class="ctl">${ctl}</div>${alert}<div class="note">Threshold ${why}</div></div>`;
  }
  function renderVitals() {
    const d = derived();
    $("vitals").innerHTML = trackCard("wound", C.wounds, d.wounds, d.woundsWhy) + trackCard("strain", C.strain, d.strain, d.strainWhy) +
      `<div class="card small"><span class="label">Soak</span><b>${d.soak}</b><span class="why">${d.soakWhy}</span></div>
       <div class="card small"><span class="label">Defense</span><div class="pair"><div><b>${d.melee}</b><small>Melee</small></div><div><b>${d.ranged}</b><small>Ranged</small></div></div><span class="why">${esc(d.defWhy)}</span></div>
       <div class="card small"><span class="label">Encumbrance</span><b>${d.enc}<span style="font-size:18px;color:var(--muted)"> / ${d.encMax}</span></b>
         <div class="enc-bar"><i style="width:${Math.min(100, (d.enc / d.encMax) * 100)}%${d.over ? ";background:var(--bad)" : ""}"></i></div><span class="why">${d.encWhy}</span></div>`;
  }

  // ---------- Skills ----------
  const miniPool = (sp) => ["proficiency", "ability"].map((t) => Array.from({ length: sp[t] }, () => dieSVG(t, null, DICE[t].name)).join("")).join("");
  function skillsHTML() {
    const q = S.q.trim().toLowerCase();
    let html = "";
    for (const [group, list] of SKILLS) {
      const rows = list.filter(([n]) => !q || n.toLowerCase().includes(q));
      if (!rows.length) continue;
      html += `<div class="group"><span class="label">${group}</span>` + rows.map(([n, stat]) => {
        const rk = C.ranks[n] || 0, career = isCareer(n);
        return `<button type="button" class="srow" data-skill="${n}" aria-label="Roll ${n}, ${CHAR_NAMES[stat]} ${C.chars[stat]}, rank ${rk}${career ? ", career skill" : ""}">
          <span class="nm"><span class="dot${career ? "" : " off"}" title="${career ? "Career skill" : "Non-career skill"}"></span>${n}<i>${stat}</i></span>
          <span class="ranks" aria-hidden="true">${Array.from({ length: 5 }, (_, i) => `<i class="${i < rk ? "on" : ""}"></i>`).join("")}</span>
          <span class="mini">${miniPool(skillPool(n, stat))}</span></button>`;
      }).join("") + `</div>`;
    }
    return html || `<p class="note" style="padding:0 14px 12px">No skill matches “${esc(S.q)}”.</p>`;
  }
  function renderSkillsDesk() { $("skills-desk").innerHTML = skillsHTML(); }

  // ---------- Tabs ----------
  const TABS = [
    ["skills", "Skills", true], ["combat", "Combat"], ["talents", "Talents"], ["gear", "Gear"],
    ["crits", "Crits"], ["story", "Obligation & story"], ["notes", "Notes"], ["history", "History"],
  ];
  function renderTabs() {
    if (isDesk() && S.tab === "skills") S.tab = "combat";
    const count = { talents: C.talents.length, crits: C.crits.length, gear: C.weapons.length + C.armor.length + C.gear.length };
    $("tabs").innerHTML = TABS.filter(([, , phone]) => !(phone && isDesk())).map(([id, label]) =>
      `<button type="button" role="tab" data-tab="${id}" aria-selected="${S.tab === id}">${label}${count[id] ? `<span class="count">${count[id]}</span>` : ""}</button>`).join("");
    renderTab();
  }
  function renderTab() {
    const fn = { skills: tabSkills, combat: tabCombat, talents: tabTalents, gear: tabGear, crits: tabCrits, story: tabStory, notes: tabNotes, history: tabHistory }[S.tab];
    $("tabbody").innerHTML = fn();
    $("tabbody").style.padding = S.tab === "skills" ? "8px 0" : "";
  }
  const tabSkills = () => `<label class="sr" for="q2">Filter skills</label><input class="search" id="q2" type="search" placeholder="Filter skills…" value="${esc(S.q)}" autocomplete="off"><div id="skills-phone">${skillsHTML()}</div>`;

  function weaponCard(w) {
    return `<div class="item weap"><div><div class="row1"><h3>${esc(w.name)}</h3><span class="meta">${w.skill}</span>${cite(w.source)}</div>
      <div class="stats-row"><span>Dmg <b>${weaponBase(w)}</b>${w["adds-brawn"] ? " (Br+" + w["damage-bonus"] + ")" : ""}</span><span>Crit <b>${w.crit}</b></span><span>Range <b>${w.range}</b></span>${qualityText(w).map((q) => `<span class="tag">${q}</span>`).join("")}</div></div>
      <span><button type="button" class="btn primary" data-weapon="${esc(w.id)}">Attack</button> <span class="mini" style="display:inline-flex">${miniPool(skillPool(w.skill, STAT_OF[w.skill]))}</span></span></div>`;
  }
  function tabCombat() {
    const d = derived();
    return `<div class="sect"><h3>Attacks</h3><div class="list">${C.weapons.map(weaponCard).join("")}</div></div>
      <div class="sect"><h3>Initiative</h3><div class="list"><div class="item"><p>Roll Cool if you saw it coming, Vigilance if you didn't.</p>
        <div class="acts"><button type="button" class="btn" data-skill="Cool">Cool ${miniPool(skillPool("Cool", "Pr"))}</button>
        <button type="button" class="btn" data-skill="Vigilance">Vigilance ${miniPool(skillPool("Vigilance", "Will"))}</button></div>
        <p class="note">Rapid Reaction: you may suffer up to ${(C.talents.find((t) => t.id === "rapid-reaction") || { rank: 0 }).rank} strain to add that many successes.</p></div></div></div>
      <div class="sect"><h3>Defenses</h3><dl class="kv"><dt>Soak</dt><dd>${d.soak} (${d.soakWhy})</dd><dt>Defense</dt><dd>Melee ${d.melee}, ranged ${d.ranged}</dd>
        <dt>Active crits</dt><dd>${C.crits.length} (+${C.crits.length * 10} to the next critical roll)</dd></dl></div>`;
  }
  // A ranked talent's later ranks count one tier higher each (CRB p.72).
  const slotTiers = (t) => Array.from({ length: t.ranked ? t.rank : 1 }, (_, i) => Math.min(5, t.tier + i));
  function pyramidHTML() {
    const counts = [1, 2, 3, 4, 5].map((t) => C.talents.flatMap(slotTiers).filter((x) => x === t).length);
    const ok = counts.every((c, i) => i === 0 || c === 0 || c < counts[i - 1]);
    const rows = [5, 4, 3, 2, 1].map((t) => {
      const have = counts[t - 1], slots = Math.max(have, 6 - t);
      return `<div class="tier"><span>Tier ${t}</span>${Array.from({ length: slots }, (_, i) => `<i class="slot${i < have ? " on" : ""}"></i>`).join("")}</div>`;
    }).join("");
    return `<div class="pyramid" aria-label="Talent pyramid: ${counts.map((c, i) => `tier ${i + 1}: ${c}`).join(", ")}">${rows}</div>
      <p class="pyr-ok" style="${ok ? "" : "color:var(--bad)"}">${ok ? "Pyramid is valid. Each tier has more talents than the tier above it." : "Pyramid broken: a tier needs more talents than the tier above it."}</p>`;
  }
  function tabTalents() {
    const items = [...C.talents].sort((a, b) => a.tier - b.tier || a.name.localeCompare(b.name)).map((t) =>
      `<div class="item"><div class="row1"><h3>${t.name}${t.ranked ? ` <span class="mono" style="font-weight:400">${t.rank}</span>` : ""}</h3>
        <span class="tag">Tier ${t.tier}</span><span class="tag">${esc(t.activation)}</span>${t.ranked ? '<span class="tag">Ranked</span>' : ""}${TALENT_MODS[t.id] ? '<span class="tag">Applied to sheet</span>' : ""}${cite(t.source)}</div><p>${rich(t.text)}</p></div>`).join("");
    return pyramidHTML() + `<div class="list">${items}</div>`;
  }
  function tabGear() {
    const d = derived();
    const armor = C.armor.map((a) => `<div class="item"><div class="row1"><h3>${a.name}</h3>${a.worn ? '<span class="tag">Worn</span>' : ""}</div>
      <div class="meta">Soak +${a.soak} · Defense ${a.defense} · Encumbrance ${a.encumbrance} · HP ${a["hard-points"] ?? 0} ${cite(a.source)}</div></div>`).join("");
    const gear = C.gear.map((g, i) => `<div class="item"><div class="row1"><h3>${esc(g.name)}</h3><span class="meta">×${g.qty} · Enc ${g.encumbrance || 0}</span>${cite(g.source)}</div>
      ${isPainkiller(g) ? `<p>A painkiller: heals 5 wounds, one fewer for each one already used today (CRB p.116). ${C.painkillers ? `${C.painkillers} used today.` : ""}</p>
      <div class="acts"><button type="button" class="btn" data-use="${i}" ${g.qty && C.painkillers < 5 ? "" : "disabled"}>Use (${g.qty} left, heals ${painkillerHeal()})</button></div>` : ""}</div>`).join("");
    const weapons = C.weapons.map((w) => `<div class="item"><div class="row1"><h3>${esc(w.name)}</h3><span class="meta">Enc ${w.encumbrance} · HP ${w["hard-points"] ?? 0}</span>${cite(w.source)}</div>
      <div class="meta">${w.skill} · Dmg ${w["adds-brawn"] ? "+" + w["damage-bonus"] : w.damage} · Crit ${w.crit} · ${w.range}${qualityText(w).length ? " · " + qualityText(w).join(", ") : ""}</div></div>`).join("");
    return `<div class="sect"><dl class="kv"><dt>Credits</dt><dd class="mono">${C.credits}</dd><dt>Encumbrance</dt><dd>${d.enc} of ${d.encMax}</dd></dl></div>
      <div class="sect"><h3>Weapons</h3><div class="list">${weapons}</div></div>
      <div class="sect"><h3>Armor</h3><div class="list">${armor}</div></div>
      <div class="sect"><h3>Gear</h3><div class="list">${gear}</div></div>`;
  }
  function tabCrits() {
    const bonus = C.crits.length * 10;
    const last = S.lastCrit ? `<span>Rolled <b>${S.lastCrit.raw}</b> + ${S.lastCrit.bonus} = <b>${S.lastCrit.total}</b>: <strong>${esc(S.lastCrit.name)}</strong></span>` : "";
    const list = C.crits.length ? C.crits.map((c, i) => `<div class="item"><div class="row1"><h3>${esc(c.name)}</h3><span class="tag">${c.severity ? SEV[c.severity] + " to heal" : "Can't be healed"}</span><span class="meta">rolled ${c.roll} · ${c.session}</span>${cite(c.source)}</div>
      <p>${rich(c.text)}</p><div class="acts"><button type="button" class="btn" data-heal="${i}">Mark healed</button></div></div>`).join("") : `<p class="note">No active critical injuries.</p>`;
    return `<div class="critroll"><button type="button" class="btn danger" data-act="crit">Roll critical injury</button><span class="meta">d100 + ${bonus} for ${C.crits.length} active</span>${last}</div>
      <div class="list">${list}</div><p class="note">Results come from Table I.6–10: Critical Injury Result ${cite({ book: "crb", page: 115 })}.</p>`;
  }
  function tabStory() {
    const ob = C.obligation, kind = ob.type.kind === "secret" ? "Secret" : "Obligation";
    return `<div class="sect"><h3>${kind}</h3><div class="item"><div class="debt-big"><b>${ob.magnitude}</b><span>${esc(ob.type.name)}, tied to <span class="corp">${esc(ob.attachment)}</span></span><span class="tag">Magnitude</span>${cite({ book: "house" })}</div>
        <p>${esc(ob.why)}</p>${ob.log.map((l) => `<p class="meta">${l.when}: ${esc(l.text)}</p>`).join("")}
        <p class="note">Each session the GM rolls d100 against the party's total magnitude. When yours comes due: −2 strain threshold for you, −1 for everyone else, doubled on doubles.</p></div></div>
      <div class="sect"><h3>Motivations</h3><div class="list">${C.motivations.map(([k, v, t]) => `<div class="item"><div class="row1"><span class="label">${k}</span><h3>${v}</h3></div><p>${t}</p></div>`).join("")}</div></div>
      <div class="sect"><h3>Session 0</h3><div class="item"><p class="meta">${C.session0.q}</p><p>${C.session0.a}</p>
        <p class="note"><span class="tag gm">GM</span> Your GM has tied this answer to a later act.</p></div></div>`;
  }
  const tabNotes = () => `<label class="label" for="notes">Notes only you and the GM can see</label><textarea id="notes">${esc(C.notes)}</textarea><p class="note">Saves as you type.</p>`;
  const tabHistory = () => `<ul class="hist">${C.history.map((h) => `<li><time>${h.when}</time><span>${esc(h.text)}</span></li>`).join("")}</ul>`;

  // ---------- Roll drawer ----------
  function openRoll(check, pool) {
    S.d = { open: true, check, pool, base: { ...pool }, ladder: check.ladder ?? 2, result: null };
    applyLadder(S.d.ladder);
    renderDrawer();
    $("scrim").hidden = false; $("drawer").hidden = false;
    $("drawer").querySelector(".rollbtn").focus();
  }
  function closeRoll() { S.d.open = false; $("scrim").hidden = true; $("drawer").hidden = true; }
  function applyLadder(i) { S.d.ladder = i; S.d.pool.difficulty = LADDER[i].n; S.d.pool.challenge = 0; }
  const dTotal = () => ORDER.reduce((n, t) => n + S.d.pool[t], 0);

  function drawerPoolHTML() {
    const p = S.d.pool, chk = S.d.check;
    const pos = ORDER.filter((t) => DICE[t].pos).flatMap((t) => Array(p[t]).fill(t));
    const neg = ORDER.filter((t) => !DICE[t].pos).flatMap((t) => Array(p[t]).fill(t));
    const btn = (t) => `<button type="button" class="die" data-remove="${t}" title="Remove a ${DICE[t].name} die">${dieSVG(t, null, "Remove " + DICE[t].name + " die")}</button>`;
    const attack = chk.weapon && chk.weapon.range !== "Engaged";
    return `<div class="tray" aria-label="Pool. Tap a die to remove it.">${dTotal() ? pos.map(btn).join("") + (pos.length && neg.length ? '<span class="vs">VS</span>' : "") + neg.map(btn).join("") : '<span class="meta">Empty pool</span>'}</div>
      <div><span class="label">${attack ? "Difficulty · range to target" : "Difficulty"}</span><div class="chips">${LADDER.map((l, i) =>
        `<button type="button" class="chip" data-ladder="${i}" aria-pressed="${S.d.ladder === i && p.difficulty + p.challenge === l.n}">${l.name}<span class="dp">${"<i></i>".repeat(l.n)}</span>${attack && l.range ? `<small>${l.range}</small>` : ""}</button>`).join("")}</div></div>
      <div class="chips">
        <button type="button" class="chip" data-add="boost">${dieSVG("boost", null, "Boost")} + Boost</button>
        <button type="button" class="chip" data-add="setback">${dieSVG("setback", null, "Setback")} + Setback</button>
        <button type="button" class="chip sp" data-act="sp-upgrade" ${playerPoints() ? "" : "disabled"}><span class="tok-mini" aria-hidden="true"></span>Story point: upgrade (${playerPoints()} left)</button>
        <button type="button" class="chip" data-up="ability">Upgrade ability</button>
        <button type="button" class="chip" data-up="difficulty">Upgrade difficulty</button></div>
      <button type="button" class="rollbtn" data-act="roll" ${dTotal() && !Dice3D.busy ? "" : "disabled"}>Roll</button>`;
  }
  function renderDrawer() {
    const chk = S.d.check;
    $("drawer").innerHTML = `<div class="grab" aria-hidden="true"></div><header><div><h2 id="d-title">${chk.label}</h2><div class="meta">${chk.meta}</div></div>
      <button type="button" class="x" data-act="close" aria-label="Close roll">×</button></header>
      <div class="body">${S.d.result ? resultHTML() : drawerPoolHTML()}</div>`;
  }

  function verdictText(r) {
    const a = r.netA > 0 ? (r.success ? ", with Advantage" : ", but with Advantage") : r.netA < 0 ? (r.success ? ", but with Threat" : ", with Threat") : "";
    return (r.success ? "Success" : "Failure") + a;
  }
  function flavor(r) {
    if (r.success && r.netA > 0) return "You do it, and something extra goes your way.";
    if (r.success && r.netA < 0) return "You do it, but it costs you.";
    if (!r.success && r.netA > 0) return "It doesn't work, but you get something out of it.";
    if (!r.success && r.netA < 0) return "It doesn't work, and things get worse.";
    return r.success ? "You do it, cleanly." : "It doesn't work.";
  }
  const remaining = (r) => r.spent.reduce((acc, s) => ({ adv: acc.adv - s.paidA, tri: acc.tri - s.paidR }), { adv: Math.max(0, r.netA), tri: r.triumph });
  // Costs from Table I.6-2: N Advantage (or one Triumph where the book allows it), one Triumph, or two Triumphs.
  function payment(opt, haveA, haveR, crit) {
    if (opt.cost === "r") return haveR >= 1 ? { paidA: 0, paidR: 1 } : null;
    if (opt.cost === "rr") return haveR >= 2 ? { paidA: 0, paidR: 2 } : null;
    const need = opt.cost === "crit" ? crit : opt.cost;
    if (haveA >= need) return { paidA: need, paidR: 0 };
    return opt.orTriumph && haveR >= 1 ? { paidA: 0, paidR: 1 } : null;
  }
  const costIcons = (o, crit) => (o.cost === "r" ? sym("r") : o.cost === "rr" ? sym("r", 2) : sym("a", o.cost === "crit" ? crit : o.cost));
  function spendHTML(r) {
    const w = S.d.check.weapon, crit = w ? w.crit : 0, rem = remaining(r);
    let html = "";
    if (r.netA > 0 || r.triumph > 0) {
      html += `<div><h4>Spend Advantage &amp; Triumph <span class="left">${rem.adv} ${sym("a")} ${rem.tri} ${sym("r")} left</span></h4>` +
        ADV_OPTS.map((o, i) => ({ o, i })).filter(({ o }) => !o.attackOnly || w).map(({ o, i }) =>
          `<button type="button" class="opt" data-spend="${i}" ${payment(o, rem.adv, rem.tri, crit) ? "" : "disabled"}><span class="cost">${costIcons(o, crit)}</span><span>${rich(o.label)}${o.cost === "crit" ? ` (Crit ${crit})` : ""}</span></button>`).join("") +
        `<p class="note">Options from Table I.6–2 ${cite({ book: "crb", page: 104 })}.</p>` +
        (r.spent.length ? `<div class="spent">${r.spent.map((s) => `<span>${s.label}</span>`).join("")}<button type="button" class="linkbtn" data-act="undo">Undo last</button></div>` : "") + `</div>`;
    }
    if (r.netA < 0 || r.despair > 0) html += `<div class="readonly">The GM spends ${Math.max(0, -r.netA)} Threat${r.despair ? ` and ${r.despair} Despair` : ""} on their screen. What they pick shows up here and in your history.</div>`;
    return html;
  }
  function resultHTML() {
    const r = S.d.result, w = S.d.check.weapon;
    const n = (s, count, name) => count ? `<span class="n">${sym(s)}<b>${count}</b> ${name}${count > 1 && name !== "Despair" ? (name.endsWith("s") ? "es" : "s") : ""}</span>` : "";
    let html = `<div class="verdict"><h3 class="${r.success ? "ok" : "no"}">${verdictText(r)}</h3><p>${flavor(r)}</p></div>
      <div class="net">${r.netS > 0 ? n("s", r.netS, "Success") : n("f", -r.netS, "Failure")}${r.netA > 0 ? n("a", r.netA, "Advantage") : n("t", -r.netA, "Threat")}${n("r", r.triumph, "Triumph")}${n("d", r.despair, "Despair")}
        ${r.netS === 0 && r.netA === 0 && !r.triumph && !r.despair ? '<span class="n">Everything cancelled</span>' : ""}</div>
      <div class="rolled${r.tumble ? " tumble" : ""}" aria-label="Dice rolled">${r.dice.map((d) => dieSVG(d.type, d.face)).join("")}</div>`;
    if (w && r.success) {
      const base = weaponBase(w), rem = remaining(r);
      html += `<div class="dmg"><span class="label">Damage</span><b>${base + r.netS}</b><span>${base} base + ${r.netS} net success</span>${rem.adv >= w.crit || rem.tri > 0 ? `<span class="crit">Critical available (Crit ${w.crit})</span>` : ""}</div>`;
    }
    if (S.d.check.kind === "recover" && r.netS > 0) html += `<button type="button" class="btn primary" data-act="recover">Recover ${r.netS} strain</button>`;
    return html + spendHTML(r) + `<button type="button" class="btn" data-act="again">Roll again</button>`;
  }

  function doRoll() {
    if (!dTotal() || Dice3D.busy) return;
    const types = poolList(S.d.pool);
    const done = (dice, tumble) => {
      S.d.result = { ...scoreDice(dice), spent: [], tumble };
      log(`Rolled ${S.d.check.label}: ${verdictText(S.d.result)}`);
      $("live").textContent = verdictText(S.d.result);
      if (S.d.open) renderDrawer();
    };
    if (Dice3D.ok && !window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      $("drawer").querySelector(".rollbtn").disabled = true;
      Dice3D.roll(types, (dice) => done(dice, false));
    } else done(instantRoll(types), true);
  }
  function spend(i) {
    const r = S.d.result, o = ADV_OPTS[i], w = S.d.check.weapon, rem = remaining(r);
    const pay = payment(o, rem.adv, rem.tri, w ? w.crit : 0);
    if (!pay) return;
    r.spent.push({ label: o.label, ...pay, effect: o.effect });
    if (o.effect && o.effect.strain) C.strain = Math.max(0, C.strain + o.effect.strain);
    log(`Spent: ${o.label}`);
    renderDrawer(); renderVitals();
  }
  function undoSpend() {
    const s = S.d.result.spent.pop();
    if (s && s.effect && s.effect.strain) C.strain -= s.effect.strain;
    log(`Undid: ${s.label}`);
    renderDrawer(); renderVitals();
  }

  // ---------- Sheet actions ----------
  function adjust(key, by) {
    C[key] = Math.max(0, C[key] + by);
    log(`${by > 0 ? (key === "wounds" ? "Took" : "Suffered") : (key === "wounds" ? "Healed" : "Recovered")} ${Math.abs(by)} ${key === "wounds" ? "wound" : "strain"}${Math.abs(by) > 1 ? "s" : ""}`);
    renderVitals();
  }
  function takeHit() {
    const raw = parseInt(S.hit, 10);
    if (!(raw >= 0)) { toast("Enter the damage first"); return; }
    const d = derived(), dealt = Math.max(0, raw - d.soak);
    C.wounds += dealt; S.hit = "";
    log(`Hit for ${raw}, soaked ${raw - dealt}, took ${dealt} wound${dealt === 1 ? "" : "s"}`);
    toast(dealt ? `${raw} damage − ${d.soak} soak = ${dealt} wound${dealt === 1 ? "" : "s"}` : `Soak absorbed all ${raw} damage`);
    renderVitals();
  }
  function rollCrit() {
    const raw = rand(100) + 1, bonus = C.crits.length * 10, total = raw + bonus;
    const row = CRIT_TABLE.find(({ roll: [lo, hi] }) => total >= lo && (hi === null || total <= hi));
    S.lastCrit = { raw, bonus, total, name: row.name };
    C.crits.push({ ...row, roll: total, session: "Now" });
    log(`Critical injury: ${row.name} (rolled ${total})`);
    if (row.id === "discouraging-wound") { flipPoint("player", "Discouraging Wound: a player story point moved to the GM"); renderStory(); }
    S.tab = "crits"; renderTabs(); renderVitals();
  }
  function useGear(i) {
    const g = C.gear[i];
    if (!g.qty || !isPainkiller(g) || C.painkillers >= 5) return;
    const healed = Math.min(C.wounds, painkillerHeal());
    g.qty--; C.painkillers++; C.wounds -= healed;
    log(`Used ${g.name}: healed ${healed} wound${healed === 1 ? "" : "s"}`);
    toast(`${g.name}: healed ${healed}`);
    renderVitals(); renderTab();
  }
  function bestRecoveryCheck() {
    const opts = [["Cool", "Pr"], ["Discipline", "Will"]].map(([s, st]) => ({ s, st, p: skillPool(s, st) }));
    const score = (o) => o.p.proficiency * 2 + o.p.ability;
    return opts.sort((a, b) => score(b) - score(a))[0];
  }

  function renderAll() { renderStory(); renderHeader(); renderVitals(); renderSkillsDesk(); renderTabs(); }

  document.addEventListener("click", (e) => {
    const t = e.target.closest("button");
    if (!t) {
      if (S.spMenu && !e.target.closest("#sp-menu")) { S.spMenu = false; renderStory(); }
      if (e.target.id === "scrim") closeRoll();
      return;
    }
    const d = t.dataset;
    if (d.tok !== undefined) { if (STORY[Number(d.tok)] === "player") { S.spMenu = !S.spMenu; renderStory(); } return; }
    if (d.act === "sp-upgrade") { spendUpgrade(); return; }
    if (d.act === "sp-detail") { spendDetail(); return; }
    if (d.act === "sp-cancel") { S.spMenu = false; renderStory(); return; }
    if (S.spMenu && !t.closest("#sp-menu")) { S.spMenu = false; renderStory(); }
    if (d.tab) { S.tab = d.tab; renderTabs(); return; }
    if (d.skill) { const st = STAT_OF[d.skill], rk = C.ranks[d.skill] || 0; openRoll({ label: d.skill, meta: `${CHAR_NAMES[st]} ${C.chars[st]} · Rank ${rk}${encNote(st)}` }, skillPool(d.skill, st)); return; }
    if (d.char) { openRoll({ label: `${CHAR_NAMES[d.char]} check`, meta: `${CHAR_NAMES[d.char]} ${C.chars[d.char]}, no skill` }, { ...emptyPool(), ability: C.chars[d.char] }); return; }
    if (d.weapon) {
      const w = C.weapons.find((x) => x.id === d.weapon), st = STAT_OF[w.skill];
      openRoll({ label: `${w.name} attack`, meta: `${w.skill} · ${CHAR_NAMES[st]} ${C.chars[st]} · Rank ${C.ranks[w.skill] || 0}${encNote(st)}`, weapon: w }, skillPool(w.skill, st)); return;
    }
    if (d.adj) { adjust(d.adj, Number(d.by)); return; }
    if (d.use !== undefined) { useGear(Number(d.use)); return; }
    if (d.heal !== undefined) { const c = C.crits.splice(Number(d.heal), 1)[0]; log(`Healed critical injury: ${c.name}`); renderTabs(); return; }
    if (d.remove) { S.d.pool[d.remove]--; renderDrawer(); return; }
    if (d.add) { S.d.pool[d.add]++; renderDrawer(); return; }
    if (d.ladder !== undefined) { applyLadder(Number(d.ladder)); renderDrawer(); return; }
    if (d.up) {
      const [lo, hi] = d.up === "ability" ? ["ability", "proficiency"] : ["difficulty", "challenge"];
      if (S.d.pool[lo] > 0) { S.d.pool[lo]--; S.d.pool[hi]++; } else S.d.pool[lo]++;
      renderDrawer(); return;
    }
    if (d.spend !== undefined) { spend(Number(d.spend)); return; }
    const act = d.act;
    if (act === "roll") doRoll();
    else if (act === "close") closeRoll();
    else if (act === "undo") undoSpend();
    else if (act === "again") { S.d.result = null; S.d.pool = { ...S.d.pool, boost: S.d.base.boost, setback: S.d.base.setback }; applyLadder(S.d.check.ladder ?? 2); renderDrawer(); }
    else if (act === "hit") takeHit();
    else if (act === "crit") rollCrit();
    else if (act === "recover") { const n = S.d.result.netS; C.strain = Math.max(0, C.strain - n); log(`End of encounter: recovered ${n} strain`); closeRoll(); renderVitals(); toast(`Recovered ${n} strain`); }
    else if (act === "spendxp") toast("Spending XP opens the character builder, the next prototype.");
    else if (t.id === "shortrest") { const b = bestRecoveryCheck(); openRoll({ label: `Recover strain: ${b.s}`, meta: `End of encounter · Simple check, 1 strain back per success`, kind: "recover", ladder: 0 }, b.p); }
  });
  document.addEventListener("input", (e) => {
    if (e.target.id === "q" || e.target.id === "q2") {
      S.q = e.target.value;
      renderSkillsDesk();
      if (e.target.id === "q2") $("skills-phone").innerHTML = skillsHTML();
    }
    if (e.target.id === "hit") S.hit = e.target.value;
    if (e.target.id === "notes") { C.notes = e.target.value; try { localStorage.setItem("ttg-sheet-notes", C.notes); } catch (err) {} }
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && S.spMenu) { S.spMenu = false; renderStory(); return; }
    if (e.key === "Escape" && S.d.open) closeRoll();
    if (e.key === "Enter" && e.target.id === "hit") takeHit();
    if (e.key.toLowerCase() === "r" && S.d.open && !S.d.result && !/^(INPUT|TEXTAREA)$/.test(document.activeElement.tagName)) { e.preventDefault(); doRoll(); }
  });
  let wasDesk = isDesk();
  window.addEventListener("resize", () => { if (isDesk() !== wasDesk) { wasDesk = isDesk(); renderTabs(); } });

  Dice3D.init();
  renderAll();
})();
