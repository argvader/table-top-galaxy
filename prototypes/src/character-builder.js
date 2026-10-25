(() => {
/*DICE_CORE*/
/*DICE_SVG*/
/*CONTENT*/
  // ---------- Library (injected by bb proto-build from content/, narrowed to the Android setting) ----------
  const KEY_OF = { brawn: "Br", agility: "Ag", intellect: "Int", cunning: "Cun", willpower: "Will", presence: "Pr" };
  const CHAR_KEYS = ["Br", "Ag", "Int", "Cun", "Will", "Pr"];
  const CHAR_NAMES = { Br: "Brawn", Ag: "Agility", Int: "Intellect", Cun: "Cunning", Will: "Willpower", Pr: "Presence" };
  const BOOK = { crb: "CRB", sotb: "SotB", "sotb-guns": "Gun Locker", epg: "EPG", house: "House rule" };
  const SKILL_GROUPS = ["General", "Combat", "Social", "Knowledge"];
  const SKILLS = SKILL_GROUPS.map((g) => [g, CONTENT.skills.filter((s) => s.category === g).map((s) => [s.name, KEY_OF[s.characteristic]])]);
  const STAT_OF = Object.fromEntries(SKILLS.flatMap(([, l]) => l));
  const ALL_SKILLS = SKILLS.flatMap(([, l]) => l.map(([n]) => n));
  const ARCHETYPES = CONTENT.archetypes.map((a) => ({
    id: a.id, name: a.name, xp: a.xp, wounds: a.wounds, strain: a.strain, abilities: a.abilities, source: a.source,
    chars: Object.fromEntries(Object.entries(a.characteristics).map(([k, v]) => [KEY_OF[k], v])),
    free: a["free-skills"] || null, freeItem: a["free-item"] || null, startingText: a["starting-skills"] || "",
  }));
  const CAREERS = CONTENT.careers;
  const TALENTS = CONTENT.talents;
  // Talents whose effect the builder applies to derived stats.
  const TALENT_MODS = { toughened: { wounds: 2 }, grit: { strain: 1 } };
  const GEAR = [...CONTENT.weapons, ...CONTENT.armor, ...CONTENT.gear].map((g) => ({ ...g, group: g.group || (g.kind === "weapon" ? g.skill : "Other") }));
  const MOTIVATION_TABLE = CONTENT.motivations;
  const OBLIGATIONS = CONTENT["obligation-types"];
  // Campaign data (Manifest Destiny), not book content: suggested attachments for a Secret or Obligation.
  const CORPS = ["Haas-Bioroid", "Jinteki", "NBN", "Weyland Consortium"];
  const MAGNITUDES = [5, 10, 15];
  const START_CREDITS = 500;
  const MAX_RANK = 2, MAX_CHAR = 5;
  const MOTIVES = [["Desire", "What they want most"], ["Fear", "What they avoid at any cost"], ["Strength", "What they can count on"], ["Flaw", "What gets them in trouble"]];
  const SESSION0 = "The Concord's manifest lists one more crate than you loaded. What was in it, and why didn't you report it?";
  const STEPS = [["archetype", "Archetype"], ["career", "Career"], ["obligation", "Obligation"], ["xp", "Spend XP"], ["gear", "Gear"], ["story", "Motivations"], ["review", "Review"]];
  const S_OB = 2, S_XP = 3, S_GEAR = 4, S_STORY = 5, S_REVIEW = 6;

  // ---------- Draft ----------
  const blankDraft = () => ({
    v: 2, name: "", tagline: "", archetype: null, career: null, careerPicks: [], archPicks: [],
    charBuy: {}, skillBuy: {}, talents: [], gear: [], freeItem: null, pocket: null, s0: "", created: false,
    motives: Object.fromEntries(MOTIVES.map(([k]) => [k, { name: "", text: "" }])),
    ob: { type: null, magnitude: 10, attachment: "", why: "" },
  });
  const firstTalent = (...ids) => ids.find((id) => TALENTS.some((t) => t.id === id));
  function sampleDraft() {
    const d = Object.assign(blankDraft(), {
      name: "Krum", tagline: "Dock mechanic, Concord berth 9", archetype: "cyborg", career: "tech",
      careerPicks: ["Mechanics", "Computers (Sysops)", "Operating", "Knowledge (Science)"],
      charBuy: { Int: 1 }, skillBuy: { Brawl: 1 },
      ob: { type: "corporate-contract", magnitude: 10, attachment: "Weyland Consortium", why: "" },
    });
    const t = firstTalent("toughened", "durable", "grit");
    if (t) d.talents = [{ id: t, rank: 1 }];
    d.motives.Desire = { name: "Belonging", text: "Wants a crew that won't trade them for a better mechanic." };
    return d;
  }
  const KEY = "ttg-builder-draft-v2";
  let D = sampleDraft();
  try { const saved = JSON.parse(localStorage.getItem(KEY)); if (saved && saved.v === 2) D = Object.assign(blankDraft(), saved); } catch (e) {}
  const U = { step: S_XP, xpTab: "skills", q: "", tq: "", gq: "", gearTab: "weapon", careerOnly: false, tier: 0, sheet: false,
    visited: new Set([0, 1, 2, 3]), restartArmed: false };
  if (!D.archetype) { U.step = 0; U.visited = new Set([0]); }

  const $ = (id) => document.getElementById(id);
  const esc = (t) => String(t).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
  const cite = (src) => (src ? `<span class="cite">${BOOK[src.book] || src.book}${src.page ? ` p.${src.page}` : ""}</span>` : "");
  const SYM_TOKENS = { success: "s", advantage: "a", threat: "t", failure: "f", triumph: "r", despair: "d" };
  // Book text marks dice and symbols as [ability], [advantage] ...; draw them as the same icons the roller uses.
  const rich = (text) => esc(text || "").replace(/\[(\w+)\]/g, (m, k) =>
    SYM_TOKENS[k] ? `<span class="glyph" title="${k}">${sym(SYM_TOKENS[k])}</span>`
      : DICE[k] ? `<span class="glyph die-i">${dieSVG(k, null, DICE[k].name)}</span>` : m);
  const d100 = () => rand(100) + 1;
  const inRoll = (n, [lo, hi]) => n >= (lo || 0) && n <= (hi || 100);
  let toastTimer = 0;
  const toast = (text) => { const t = $("toast"); t.textContent = text; t.hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => (t.hidden = true), 2800); };
  let saveTimer = 0;
  function save() {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(() => {
      try { localStorage.setItem(KEY, JSON.stringify(D)); $("saved").textContent = "Draft saved"; } catch (e) { $("saved").textContent = ""; }
    }, 300);
  }

  // ---------- Rules (CRB p.44-45; every number derives from the draft) ----------
  const arch = () => ARCHETYPES.find((a) => a.id === D.archetype);
  const career = () => CAREERS.find((c) => c.id === D.career);
  const talentDef = (id) => TALENTS.find((t) => t.id === id);
  const gearDef = (id) => GEAR.find((g) => g.id === id);
  const obDef = () => OBLIGATIONS.find((o) => o.id === D.ob.type);
  const baseChar = (k) => (arch() ? arch().chars[k] : 2);
  const charVal = (k) => baseChar(k) + (D.charBuy[k] || 0);
  const isCareer = (s) => !!career() && career().skills.includes(s);
  const archFixed = (s) => { const f = arch() && arch().free; return f && f.skills && f.skills.includes(s) ? f.ranks : 0; };
  const archPicked = (s) => { const f = arch() && arch().free; return f && f.choose && D.archPicks.includes(s) ? f.ranks : 0; };
  const freeRanks = (s) => (D.careerPicks.includes(s) ? 1 : 0) + archFixed(s) + archPicked(s);
  const rankOf = (s) => freeRanks(s) + (D.skillBuy[s] || 0);
  const rankPrice = (s, r) => 5 * r + (isCareer(s) ? 0 : 5);
  const sumRange = (lo, hi, f) => { let n = 0; for (let r = lo; r <= hi; r++) n += f(r); return n; };

  const charsXP = () => CHAR_KEYS.reduce((n, k) => n + sumRange(baseChar(k) + 1, charVal(k), (r) => 10 * r), 0);
  const skillsXP = () => ALL_SKILLS.reduce((n, s) => n + sumRange(freeRanks(s) + 1, rankOf(s), (r) => rankPrice(s, r)), 0);
  // A ranked talent buys each further rank one tier higher, capped at tier 5 (CRB p.72).
  const slotTiers = (t) => Array.from({ length: t.rank }, (_, i) => Math.min(5, talentDef(t.id).tier + i));
  const tierCounts = (talents) => { const c = [0, 0, 0, 0, 0, 0]; talents.forEach((t) => slotTiers(t).forEach((tier) => c[tier]++)); return c; };
  const pyramidOk = (c) => [2, 3, 4, 5].every((t) => c[t] === 0 || c[t - 1] > c[t]);
  const talentsXP = () => D.talents.reduce((n, t) => n + slotTiers(t).reduce((m, tier) => m + 5 * tier, 0), 0);
  const obXP = () => (D.ob.type ? D.ob.magnitude : 0);
  const budget = () => (arch() ? arch().xp : 0) + obXP();
  const spentXP = () => charsXP() + skillsXP() + talentsXP();
  const xpLeft = () => budget() - spentXP();

  const talentMod = (k) => D.talents.reduce((n, t) => n + ((TALENT_MODS[t.id] || {})[k] || 0) * t.rank, 0);
  const ownedGear = () => D.gear.map((g) => ({ ...gearDef(g.id), qty: g.qty })).filter((g) => g.id);
  const freeItemDef = () => (D.freeItem ? gearDef(D.freeItem) : null);
  const carried = () => [...ownedGear(), ...(freeItemDef() ? [{ ...freeItemDef(), qty: 1, free: true }] : [])];
  const bestArmor = () => carried().filter((g) => g.kind === "armor").sort((a, b) => b.soak + b.defense - (a.soak + a.defense))[0];
  const creditsLeft = () => START_CREDITS - ownedGear().reduce((n, g) => n + (g.price || 0) * g.qty, 0);
  function derived() {
    const a = arch(), armor = bestArmor(), tw = talentMod("wounds"), ts = talentMod("strain");
    const bonus = carried().reduce((n, g) => n + (g["encumbrance-bonus"] || 0), 0);
    return {
      wounds: a ? a.wounds + charVal("Br") + tw : 0, woundsWhy: a ? `${a.wounds} + Brawn ${charVal("Br")}${tw ? ` + talents ${tw}` : ""}` : "",
      strain: a ? a.strain + charVal("Will") + ts : 0, strainWhy: a ? `${a.strain} + Willpower ${charVal("Will")}${ts ? ` + talents ${ts}` : ""}` : "",
      soak: charVal("Br") + (armor ? armor.soak : 0), def: armor ? armor.defense : 0,
      enc: carried().reduce((n, g) => n + (g.encumbrance || 0) * g.qty, 0), encMax: 5 + charVal("Br") + bonus, encBonus: bonus,
    };
  }
  const skillPool = (s) => { const ch = charVal(STAT_OF[s]), rk = rankOf(s); return { proficiency: Math.min(ch, rk), ability: Math.max(ch, rk) - Math.min(ch, rk) }; };

  // ---------- Purchase checks: null when allowed, otherwise the reason ----------
  const short = (cost) => (cost > xpLeft() ? `Needs ${cost} XP, you have ${Math.max(0, xpLeft())}` : null);
  function charUpBlock(k) {
    if (!arch()) return "Choose an archetype first";
    if (charVal(k) >= MAX_CHAR) return `${MAX_CHAR} is the most`;
    return short(10 * (charVal(k) + 1));
  }
  const skillUpBlock = (s) => (rankOf(s) >= MAX_RANK ? `Rank ${MAX_RANK} is the most at creation` : short(rankPrice(s, rankOf(s) + 1)));
  const nextTier = (def, owned) => (owned ? Math.min(5, def.tier + owned.rank) : def.tier);
  function talentBuyBlock(def) {
    const owned = D.talents.find((t) => t.id === def.id);
    if (owned && !def.ranked) return "Already owned";
    const tier = nextTier(def, owned), c = tierCounts(D.talents);
    if (tier > 1 && !(c[tier - 1] > c[tier] + 1)) { const need = c[tier] + 2; return `Needs ${need} tier ${tier - 1} talents first (you have ${c[tier - 1]})`; }
    return short(5 * tier);
  }
  function talentDropBlock(id) {
    const after = D.talents.map((t) => (t.id === id ? { ...t, rank: t.rank - 1 } : t)).filter((t) => t.rank > 0);
    return pyramidOk(tierCounts(after)) ? null : `Remove a tier ${slotTiers(D.talents.find((t) => t.id === id)).pop() + 1} talent first`;
  }
  const freePickBlock = (s, extra) => (freeRanks(s) + extra > MAX_RANK ? `Would put ${s} above rank ${MAX_RANK}` : null);

  // ---------- Validation ----------
  function issues() {
    const out = [], add = (step, level, text, tab) => out.push({ step, level, text, tab });
    const a = arch(), c = career(), f = a && a.free;
    if (!a) add(0, "block", "Choose an archetype");
    if (!c) add(1, "block", "Choose a career");
    else if (D.careerPicks.length < 4) add(1, "block", `Pick ${4 - D.careerPicks.length} more career skill${D.careerPicks.length === 3 ? "" : "s"} for a free rank`);
    if (f && f.choose && D.archPicks.length < f.choose) add(1, "block", `Pick ${f.choose - D.archPicks.length} more skill${f.choose - D.archPicks.length > 1 ? "s" : ""} for your ${a.name} free ranks`);
    if (!D.ob.type) add(S_OB, "block", "Pick or roll a Secret or Obligation");
    else if (!D.ob.attachment.trim()) add(S_OB, "block", "Say who or what your Obligation ties you to");
    else if (!D.ob.why.trim()) add(S_OB, "warn", "Say why your Obligation matters to your character");
    if (a && xpLeft() < 0) add(S_XP, "block", `Over the XP budget by ${-xpLeft()}`);
    CHAR_KEYS.filter((k) => charVal(k) > MAX_CHAR).forEach((k) => add(S_XP, "block", `${CHAR_NAMES[k]} is ${charVal(k)}. The most is ${MAX_CHAR}`, "chars"));
    ALL_SKILLS.filter((s) => rankOf(s) > MAX_RANK).forEach((s) => add(S_XP, "block", `${s} is rank ${rankOf(s)}. The most at creation is ${MAX_RANK}`, "skills"));
    if (!pyramidOk(tierCounts(D.talents))) add(S_XP, "block", "Talent pyramid is broken", "talents");
    if (a && xpLeft() > 0) add(S_XP, "warn", `${xpLeft()} XP unspent. You keep it, but characteristics can only be bought now`);
    const d = derived();
    if (creditsLeft() < 0) add(S_GEAR, "block", `Over budget by ${-creditsLeft()} credits`);
    if (a && a.freeItem && !D.freeItem) add(S_GEAR, "warn", `Choose your free ${a.freeItem.group === "G-Mods" ? "g-mod" : "cybernetic"} (${a.name})`);
    if (!D.gear.length) add(S_GEAR, "warn", "No gear bought yet");
    if (D.pocket === null) add(S_GEAR, "warn", "Pocket money not rolled yet");
    if (d.enc > d.encMax) add(S_GEAR, "warn", `Encumbered by ${d.enc - d.encMax}: Brawn and Agility checks add ${d.enc - d.encMax} Setback`);
    if (!D.name.trim()) add(S_STORY, "block", "Give your character a name");
    const empty = MOTIVES.filter(([k]) => !D.motives[k].name.trim()).length;
    if (empty) add(S_STORY, "warn", `${empty} motivation${empty > 1 ? "s" : ""} left blank`);
    if (!D.s0.trim()) add(S_STORY, "warn", "Session 0 question not answered yet");
    return out.sort((x, y) => (x.level === y.level ? x.step - y.step : x.level === "block" ? -1 : 1));
  }
  const blocks = (list) => list.filter((i) => i.level === "block");

  // ---------- Header, step rail, preview, dock ----------
  const initials = (n) => n.trim().split(/\s+/).map((w) => w[0]).join("").slice(0, 2).toUpperCase() || "?";
  function renderHeader() {
    if (document.activeElement !== $("cname")) $("cname").value = D.name;
    $("portrait").textContent = initials(D.name);
    $("csub").textContent = [arch() && arch().name, career() && career().name, D.tagline].filter(Boolean).join(" · ") || "New character";
  }
  function stepSub(i) {
    const a = arch(), c = career(), mot = MOTIVES.filter(([k]) => D.motives[k].name.trim()).length, ob = obDef();
    return [a ? a.name : "Not chosen", c ? `${c.name} · ${D.careerPicks.length}/4 picks` : "Not chosen",
      ob ? `${ob.name} · magnitude ${D.ob.magnitude}` : "Not chosen", a ? `${spentXP()} of ${budget()} XP` : "Needs an archetype",
      `${creditsLeft()} credits left`, `${mot} of 4 motivations`, D.created ? "Created" : `${blocks(issues()).length} to fix`][i];
  }
  function renderSteps() {
    const list = issues();
    $("steps").innerHTML = STEPS.map(([, label], i) => {
      const blocked = list.some((x) => x.step === i && x.level === "block");
      const state = i === S_REVIEW ? (D.created ? "ok" : "") : blocked && U.visited.has(i) ? "issue" : U.visited.has(i) && !blocked ? "ok" : "";
      const mark = state === "ok" ? "✓" : state === "issue" ? "!" : i + 1;
      return `<li><button type="button" class="${state}" data-go="${i}" ${U.step === i ? 'aria-current="step"' : ""}
        aria-label="Step ${i + 1}, ${label}${state === "ok" ? ", done" : state === "issue" ? ", needs attention" : ""}">
        <span class="n" aria-hidden="true">${mark}</span><span><span class="t">${label}</span><span class="s">${esc(stepSub(i))}</span></span></button></li>`;
    }).join("");
  }
  const statline = (chars, hiFrom) => `<div class="statline">${CHAR_KEYS.map((k) => `<div class="${chars[k] > hiFrom ? "hi" : ""}"><b>${chars[k]}</b><span>${k}</span></div>`).join("")}</div>`;
  function budgetHTML() {
    const b = budget(), s = spentXP(), max = Math.max(b, s, 1), left = b - s;
    const seg = (cls, n) => `<i class="${cls}" style="width:${(n / max) * 100}%"></i>`;
    return `<div class="budget"><div class="row"><span class="label">XP left</span><span class="of">${s} of ${b} spent</span></div>
      <b class="${left < 0 ? "neg" : ""}">${left}</b>
      <div class="bar${left < 0 ? " over" : ""}" aria-hidden="true">${seg("c", charsXP())}${seg("s", skillsXP())}${seg("t", talentsXP())}</div>
      <div class="barkey"><span><i style="background:var(--accent)"></i>Characteristics ${charsXP()}</span><span><i style="background:color-mix(in srgb, var(--accent) 55%, var(--surface))"></i>Skills ${skillsXP()}</span><span><i style="background:var(--free)"></i>Talents ${talentsXP()}</span></div>
      ${obXP() ? `<span class="note" style="margin:0">Includes +${obXP()} XP from your Obligation's magnitude</span>` : ""}</div>`;
  }
  function issuesHTML(list) {
    if (!list.length) return `<p class="allgood">Everything checks out. Ready to create.</p>`;
    return `<div class="issues">${list.map((x) => `<button type="button" class="issue-row ${x.level}" data-go="${x.step}"${x.tab ? ` data-tab="${x.tab}"` : ""}><span>${esc(x.text)}</span></button>`).join("")}</div>`;
  }
  function previewHTML() {
    const d = derived(), list = issues(), chars = Object.fromEntries(CHAR_KEYS.map((k) => [k, charVal(k)]));
    return `${budgetHTML()}${arch() ? statline(chars, 99) : '<p class="note" style="margin:0">Choose an archetype to see characteristics.</p>'}
      <div class="derived">
        <div><b>${d.wounds}</b><small>Wound threshold</small></div><div><b>${d.strain}</b><small>Strain threshold</small></div>
        <div><b>${d.soak}</b><small>Soak</small></div><div><b>${d.def} / ${d.def}</b><small>Defense melee / ranged</small></div>
        <div><b>${d.enc}<span style="font-size:15px;color:var(--muted)"> / ${d.encMax}</span></b><small>Encumbrance</small></div>
        <div><b class="${creditsLeft() < 0 ? "neg" : ""}">${creditsLeft() + (D.pocket || 0)}</b><small>Credits${D.pocket !== null ? " incl. pocket money" : ""}</small></div></div>
      <div><span class="label">${blocks(list).length ? `${blocks(list).length} to fix` : "Checks"}</span>${issuesHTML(list)}</div>`;
  }
  function renderPreview() {
    $("preview").innerHTML = `<h2>Preview</h2>${previewHTML()}`;
    if (U.sheet) $("sheet").innerHTML = `<div class="grab" aria-hidden="true"></div><div class="preview">
      <div style="display:flex;justify-content:space-between;align-items:center"><h2>Preview</h2><button type="button" class="x" data-act="sheet-close" aria-label="Close preview">×</button></div>${previewHTML()}</div>`;
  }
  function renderDock() {
    const left = xpLeft(), last = U.step === STEPS.length - 1;
    const next = last ? `<button type="button" class="btn primary" data-act="create" ${blocks(issues()).length || D.created ? "disabled" : ""}>Create</button>`
      : `<button type="button" class="btn primary" data-act="next">Next: ${STEPS[U.step + 1][1]}</button>`;
    $("dock").innerHTML = `<button type="button" class="xpchip" data-act="sheet-open" aria-label="Open preview. ${left} XP left.">
        <b class="${left < 0 ? "neg" : ""}">${arch() ? left : "–"} XP</b><small>Preview · ${blocks(issues()).length} to fix</small></button>
      <div class="spacer"></div><button type="button" class="btn" data-act="back" ${U.step === 0 ? "disabled" : ""}>Back</button>${next}`;
  }

  // ---------- Steps ----------
  const head = (title, text) => `<div class="stephead"><h2 id="step-title" tabindex="-1">${title}</h2><p>${text}</p></div>`;
  const stepNav = () => `<div class="stepnav"><button type="button" class="btn big" data-act="back" ${U.step === 0 ? "disabled" : ""}>Back</button>
      ${U.step === STEPS.length - 1 ? "" : `<button type="button" class="btn primary big" data-act="next">Next: ${STEPS[U.step + 1][1]}</button>`}</div>`;
  const freeText = (a) => {
    const f = a.free;
    if (!f) return "No free skill ranks";
    if (f.choose) return `${f.ranks === 2 ? "Two ranks" : "One rank"} in each of ${f.choose} ${f["non-career"] ? "non-career " : ""}skills you choose`;
    return `${f.ranks === 2 ? "Two ranks" : "One rank"} in ${f.skills.join(", ")}`;
  };

  function stepArchetype() {
    const cards = ARCHETYPES.map((a) => `<button type="button" class="choice" data-arch="${a.id}" aria-pressed="${D.archetype === a.id}">
      <h3>${a.name} ${cite(a.source)}</h3>${statline(a.chars, 2)}
      <div class="kvline"><span>Wounds <b>${a.wounds}</b> + Br</span><span>Strain <b>${a.strain}</b> + Will</span><span>Starting XP <b>${a.xp}</b></span></div>
      ${a.abilities.map((ab) => `<div class="ability"><b>${esc(ab.name)}.</b> ${rich(ab.text)}</div>`).join("")}
      <div class="meta">${freeText(a)}${a.freeItem ? ` · a free ${a.freeItem.group === "G-Mods" ? "g-mod" : "cybernetic"} up to ${a.freeItem["max-price"].toLocaleString()} credits` : ""}</div></button>`).join("");
    return head("Choose an archetype", "Your archetype sets your starting characteristics, thresholds and XP. You can switch later; anything you've bought stays and is repriced.")
      + `<div class="choices">${cards}</div>`;
  }

  const pickHTML = (s, on, block, kind) => `<button type="button" class="pick" data-${kind}="${esc(s)}" aria-pressed="${on}" ${block && !on ? `disabled title="${esc(block)}"` : ""}>
      <span class="box" aria-hidden="true"></span><span class="nm">${s}<i>${STAT_OF[s] || ""}</i></span></button>`;
  function stepCareer() {
    const a = arch(), c = career(), f = a && a.free;
    const cards = CAREERS.map((x) => `<button type="button" class="choice" data-career="${x.id}" aria-pressed="${D.career === x.id}">
      <h3>${x.name} ${cite(x.source)}</h3><div class="skillchips">${x.skills.map((s) => `<span class="tag">${s}</span>`).join("")}</div>
      ${(x["useful-talents"] || []).length ? `<div class="meta">Suggested talents: ${x["useful-talents"].map((t) => t.name).join(", ")}</div>` : ""}</button>`).join("");
    let html = head("Choose a career", "Career skills cost 5 XP less per rank. Pick four of them to start with a free rank (CRB p.40).") + `<div class="choices">${cards}</div>`;
    if (c) {
      const n = D.careerPicks.length;
      html += `<div class="sect"><h3>Free career ranks <span class="count${n === 4 ? " done" : ""}">${n} of 4</span></h3>
        <div class="picks">${c.skills.map((s) => pickHTML(s, D.careerPicks.includes(s), n >= 4 ? "Four picked" : freePickBlock(s, 1), "cpick")).join("")}</div></div>`;
    }
    if (f && f.choose) {
      const n = D.archPicks.length, pool = ALL_SKILLS.filter((s) => !f["non-career"] || !isCareer(s));
      html += `<div class="sect"><h3>${a.name}: ${freeText(a).toLowerCase()} <span class="count${n === f.choose ? " done" : ""}">${n} of ${f.choose}</span></h3>
        ${c || !f["non-career"] ? `<div class="picks">${pool.map((s) => pickHTML(s, D.archPicks.includes(s), n >= f.choose ? `${f.choose} picked` : freePickBlock(s, f.ranks), "apick")).join("")}</div>`
        : '<p class="note">Choose a career first. These must be skills outside it.</p>'}</div>`;
    } else if (f) html += `<p class="note">${a.name} also gets ${freeText(a).toLowerCase()}.</p>`;
    return html + `<p class="note" style="margin:0">No skill can be above rank ${MAX_RANK} when creation ends (CRB p.44).</p>`;
  }

  function stepObligation() {
    const ob = obDef();
    const cards = OBLIGATIONS.map((o) => `<button type="button" class="choice" data-ob="${o.id}" aria-pressed="${D.ob.type === o.id}">
      <h3>${o.name}</h3><div class="kvline"><span class="tag${o.kind === "secret" ? " gm" : ""}">${o.kind === "secret" ? "Secret" : "Obligation"}</span><span>d100 <b>${String(o.roll[0]).padStart(2, "0")}–${o.roll[1] === 100 ? "00" : o.roll[1]}</b></span></div>
      <p>${esc(o.text)}</p></button>`).join("");
    return head("Secret or Obligation", "Everyone on the Concord carries something. Pick one or roll for it, choose how heavy it is, then say who or what it ties you to.")
      + `<div class="toolbar"><button type="button" class="btn primary" data-act="ob-roll">Roll d100</button>${D.ob.rolled ? `<span class="note" style="margin:0">Rolled ${D.ob.rolled}</span>` : ""}</div>
      <div class="choices">${cards}</div>
      <div class="card debt">
        <div><span class="label">Magnitude</span><div class="seg" role="radiogroup" aria-label="Magnitude">${MAGNITUDES.map((m) =>
          `<button type="button" role="radio" data-mag="${m}" aria-selected="${D.ob.magnitude === m}" aria-checked="${D.ob.magnitude === m}">${m}<span class="c">+${m} XP</span></button>`).join("")}</div>
          <p class="note">A heavier ${ob && ob.kind === "secret" ? "Secret" : "Obligation"} gives more starting XP, and comes due more often.</p></div>
        <div><label class="label" for="ob-attach">Who or what it ties you to</label><input type="text" id="ob-attach" value="${esc(D.ob.attachment)}" placeholder="A person, a corp, a thing" autocomplete="off">
          <div class="sugg" style="margin-top:6px">${CORPS.map((c) => `<button type="button" class="chip" data-attach="${esc(c)}" aria-pressed="${D.ob.attachment === c}">${c}</button>`).join("")}</div></div>
        <div><label class="label" for="ob-why">Why it matters to them</label><textarea id="ob-why" placeholder="One sentence the GM can build a scene on">${esc(D.ob.why)}</textarea></div>
        <div class="rulebox" style="border-color:var(--line)"><b style="color:var(--ink)">At the table.</b> Each session the GM rolls d100 against the party's total magnitude.
          If it lands in your range, your ${ob && ob.kind === "secret" ? "Secret" : "Obligation"} comes due: the attachment shows up that session, you get −2 strain threshold and everyone else −1 (doubled on doubles). ${cite({ book: "house" })}</div></div>`;
  }

  function charsTab() {
    return `<div class="chars">${CHAR_KEYS.map((k) => {
      const v = charVal(k), up = charUpBlock(k), bought = D.charBuy[k] || 0, line = up || `Next: ${10 * (v + 1)} XP`;
      return `<div class="char${bought ? " raised" : ""}"><span class="label">${CHAR_NAMES[k]}</span><b>${v}</b>
        <span class="base">Base ${baseChar(k)}${bought ? ` · +${bought} bought` : ""}</span>
        <div class="ctl"><button type="button" class="btn step" data-char-down="${k}" ${bought ? "" : "disabled"} aria-label="Lower ${CHAR_NAMES[k]}, refund ${10 * v} XP">&minus;</button>
        <button type="button" class="btn step" data-char-up="${k}" ${up ? "disabled" : ""} aria-label="Raise ${CHAR_NAMES[k]}. ${esc(line)}">+</button></div>
        <span class="cost">${esc(line)}</span></div>`;
    }).join("")}</div><p class="note">A raise costs ten times the new rating, and this is the only time characteristics can be bought with XP (CRB p.44).</p>`;
  }
  const mini = (p) => ["proficiency", "ability"].map((t) => Array.from({ length: p[t] }, () => dieSVG(t, null, DICE[t].name)).join("")).join("");
  function skillRow(s) {
    const free = freeRanks(s), rk = rankOf(s), bought = D.skillBuy[s] || 0, car = isCareer(s), up = skillUpBlock(s);
    const pips = Array.from({ length: 5 }, (_, i) => `<i class="${i < free ? "free" : i < rk ? "on" : i >= MAX_RANK ? "cap" : ""}"></i>`).join("");
    const why = [car ? "Career" : "Non-career", free ? `${free} free` : "", rk < MAX_RANK ? up || `next rank ${rankPrice(s, rk + 1)} XP` : ""].filter(Boolean).join(" · ");
    return `<div class="srow"><span class="nm"><span class="dot${car ? "" : " off"}" aria-hidden="true"></span>${s}<i>${STAT_OF[s]}</i><span class="why">${esc(why)}</span></span>
      <span class="ranks" aria-label="Rank ${rk}${free ? `, ${free} free` : ""}">${pips}</span><span class="mini">${mini(skillPool(s))}</span>
      <span class="sctl"><button type="button" class="btn step" data-skill-down="${esc(s)}" ${bought ? "" : "disabled"} aria-label="Remove a rank of ${s}">&minus;</button>
      <button type="button" class="btn step" data-skill-up="${esc(s)}" ${up ? "disabled" : ""} aria-label="Buy a rank of ${s}${up ? `. ${esc(up)}` : ""}">+</button></span></div>`;
  }
  function skillsTab() {
    const q = U.q.trim().toLowerCase();
    const rows = SKILLS.map(([group, list]) => {
      const hits = list.filter(([n]) => (!q || n.toLowerCase().includes(q)) && (!U.careerOnly || isCareer(n)));
      return hits.length ? `<div class="sgroup"><span class="label">${group}</span>${hits.map(([n]) => skillRow(n)).join("")}</div>` : "";
    }).join("");
    return `<div class="toolbar"><label class="sr" for="q">Filter skills</label><input id="q" type="search" placeholder="Filter skills…" value="${esc(U.q)}" autocomplete="off">
        <label class="toggle"><input type="checkbox" id="careeronly" ${U.careerOnly ? "checked" : ""}> Career skills only</label></div>
      <div class="legend"><span class="ranks"><i class="free"></i></span>Free rank <span class="ranks"><i class="on"></i></span>Bought <span class="ranks"><i class="cap"></i></span>Locked until after creation <span><span class="dot"></span>Career skill</span></div>
      <div>${rows || `<p class="note">No skill matches “${esc(U.q)}”.</p>`}</div><p class="note">The 31 skills used in the Android setting ${cite({ book: "sotb", page: 42 })}.</p>`;
  }
  function pyramidHTML() {
    const c = tierCounts(D.talents), ok = pyramidOk(c);
    const rows = [5, 4, 3, 2, 1].map((t) => `<div class="tier"><span>Tier ${t}</span>${Array.from({ length: Math.max(c[t], 6 - t) }, (_, i) => `<i class="slot${i < c[t] ? " on" : ""}"></i>`).join("")}</div>`).join("");
    return `<div class="pyramid" aria-label="Talent pyramid: ${[1, 2, 3, 4, 5].map((t) => `tier ${t}: ${c[t]}`).join(", ")}">${rows}
      <p class="pyr-msg ${ok ? "ok" : "no"}">${ok ? "Valid. Each tier needs more talents than the tier above it." : "Broken. Each tier needs more talents than the tier above it."}</p></div>`;
  }
  function talentCard(def) {
    const owned = D.talents.find((t) => t.id === def.id), buy = talentBuyBlock(def), tier = nextTier(def, owned);
    const drop = owned ? talentDropBlock(def.id) : null, showBuy = !owned || def.ranked;
    const suggested = career() && (career()["useful-talents"] || []).some((t) => t.id === def.id);
    return `<div class="talent${owned ? " owned" : ""}"><div><h4>${def.name}${owned && def.ranked ? ` <span class="rk">rank ${owned.rank}</span>` : ""}
        <span class="tag">Tier ${def.tier}</span><span class="tag">${esc(def.activation)}</span>${def.ranked ? '<span class="tag">Ranked</span>' : ""}${suggested ? '<span class="tag sugg-tag">Suggested for your career</span>' : ""}${cite(def.source)}</h4><p>${rich(def.text)}</p></div>
      <div class="acts">${owned ? `<button type="button" class="btn" data-tdrop="${def.id}" ${drop ? `disabled title="${esc(drop)}"` : ""}>Remove${def.ranked && owned.rank > 1 ? " a rank" : ""}</button>` : ""}
        ${showBuy ? `<button type="button" class="btn${owned ? "" : " primary"}" data-tbuy="${def.id}" ${buy ? "disabled" : ""}>${owned ? `+ Rank ${owned.rank + 1} · ${5 * tier} XP` : `Buy · ${5 * tier} XP`}</button>` : ""}</div>
      ${showBuy && buy ? `<div class="block">${esc(buy)}</div>` : drop ? `<div class="block">${esc(drop)}</div>` : ""}</div>`;
  }
  function talentsTab() {
    const q = U.tq.trim().toLowerCase(), mine = (t) => !!D.talents.find((x) => x.id === t.id);
    const useful = new Set(((career() || {})["useful-talents"] || []).map((t) => t.id));
    const list = TALENTS.filter((t) => (!U.tier || t.tier === U.tier) && (!q || t.name.toLowerCase().includes(q) || (t.text || "").toLowerCase().includes(q)))
      .sort((a, b) => mine(b) - mine(a) || useful.has(b.id) - useful.has(a.id) || a.tier - b.tier || a.name.localeCompare(b.name));
    const shown = list.slice(0, 40);
    return pyramidHTML() + `<div class="toolbar"><label class="sr" for="tq">Search talents</label><input id="tq" type="search" placeholder="Search ${TALENTS.length} talents…" value="${esc(U.tq)}" autocomplete="off"></div>
      <div class="filters" role="group" aria-label="Filter by tier">${[0, 1, 2, 3, 4, 5].map((t) => `<button type="button" class="chip" data-tier="${t}" aria-pressed="${U.tier === t}">${t ? `Tier ${t}` : "All tiers"}</button>`).join("")}</div>
      <div class="tlist">${shown.map(talentCard).join("") || `<p class="note">No talent matches.</p>`}</div>
      ${list.length > shown.length ? `<p class="note">Showing ${shown.length} of ${list.length}. Search or pick a tier to narrow it down.</p>` : ""}
      <p class="note">A talent costs 5 XP per tier (CRB p.45). These are the talents allowed in the Android setting ${cite({ book: "sotb", page: 46 })}.</p>`;
  }
  function stepXP() {
    if (!arch()) return head("Spend XP", "Choose an archetype first. It sets your XP budget.") + `<button type="button" class="btn primary" data-go="0">Go to Archetype</button>`;
    const tabs = [["chars", "Characteristics", charsXP()], ["skills", "Skills", skillsXP()], ["talents", "Talents", talentsXP()]];
    return head("Spend XP", `You have <b class="mono">${xpLeft()}</b> of ${budget()} XP left: ${arch().xp} from ${arch().name}${obXP() ? ` and ${obXP()} from your Obligation` : ""}. Prices update as you go, and you can undo any purchase.`)
      + `<div class="seg" role="tablist" aria-label="What to spend XP on">${tabs.map(([id, label, n]) => `<button type="button" role="tab" data-xptab="${id}" aria-selected="${U.xpTab === id}">${label}<span class="c">${n}</span></button>`).join("")}</div>
      <div role="tabpanel">${{ chars: charsTab, skills: skillsTab, talents: talentsTab }[U.xpTab]()}</div>`;
  }

  const gearDetail = (g) => g.kind === "weapon"
    ? `${g.skill} · Dmg ${g["adds-brawn"] ? `+${g["damage-bonus"]}` : g.damage} · Crit ${g.crit} · ${g.range}${(g.qualities || []).length ? ` · ${g.qualities.map((q) => q.name + (q.rating ? " " + q.rating : "")).join(", ")}` : ""}`
    : g.kind === "armor" ? `Soak +${g.soak} · Defense ${g.defense}` : g["encumbrance-bonus"] ? `Carry +${g["encumbrance-bonus"]}` : "";
  function catalogHTML(left) {
    const q = U.gq.trim().toLowerCase();
    const items = GEAR.filter((g) => g.kind === U.gearTab && (!q || g.name.toLowerCase().includes(q) || (g.group || "").toLowerCase().includes(q)));
    const groups = [...new Set(items.map((g) => g.group))];
    return groups.map((grp) => `<div class="sect"><h3>${esc(grp)}</h3><div class="glist">${items.filter((g) => g.group === grp).map((g) => {
      const have = D.gear.find((x) => x.id === g.id), single = g.kind !== "gear", canBuy = (g.price || 0) <= left && !(have && single);
      return `<div class="gitem"><div><h4>${esc(g.name)}${g.restricted ? ' <span class="tag">Restricted</span>' : ""}</h4><div class="meta">${esc(gearDetail(g))}${gearDetail(g) ? " · " : ""}Enc ${g.encumbrance || 0} · Rarity ${g.rarity ?? "–"} ${cite(g.source)}</div></div>
        <div class="acts"><span class="price">${(g.price || 0).toLocaleString()}</span><button type="button" class="btn" data-gbuy="${g.id}" ${canBuy ? "" : "disabled"}>${have && single ? "Bought" : "Buy"}</button></div></div>`;
    }).join("")}</div></div>`).join("") || `<p class="note">Nothing matches “${esc(U.gq)}”.</p>`;
  }
  function freeItemHTML() {
    const a = arch(), fi = a && a.freeItem;
    if (!fi) return "";
    const options = GEAR.filter((g) => g.group === fi.group && (g.price || 0) <= fi["max-price"]);
    const label = fi.group === "G-Mods" ? "g-mod" : "cybernetic";
    return `<div class="card s0"><div><span class="tag">${a.name}</span></div><div class="q">Your free ${label}, worth up to ${fi["max-price"].toLocaleString()} credits</div>
      <div class="sugg">${options.map((g) => `<button type="button" class="chip" data-free="${g.id}" aria-pressed="${D.freeItem === g.id}">${esc(g.name)} · ${g.price.toLocaleString()}</button>`).join("")}</div>
      <p class="note" style="margin:0">It doesn't come out of your 500 credits.</p></div>`;
  }
  function stepGear() {
    const d = derived(), left = creditsLeft(), mine = carried();
    const inv = mine.length ? mine.map((g) => `<div class="gitem"><div><h4>${esc(g.name)}${g.qty > 1 ? ` <span class="mono" style="font-weight:400">×${g.qty}</span>` : ""}</h4>
        <div class="meta">${g.free ? `Free (${arch().name}) · ` : ""}${g.kind === "armor" ? (bestArmor() && g.id === bestArmor().id ? "Worn · " : "Carried, not worn · ") : ""}Enc ${(g.encumbrance || 0) * g.qty}${g.free ? "" : ` · ${((g.price || 0) * g.qty).toLocaleString()} credits`}</div></div>
      <div class="acts">${g.free ? "" : `${g.kind === "gear" ? `<button type="button" class="btn step" data-gbuy="${g.id}" ${(g.price || 0) <= left ? "" : "disabled"} aria-label="Add one more ${esc(g.name)}">+</button>` : ""}
        <button type="button" class="btn" data-gdrop="${g.id}">${g.qty > 1 ? "−1" : "Remove"}</button>`}</div></div>`).join("")
      : `<div class="empty">Nothing yet. Buy from the catalog. Credits you don't spend stay on your sheet.</div>`;
    const tabs = [["weapon", "Weapons"], ["armor", "Armor"], ["gear", "Gear"]];
    return head("Buy starting gear", `You start with ${START_CREDITS} credits (CRB p.51). Leftover credits stay on your sheet. After shopping, roll d100 for pocket money.`)
      + `<div class="money"><div><span class="label">Credits left</span><br><b class="${left < 0 ? "neg" : ""}">${left}</b> <span class="note">of ${START_CREDITS}</span></div>
        <div class="enc"><span class="label">Encumbrance ${d.enc} / ${d.encMax}</span><div class="enc-bar${d.enc > d.encMax ? " over" : ""}"><i style="width:${Math.min(100, (d.enc / d.encMax) * 100)}%"></i></div>
        <span class="note" style="margin:0">Threshold 5 + Brawn ${charVal("Br")}${d.encBonus ? ` + ${d.encBonus} carried` : ""}</span></div>
        <div><span class="label">Pocket money</span><br>${D.pocket === null ? `<button type="button" class="btn" data-act="pocket">Roll d100</button>` : `<b>+${D.pocket}</b> <span class="note">can't buy starting gear</span>`}</div></div>
      ${freeItemHTML()}
      <div class="gearcols"><div><div class="toolbar"><div class="seg" role="tablist" aria-label="Catalog">${tabs.map(([id, l]) => `<button type="button" role="tab" data-geartab="${id}" aria-selected="${U.gearTab === id}">${l}<span class="c">${GEAR.filter((g) => g.kind === id).length}</span></button>`).join("")}</div>
          <label class="sr" for="gq">Search the catalog</label><input id="gq" type="search" placeholder="Search…" value="${esc(U.gq)}" autocomplete="off"></div>${catalogHTML(left)}</div>
        <div class="sect"><h3>Your gear <span class="count">${mine.reduce((n, g) => n + g.qty, 0)} items</span></h3><div class="glist">${inv}</div>
          <p class="note">You wear one armor at a time. The best one counts for soak and defense.</p></div></div>`;
  }

  function stepStory() {
    const cards = MOTIVES.map(([k, hint]) => {
      const m = D.motives[k], id = k.toLowerCase(), table = MOTIVATION_TABLE.filter((x) => x.type === k);
      return `<div class="card motive"><div class="hd"><span class="label">${k}</span><span class="q">${hint}</span><button type="button" class="linkbtn" data-mroll="${k}">Roll d100</button></div>
        <label class="sr" for="m-${id}">${k} in a word or two</label><input type="text" id="m-${id}" data-motive="${k}" data-field="name" value="${esc(m.name)}" placeholder="${k} in a word or two" autocomplete="off">
        <div class="sugg" aria-label="From the Core Rulebook">${table.map((x) => `<button type="button" class="chip" data-suggest="${k}" data-val="${esc(x.name)}" aria-pressed="${m.name === x.name}" title="${esc(x.text)}">${esc(x.name)}</button>`).join("")}</div>
        <label class="sr" for="mt-${id}">${k}: what it means for them</label><textarea id="mt-${id}" data-motive="${k}" data-field="text" placeholder="What it means for them, in a sentence">${esc(m.text)}</textarea></div>`;
    }).join("");
    return head("Motivations and Session 0", "Motivations tell the GM what drives your character. Pick from the book's tables, roll, or write your own (CRB p.46–50).")
      + `<div><label class="label" for="tagline">One-line description</label><input type="text" id="tagline" value="${esc(D.tagline)}" placeholder="e.g. Dock mechanic, Concord berth 9" autocomplete="off"></div>
      <div class="motives">${cards}</div>
      <div class="card s0"><div><span class="tag gm">Session 0 · from your GM</span></div><div class="q">${SESSION0}</div>
        <label class="sr" for="s0">Your answer</label><textarea id="s0" placeholder="Your answer">${esc(D.s0)}</textarea>
        <p class="note" style="margin:0">Only you and the GM see this answer. The GM has tied the question to a later act.</p></div>`;
  }

  function reviewCards() {
    const a = arch(), c = career(), d = derived(), ob = obDef(), chars = Object.fromEntries(CHAR_KEYS.map((k) => [k, charVal(k)]));
    const ranked = ALL_SKILLS.filter((s) => rankOf(s)).map((s) => `${s} ${rankOf(s)}`);
    const card = (title, step, body) => `<div class="card rev"><h3>${title}<button type="button" class="linkbtn" data-go="${step}">Edit</button></h3>${body}</div>`;
    return `<div class="revgrid">
      ${card("Identity", 0, `<p><b>${esc(D.name || "Unnamed")}</b>${D.tagline ? `, ${esc(D.tagline)}` : ""}</p><p>${a ? a.name : "No archetype"} · ${c ? c.name : "No career"}</p>${a ? `<p class="note" style="margin:0">${a.abilities.map((x) => esc(x.name)).join(", ")}</p>` : ""}`)}
      ${card("Secret or Obligation", S_OB, ob ? `<p><b>${ob.name}</b>, magnitude <b class="mono">${D.ob.magnitude}</b></p><p>Tied to ${esc(D.ob.attachment || "nobody yet")}</p>${D.ob.why ? `<p class="note" style="margin:0">${esc(D.ob.why)}</p>` : ""}` : "<p>Not chosen.</p>")}
      ${card("Characteristics", S_XP, `${statline(chars, 99)}<p class="note" style="margin:0">Wounds ${d.wounds} · Strain ${d.strain} · Soak ${d.soak} · Defense ${d.def}</p>`)}
      ${card("Skills", S_XP, ranked.length ? `<p>${ranked.join(", ")}</p>` : "<p>No ranks yet.</p>")}
      ${card("Talents", S_XP, D.talents.length ? `<ul>${D.talents.map((t) => `<li>${talentDef(t.id).name}${talentDef(t.id).ranked ? ` ${t.rank}` : ""}</li>`).join("")}</ul>` : "<p>None yet.</p>")}
      ${card("Gear", S_GEAR, `${carried().length ? `<ul>${carried().map((g) => `<li>${esc(g.name)}${g.qty > 1 ? ` ×${g.qty}` : ""}${g.free ? " (free)" : ""}</li>`).join("")}</ul>` : "<p>Nothing bought.</p>"}<p class="note" style="margin:0">${creditsLeft() + (D.pocket || 0)} credits${D.pocket !== null ? ` (incl. ${D.pocket} pocket money)` : ""}</p>`)}
      ${card("Motivations", S_STORY, `<ul>${MOTIVES.map(([k]) => `<li><b>${k}:</b> ${esc(D.motives[k].name || "blank")}</li>`).join("")}</ul>`)}</div>`;
  }
  function stepReview() {
    if (D.created) return `<div class="card done"><span class="label">Character created</span><h2>${esc(D.name)} is ready</h2>
      <p>Saved to your characters and shared with the GM of Manifest Destiny. Unspent XP and credits are on the sheet.</p>
      <div class="acts"><button type="button" class="btn primary big" data-act="open-sheet">Open character sheet</button><button type="button" class="btn big" data-act="uncreate">Keep editing</button></div></div>` + reviewCards();
    const list = issues(), nBlock = blocks(list).length;
    const checks = list.length ? list.map((x) => `<div class="check ${x.level}"><span class="i" aria-hidden="true">${x.level === "block" ? "!" : "i"}</span>
        <span>${esc(x.text)}<span class="sr">${x.level === "block" ? " (must fix)" : " (optional)"}</span></span>
        <button type="button" class="linkbtn" data-go="${x.step}"${x.tab ? ` data-tab="${x.tab}"` : ""}>${STEPS[x.step][1]}</button></div>`).join("")
      : `<div class="check ok"><span class="i" aria-hidden="true">✓</span><span>Everything checks out.</span><span></span></div>`;
    return head("Review", "Fix anything marked red, then create your character. Yellow items are fine to leave for now.")
      + `<div class="checks">${checks}</div>${reviewCards()}
      <div class="stepnav" style="display:flex"><button type="button" class="btn big" data-act="back">Back</button>
        <span style="display:flex;gap:10px;align-items:center;flex-wrap:wrap;justify-content:flex-end">${nBlock ? `<span class="note" style="margin:0">${nBlock} thing${nBlock > 1 ? "s" : ""} to fix first</span>` : ""}
        <button type="button" class="btn primary big" data-act="create" ${nBlock ? "disabled" : ""}>Create character</button></span></div>`;
  }

  function renderStep(focus) {
    const fn = [stepArchetype, stepCareer, stepObligation, stepXP, stepGear, stepStory, stepReview][U.step];
    $("step").innerHTML = fn() + (U.step === STEPS.length - 1 ? "" : stepNav());
    if (focus) { const h = $("step-title"); if (h) h.focus({ preventScroll: true }); }
  }
  function renderLive() { renderHeader(); renderSteps(); renderPreview(); renderDock(); save(); }
  function renderAll(focus) { renderStep(focus); renderLive(); }

  function go(i, tab) {
    U.visited.add(U.step);
    U.step = Math.max(0, Math.min(STEPS.length - 1, i));
    U.visited.add(U.step);
    if (tab) U.xpTab = tab;
    closeSheet();
    renderAll(true);
    const top = $("step").getBoundingClientRect().top + window.scrollY - 12;
    if (window.scrollY > top) window.scrollTo({ top, behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" });
  }
  function openSheet() { U.sheet = true; $("scrim").hidden = false; $("sheet").hidden = false; renderPreview(); $("sheet").querySelector(".x").focus(); }
  function closeSheet() { U.sheet = false; $("scrim").hidden = true; $("sheet").hidden = true; }

  // ---------- Actions ----------
  function chooseArchetype(id) {
    if (D.archetype === id) return;
    const had = !!D.archetype;
    D.archetype = id; D.archPicks = [];
    if (D.freeItem && (!arch().freeItem || (gearDef(D.freeItem) || {}).group !== arch().freeItem.group)) D.freeItem = null;
    if (had) toast(`Switched to ${arch().name}. Purchases kept and repriced.`);
  }
  function chooseCareer(id) {
    if (D.career === id) return;
    const had = D.careerPicks.length;
    D.career = id; D.careerPicks = [];
    const f = arch() && arch().free;
    if (f && f["non-career"]) D.archPicks = D.archPicks.filter((s) => !isCareer(s));
    if (had) toast("Career changed. Pick your four free ranks again.");
  }
  const toggle = (list, s) => (list.includes(s) ? list.filter((x) => x !== s) : [...list, s]);
  function buyTalent(id) {
    const def = talentDef(id);
    if (talentBuyBlock(def)) return;
    const owned = D.talents.find((t) => t.id === id);
    if (owned) owned.rank++; else D.talents.push({ id, rank: 1 });
    $("live").textContent = `Bought ${def.name}`;
  }
  function dropTalent(id) {
    if (talentDropBlock(id)) return;
    D.talents.find((t) => t.id === id).rank--;
    D.talents = D.talents.filter((t) => t.rank > 0);
  }
  function buyGear(id) {
    const g = gearDef(id), have = D.gear.find((x) => x.id === id);
    if ((g.price || 0) > creditsLeft() || (have && g.kind !== "gear")) return;
    if (have) have.qty++; else D.gear.push({ id, qty: 1 });
  }
  function dropGear(id) {
    D.gear.find((x) => x.id === id).qty--;
    D.gear = D.gear.filter((x) => x.qty > 0);
  }
  function rollObligation() {
    const n = d100(), o = OBLIGATIONS.find((x) => inRoll(n, x.roll));
    D.ob.type = o.id; D.ob.rolled = n;
    toast(`Rolled ${n}: ${o.name}`);
  }
  function rollMotivation(type) {
    const n = d100(), m = MOTIVATION_TABLE.find((x) => x.type === type && inRoll(n === 100 ? 100 : n, x.roll)) || MOTIVATION_TABLE.find((x) => x.type === type);
    D.motives[type] = { name: m.name, text: m.text };
    toast(`${type}: rolled ${n}, ${m.name}`);
  }
  function restart() {
    const b = document.querySelector('[data-act="restart"]');
    if (!U.restartArmed) {
      U.restartArmed = true; b.textContent = "Tap again to clear the draft";
      setTimeout(() => { U.restartArmed = false; b.textContent = "Start over"; }, 3000);
      return;
    }
    U.restartArmed = false; b.textContent = "Start over";
    D = blankDraft(); U.visited = new Set([0]); U.xpTab = "chars"; U.q = ""; U.tq = ""; U.gq = "";
    go(0);
    toast("Draft cleared. Start with an archetype.");
  }
  function create() {
    if (blocks(issues()).length) return;
    D.created = true; U.visited.add(S_REVIEW);
    renderAll(true);
    toast(`${D.name} created`);
  }

  // Each handler returns true when the step body must re-render.
  const CLICK = {
    go: (d) => { go(Number(d.go), d.tab); return false; },
    arch: (d) => (chooseArchetype(d.arch), true),
    career: (d) => (chooseCareer(d.career), true),
    cpick: (d) => { if (D.careerPicks.includes(d.cpick) || (D.careerPicks.length < 4 && !freePickBlock(d.cpick, 1))) D.careerPicks = toggle(D.careerPicks, d.cpick); return true; },
    apick: (d) => { const f = arch().free; if (D.archPicks.includes(d.apick) || (D.archPicks.length < f.choose && !freePickBlock(d.apick, f.ranks))) D.archPicks = toggle(D.archPicks, d.apick); return true; },
    ob: (d) => { D.ob.type = d.ob; D.ob.rolled = null; return true; },
    mag: (d) => { D.ob.magnitude = Number(d.mag); return true; },
    attach: (d) => { D.ob.attachment = d.attach; return true; },
    xptab: (d) => { U.xpTab = d.xptab; return true; },
    geartab: (d) => { U.gearTab = d.geartab; return true; },
    charUp: (d) => { if (!charUpBlock(d.charUp)) D.charBuy[d.charUp] = (D.charBuy[d.charUp] || 0) + 1; return true; },
    charDown: (d) => { if (D.charBuy[d.charDown]) D.charBuy[d.charDown]--; return true; },
    skillUp: (d) => { if (!skillUpBlock(d.skillUp)) D.skillBuy[d.skillUp] = (D.skillBuy[d.skillUp] || 0) + 1; return true; },
    skillDown: (d) => { if (D.skillBuy[d.skillDown]) D.skillBuy[d.skillDown]--; return true; },
    tier: (d) => { U.tier = Number(d.tier); return true; },
    tbuy: (d) => (buyTalent(d.tbuy), true),
    tdrop: (d) => (dropTalent(d.tdrop), true),
    gbuy: (d) => (buyGear(d.gbuy), true),
    gdrop: (d) => (dropGear(d.gdrop), true),
    free: (d) => { D.freeItem = D.freeItem === d.free ? null : d.free; return true; },
    suggest: (d) => { const m = MOTIVATION_TABLE.find((x) => x.type === d.suggest && x.name === d.val); D.motives[d.suggest] = { name: d.val, text: D.motives[d.suggest].text || (m ? m.text : "") }; return true; },
    mroll: (d) => (rollMotivation(d.mroll), true),
  };
  const ACTS = {
    next: () => go(U.step + 1), back: () => go(U.step - 1), create, restart,
    "sheet-open": openSheet, "sheet-close": closeSheet,
    "ob-roll": () => { rollObligation(); renderAll(false); },
    pocket: () => { D.pocket = d100(); toast(`Pocket money: ${D.pocket} credits`); renderAll(false); },
    uncreate: () => { D.created = false; renderAll(true); },
    "open-sheet": () => toast("In the app this opens the character sheet. The sheet prototype is its own page."),
  };
  document.addEventListener("click", (e) => {
    if (e.target.id === "scrim") { closeSheet(); return; }
    const t = e.target.closest("button");
    if (!t || t.disabled) return;
    const d = t.dataset;
    if (d.act && ACTS[d.act]) { ACTS[d.act](); return; }
    const key = Object.keys(CLICK).find((k) => d[k] !== undefined);
    if (!key) return;
    if (D.created && key !== "go") D.created = false;
    if (CLICK[key](d)) { const y = window.scrollY; renderAll(false); window.scrollTo(0, y); }
  });
  // Typing updates the draft and the live panels, but never re-renders the field being typed in.
  const SEARCH = { q: "q", tq: "tq", gq: "gq" };
  document.addEventListener("input", (e) => {
    const t = e.target;
    if (t.id === "cname") D.name = t.value;
    else if (t.id === "tagline") D.tagline = t.value;
    else if (t.id === "s0") D.s0 = t.value;
    else if (t.id === "ob-attach") { D.ob.attachment = t.value; document.querySelectorAll("[data-attach]").forEach((c) => c.setAttribute("aria-pressed", c.dataset.attach === t.value)); }
    else if (t.id === "ob-why") D.ob.why = t.value;
    else if (t.dataset.motive) {
      D.motives[t.dataset.motive][t.dataset.field] = t.value;
      if (t.dataset.field === "name") t.closest(".motive").querySelectorAll("[data-suggest]").forEach((c) => c.setAttribute("aria-pressed", c.dataset.val === t.value));
    } else if (SEARCH[t.id]) {
      U[SEARCH[t.id]] = t.value;
      const pos = t.selectionStart; renderStep(false);
      const f = $(t.id); f.focus(); f.setSelectionRange(pos, pos);
      return;
    } else return;
    renderLive();
  });
  document.addEventListener("change", (e) => { if (e.target.id === "careeronly") { U.careerOnly = e.target.checked; renderStep(false); } });
  document.addEventListener("keydown", (e) => { if (e.key === "Escape" && U.sheet) closeSheet(); });

  renderAll(false);
})();
