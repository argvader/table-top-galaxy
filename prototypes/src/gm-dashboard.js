(() => {
/*DICE_CORE*/
/*DICE_SVG*/
/*CONTENT*/
  // ---------- Library (injected by bb proto-build from content/, Android setting) ----------
  const KEY_OF = { brawn: "Br", agility: "Ag", intellect: "Int", cunning: "Cun", willpower: "Will", presence: "Pr" };
  const CHAR_NAMES = { Br: "Brawn", Ag: "Agility", Int: "Intellect", Cun: "Cunning", Will: "Willpower", Pr: "Presence" };
  const BOOK = { crb: "CRB", sotb: "SotB", "sotb-guns": "Gun Locker", epg: "EPG", house: "House rule" };
  const find = (type, id) => CONTENT[type].find((e) => e.id === id);
  const cite = (src) => (src ? `<span class="cite">${BOOK[src.book] || src.book}${src.page ? ` p.${src.page}` : ""}</span>` : "");
  const esc = (t) => String(t ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
  const SYM_TOKENS = { success: "s", advantage: "a", threat: "t", failure: "f", triumph: "r", despair: "d" };
  const rich = (text) => esc(text).replace(/\[\[?(\w+)\]?\]/g, (m, k) => SYM_TOKENS[k] ? `<span class="glyph" title="${k}">${sym(SYM_TOKENS[k])}</span>`
    : DICE[k] ? `<span class="glyph">${dieSVG(k, null, DICE[k].name)}</span>` : m);
  // Skill -> characteristic key. Adversaries list generic names ("Computers") where the Android list splits them.
  const SKILL_CHAR = Object.fromEntries(CONTENT.skills.map((s) => [s.name, KEY_OF[s.characteristic]]));
  const charOf = (skill) => SKILL_CHAR[skill] || (Object.entries(SKILL_CHAR).find(([n]) => n.startsWith(skill + " ")) || [])[1] || "Int";
  const CRIT_TABLE = CONTENT.crits.slice().sort((a, b) => a.roll[0] - b.roll[0]);
  const critAt = (total) => CRIT_TABLE.find(({ roll: [lo, hi] }) => total >= lo && (hi === null || total <= hi));
  const LADDER = [
    { name: "Simple", n: 0 }, { name: "Easy", n: 1 }, { name: "Average", n: 2 },
    { name: "Hard", n: 3 }, { name: "Daunting", n: 4 }, { name: "Formidable", n: 5 },
  ];
  // Table I.6-2 (CRB p.104), Threat and Despair side: what the GM buys with a player's bad rolls.
  const THREAT_OPTS = CONTENT["spend-results"].filter((o) => o.side === "negative")
    .sort((a, b) => ((a.cost.threat || 9) - (b.cost.threat || 9)) || a.name.localeCompare(b.name))
    .map((o) => ({ threat: o.cost.threat || 0, orDespair: !!o.cost["or-despair"], despair: o.cost.despair || 0, label: o.name, source: o.source }));

  // ---------- Sample party (names from the table's list; thresholds derived from the Android archetypes) ----------
  const PARTY = [
    { id: "prance", name: "Prance Galavent", initials: "PG", arch: "natural", career: "courier", color: "var(--pc-1)",
      chars: { Br: 2, Ag: 3, Int: 2, Cun: 3, Will: 2, Pr: 2 }, bonus: { wounds: 2, strain: 1, why: "Toughened, Grit" }, soak: 3, def: [0, 0],
      ranks: { Cool: 1, Vigilance: 1, Perception: 1, Piloting: 2, "Ranged (Light)": 1, Stealth: 1 },
      wounds: 4, strain: 3, crits: [4],
      ob: { type: "debt", mag: 10, att: "Weyland Consortium", why: "Weyland paid for the berth on the Concord, the salvage rig and the scout ship's fuel." },
      s0: { q: "Out past the last gate, you heard something on an empty comms channel. What did it say, and why did it use your name?", a: "A voice they knew, calling their name. Prance has told no one.", act: "Act III · Ten" },
      sig: [["Piloting", "Steering through the debris field"], ["Ranged (Light)", "Enforcer at the drone"], ["Perception", "Scanning the cargo ring"]] },
    { id: "krum", name: "Krum", initials: "KR", arch: "g-mod", career: "runner", color: "var(--pc-2)",
      chars: { Br: 2, Ag: 2, Int: 4, Cun: 3, Will: 1, Pr: 2 }, soak: 2, def: [0, 0],
      ranks: { "Computers (Hacking)": 2, "Computers (Sysops)": 1, Cool: 1, Perception: 1, Deception: 1, Skulduggery: 1 },
      wounds: 2, strain: 6, crits: [],
      ob: { type: "forbidden-knowledge", mag: 5, att: "Jinteki", why: "Pulled a clone-batch manifest off a Jinteki node that lists the Meridian's crew as 'returned'." },
      s0: { q: "Which corporation owns something that used to be part of you: a limb, a memory, a patent, a face?", a: "Jinteki. Krum's neural lace is Jinteki IP, and so is the face on a clone-batch intake form Krum pulled off a node: Krum's own, dated next year.", act: "Act II · Shō" },
      sig: [["Computers (Hacking)", "Slicing the dock's ICE"], ["Skulduggery", "Lifting the drone's access chip"], ["Deception", "Bluffing the dock AI"]] },
    { id: "behnsun", name: "Behnsun Payne", initials: "BP", arch: "clone", career: "tech", color: "var(--pc-3)",
      chars: { Br: 2, Ag: 2, Int: 3, Cun: 2, Will: 2, Pr: 2 }, soak: 2, def: [0, 0],
      ranks: { Medicine: 2, Mechanics: 1, Vigilance: 1, Perception: 1, "Computers (Sysops)": 1 },
      wounds: 0, strain: 1, crits: [],
      ob: { type: "family-duty", mag: 10, att: "Ama Payne, a sibling in Jinteki's Quinde crèche", why: "Ama's contract renews next year unless someone buys it out." },
      s0: { q: "Who is the last person who trusted you completely, and what happened to them?", a: "Ama, Behnsun's sibling in Jinteki's Quinde crèche. Behnsun promised to buy out Ama's contract before it renews.", act: "Act IV · Ketsu" },
      sig: [["Medicine", "Patching Fizz's chassis"], ["Mechanics", "Bypassing the blast door"], ["Perception", "Reading the cryo logs"]] },
    { id: "fizz", name: "Fizz", initials: "FZ", arch: "bioroid", career: "roughneck", color: "var(--pc-4)",
      chars: { Br: 3, Ag: 2, Int: 1, Cun: 1, Will: 2, Pr: 1 }, soak: 4, def: [1, 1],
      ranks: { Athletics: 2, Gunnery: 1, "Ranged (Heavy)": 1, Vigilance: 2, Resilience: 1, Operating: 1 },
      wounds: 9, strain: 4, crits: [40],
      ob: { type: "corporate-contract", mag: 15, att: "Haas-Bioroid", why: "Fizz's chassis is Haas-Bioroid property on paper. The recall order is signed, just not served." },
      s0: { q: "You once left someone behind in vacuum to save yourself. Who were they, and what do you tell people happened?", a: "A foreman on the Quinde pit line. Fizz sealed the bulkhead because it was ordered to, and tells people the foreman made it out.", act: "Act III · Ten" },
      sig: [["Athletics", "Hauling the cargo pod"], ["Ranged (Heavy)", "Covering fire on the guards"], ["Resilience", "Shrugging off the EMP"]] },
    { id: "adarius", name: "Adarius", initials: "AD", arch: "loonie", career: "con-artist", color: "var(--pc-5)",
      chars: { Br: 2, Ag: 3, Int: 2, Cun: 3, Will: 2, Pr: 3 }, soak: 2, def: [0, 0],
      ranks: { Deception: 2, Charm: 1, Cool: 2, Skulduggery: 1, Perception: 1, Negotiation: 1 },
      wounds: 3, strain: 7, crits: [],
      ob: { type: "betrayal", mag: 5, att: "Orgcrime: the Tsang family", why: "Sold the Tsangs' Heinlein route to the NAPD to get off the Moon." },
      s0: { q: "Someone back in the Core still holds a funeral for you every year. Why do they think you're dead?", a: "Lien Tsang. Adarius faked a death on the Heinlein route to get off the Moon, and Lien still lights a candle every year.", act: "Act IV · Ketsu" },
      sig: [["Charm", "Talking the clerk out of the manifest"], ["Deception", "Posing as a Weyland auditor"], ["Cool", "Holding the bluff"]] },
  ].map((p) => ({ ...p, archetype: find("archetypes", p.arch), careerE: find("careers", p.career), obType: find("obligation-types", p.ob.type),
    crits: p.crits.map((r) => ({ ...critAt(r), rolled: r })) }));
  const pcById = (id) => PARTY.find((p) => p.id === id);
  const isSecretOb = (p) => p.obType.kind === "secret";

  // Sample campaign people and corps. The campaign manager owns these; here they're quick links.
  const NPCS = [
    { id: "rook", name: "Rook", role: "Haas-Bioroid dock chief, Concord cargo ring", pub: "Runs the cargo ring's docks. Owes no one, says everyone.",
      gm: "Skimming Haas-Bioroid salvage. Will sell the party out to keep it quiet." },
    { id: "mx7", name: "MX-7", role: "Bioroid deckhand", pub: "Keeps asking whether the gate counts as 'outside'.",
      gm: "Someone aboard patched its Directives. It doesn't know yet." },
    { id: "okafor", name: "Jules Okafor", role: "NBN camera crew", pub: "Friendly. Films everything.",
      gm: "Streams the raw feed to an NBN buyer who pays for the party's secrets." },
  ];
  const CORPS = [
    ["haas-bioroid", "Builds the Concord's bioroid crew.", "Wants the gate's far side surveyed by bioroids only: no human witnesses."],
    ["jinteki", "Supplies the clone medics and cryo bay.", "The Meridian's crew came back. Jinteki has been growing replacements."],
    ["nbn", "Owns the broadcast rights to the voyage.", "Is scripting a disaster for the Ten act ratings spike."],
    ["weyland-consortium", "Funds the voyage and holds the party's debts.", "Means to claim the gate before the UN rules on it, whatever it costs the crew."],
  ].map(([id, pub, gm]) => ({ ...(find("factions", id) || { id, name: id }), pub, gm }));

  // ---------- Encounter NPCs (stat blocks from the library) ----------
  let npcSeq = 0;
  const makeNpc = (advId, label, count) => {
    const a = find("adversaries", advId);
    return { uid: `n${++npcSeq}`, adv: a, label: label || a.name, count: a.type === "minion" ? count || 3 : 1, dmg: 0, strainDmg: 0 };
  };
  const npcThreshold = (n) => n.adv.wounds * n.count;
  const minionsLeft = (n) => Math.max(0, n.count - Math.floor(n.dmg / n.adv.wounds));
  const npcDown = (n) => (n.adv.type === "minion" ? minionsLeft(n) === 0 : n.dmg > n.adv.wounds || (n.adv.strain && n.strainDmg > n.adv.strain));
  // What players get instead of numbers: a condition word.
  const npcCondition = (n) => {
    if (npcDown(n)) return ["Down", "bad"];
    const f = n.adv.type === "minion" ? 1 - minionsLeft(n) / n.count : n.dmg / n.adv.wounds;
    return f === 0 ? ["Unhurt", "ok"] : f < 0.5 ? ["Hurt", "warn"] : ["Badly hurt", "bad"];
  };
  const npcSkillRank = (n, skill) => {
    const s = (n.adv.skills || []).find((x) => x.name === skill);
    if (!s) return 0;
    return n.adv.type === "minion" ? Math.min(5, minionsLeft(n) - 1) : s.rank || 0;
  };

  // ---------- Pools ----------
  const emptyPool = () => ({ proficiency: 0, ability: 0, boost: 0, challenge: 0, difficulty: 0, setback: 0 });
  const mkPool = (ch, rk) => ({ ...emptyPool(), proficiency: Math.min(ch, rk), ability: Math.max(ch, rk) - Math.min(ch, rk) });
  const pcPool = (p, skill) => mkPool(p.chars[charOf(skill)], p.ranks[skill] || 0);
  const npcPool = (n, skill) => mkPool(n.adv.characteristics[Object.keys(KEY_OF).find((k) => KEY_OF[k] === charOf(skill))] || 2, npcSkillRank(n, skill));
  const miniPool = (pool) => ["proficiency", "ability"].map((t) => Array.from({ length: pool[t] }, () => dieSVG(t, null, DICE[t].name)).join("")).join("");

  // ---------- Derived stats ----------
  const tWounds = (p) => p.archetype.wounds + p.chars.Br + ((p.bonus || {}).wounds || 0);
  const tStrainBase = (p) => p.archetype.strain + p.chars.Will + ((p.bonus || {}).strain || 0);
  // House rule: the PC whose Obligation came due gets -2 strain threshold, everyone else -1, doubled on doubles.
  const obMod = (p) => (S.due && S.due.id ? (p.id === S.due.id ? -2 : -1) * (S.due.doubles ? 2 : 1) : 0);
  const tStrain = (p) => tStrainBase(p) + obMod(p);
  const isDown = (p) => p.wounds > tWounds(p) || p.strain > tStrain(p);
  const totalMag = () => PARTY.reduce((n, p) => n + p.ob.mag, 0);
  const slices = () => { let lo = 1; return PARTY.map((p) => { const s = { p, lo, hi: lo + p.ob.mag - 1 }; lo += p.ob.mag; return s; }); };

  // ---------- State ----------
  const isDesk = () => window.matchMedia("(min-width: 1000px)").matches;
  const now = Date.now();
  const npcs = [makeNpc("militia", "Globalsec guards", 3), makeNpc("combat-drone", "Weyland security drone"), makeNpc("intelligence-agent", "Agent Sato (Weyland)")];
  const S = {
    preview: false, tab: "party", live: true, unread: 0, spMenu: false, flipped: -1, freshId: 0,
    session: 5, act: "Act II · Shō", scene: "Dock 9, Concord cargo ring",
    story: ["player", "player", "player", "gm", "gm", "gm", "gm"],
    obRoll: null, due: null,
    notes: "Rook is stalling the guards. If the party spares the drone, Sato remembers it in Act III.\nOkafor is filming from the gantry.",
    init: { rolled: true, round: 1, cur: 1, surprised: false, filled: { 0: "krum" },
      slots: [{ side: "pc", s: 3, a: 1, by: "krum" }, { side: "npc", ref: "n2", s: 2, a: 2 }, { side: "pc", s: 2, a: 0, by: "fizz" },
        { side: "npc", ref: "n1", s: 1, a: 1 }, { side: "pc", s: 1, a: 2, by: "prance" }, { side: "pc", s: 1, a: 0, by: "adarius" },
        { side: "npc", ref: "n3", s: 0, a: 1 }, { side: "pc", s: 0, a: 0, by: "behnsun" }] },
    feed: [
      { id: 1, who: "fizz", check: "Athletics: hauling the cargo pod", netS: 1, netA: -2, triumph: 0, despair: 0, spentT: [], at: now - 1 * 60e3 },
      { id: 2, who: "behnsun", check: "Medicine: cryo pod 14", netS: -1, netA: 2, triumph: 0, despair: 0, spent: ["Notice a single important point in the ongoing conflict"], spentT: [], at: now - 3 * 60e3 },
      { id: 3, who: "gm", check: "Vigilance for Prance: sabotage alarm", netS: 1, netA: -2, triumph: 0, despair: 0, secret: true, spentT: [], at: now - 5 * 60e3 },
      { id: 4, who: "krum", check: "Computers (Hacking): nav ICE", netS: 2, netA: 1, triumph: 1, despair: 0,
        spent: ["Do something vital", "Recover 1 strain."], spentT: [], at: now - 8 * 60e3 },
    ],
    d: { open: false },
  };
  let feedSeq = 10;
  try { const saved = localStorage.getItem("ttg-gm-notes"); if (saved !== null) S.notes = saved; } catch (e) {}
  const $ = (id) => document.getElementById(id);
  const gm = () => !S.preview;
  let toastTimer = 0;
  const toast = (text) => { const t = $("toast"); t.textContent = text; t.hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => (t.hidden = true), 2800); };
  const post = (entry) => { const e = { id: ++feedSeq, spentT: [], at: Date.now(), ...entry }; S.feed.unshift(e); S.freshId = e.id; if (S.tab !== "feed" && !isDesk()) S.unread++; return e; };
  const sysPost = (text) => post({ who: "sys", text });
  const ago = (t) => { const m = Math.round((Date.now() - t) / 60e3); return m < 1 ? "now" : m < 60 ? `${m}m` : `${Math.round(m / 60)}h`; };
  const gmTag = () => `<span class="tag gm">GM only</span>`;
  const gmBlock = (inner) => (gm() ? `<div class="gmonly"><div class="gmhd">${gmTag()}</div>${inner}</div>` : "");

  // ---------- Top bar: session, story points, view ----------
  const playerPoints = () => S.story.filter((x) => x === "player").length;
  function renderTop() {
    $("sess").textContent = `Session ${S.session} · ${S.act}`;
    $("tokens").innerHTML = S.story.map((x, i) => {
      const label = x === "gm" ? (gm() ? "GM story point. Tap to spend." : "GM story point") : "Player story point";
      return `<button type="button" class="tok ${x}${i === S.flipped ? " flip" : ""}" data-tok="${i}" aria-label="${label}" title="${label}"${x === "player" || !gm() ? ' aria-disabled="true"' : ""}></button>`;
    }).join("");
    $("sp-count").textContent = `${playerPoints()} player · ${S.story.length - playerPoints()} GM`;
    const m = $("sp-menu");
    m.hidden = !S.spMenu || !gm();
    if (!m.hidden) m.innerHTML = `<p>Spend a GM story point. It flips to the players' side.</p>
      <button type="button" class="btn" data-act="sp-complication">Introduce a complication</button>
      <button type="button" class="btn" data-act="sp-npc">Upgrade an NPC check (opens the roller)</button>
      <p>To upgrade a player's difficulty, use the chip in their roll's GM menu once rolls sync (M6).</p>
      <button type="button" class="linkbtn" data-act="sp-cancel">Cancel</button>`;
    S.flipped = -1;
    $("live-btn").setAttribute("aria-pressed", String(S.live));
    document.querySelectorAll("[data-view]").forEach((b) => b.setAttribute("aria-pressed", String((b.dataset.view === "player") === S.preview)));
    $("app").classList.toggle("preview", S.preview);
    $("banner").hidden = !S.preview;
    $("banner").innerHTML = `<b>Player view.</b> What a player's browser receives. GM-only fields are stripped by the server, not just hidden. <button type="button" class="linkbtn" data-view="gm">Back to GM view</button>`;
  }
  function flipGm(why) {
    const i = S.story.indexOf("gm");
    if (i < 0) { toast("No GM story points left"); return false; }
    S.story[i] = "player"; S.flipped = i; S.spMenu = false;
    sysPost(`GM spent a story point: ${why}`);
    renderTop(); renderFeed();
    return true;
  }

  // ---------- Phone tabs ----------
  const pendingThreat = () => S.feed.filter((e) => threatLeft(e).any && !e.tDone && e.who !== "gm" && e.who !== "sys").length;
  function renderTabs() {
    const badges = { feed: S.unread ? `<span class="badge soft">${S.unread}</span>` : "", init: "" };
    if (gm() && pendingThreat()) badges.feed = `<span class="badge" title="Threat to spend">${pendingThreat()}</span>`;
    if (S.init.rolled) badges.init = `<span class="badge soft">R${S.init.round}</span>`;
    $("tabs").innerHTML = [["party", "Party"], ["init", "Initiative"], ["feed", "Feed"], ["session", "Session"]].map(([id, label]) =>
      `<button type="button" role="tab" data-tab="${id}" aria-selected="${S.tab === id}">${label}${badges[id] || ""}</button>`).join("");
    document.querySelectorAll("[data-panel]").forEach((el) => { el.hidden = !isDesk() && el.dataset.panel !== S.tab; });
  }

  // ---------- Party ----------
  function bar(kind, cur, max, base, p) {
    const over = cur > max, pct = Math.min(100, (cur / Math.max(1, max)) * 100);
    const title = kind === "wounds" ? "Wounds" : "Strain";
    const mod = kind === "strain" && max !== base ? `<span class="why">${max - base} Obligation</span>` : "";
    return `<div class="bar"><div class="hd"><span class="label">${title}</span><span class="v"><b>${cur}</b> / ${max !== base ? `<s>${base}</s> ` : ""}${max}</span></div>
      <div class="meter ${kind === "wounds" ? "wound" : "strain"}${over ? " over" : ""}" role="img" aria-label="${title} ${cur} of ${max}"><i style="width:${pct}%"></i></div>
      ${gm() ? `<div class="ctl"><button type="button" class="btn step" data-adj="${kind}" data-pc="${p.id}" data-by="-1" aria-label="${p.name}: ${kind === "wounds" ? "heal 1 wound" : "recover 1 strain"}">&minus;</button>
        <button type="button" class="btn step" data-adj="${kind}" data-pc="${p.id}" data-by="1" aria-label="${p.name}: ${kind === "wounds" ? "take 1 wound" : "suffer 1 strain"}">+</button>${mod}</div>` : mod}</div>`;
  }
  function obChip(p) {
    if (!gm() && isSecretOb(p)) return `<span class="tag">Secret</span>`;
    const due = S.due && S.due.id === p.id;
    return `<span class="tag${due ? " warn" : ""}">${esc(p.obType.name)} ${p.ob.mag}${due ? " · due" : ""}</span>`;
  }
  function pcCard(p) {
    const flags = [isDown(p) ? `<span class="tag bad">Incapacitated</span>` : "",
      p.crits.length ? `<span class="tag bad">${p.crits.length} crit${p.crits.length > 1 ? "s" : ""}</span>` : ""].join("");
    return `<article class="pc${isDown(p) ? " down" : ""}${S.flash === p.id ? " flash" : ""}" style="--pc:${p.color}">
      <div class="row1"><span class="av" aria-hidden="true">${p.initials}</span>
        <div><h3><button type="button" data-pcopen="${p.id}">${esc(p.name)}</button></h3><div class="sub">${p.archetype.name} · ${p.careerE.name}</div></div>
        <div class="flags">${flags}</div></div>
      <div class="bars">${bar("wounds", p.wounds, tWounds(p), tWounds(p), p)}${bar("strain", p.strain, tStrain(p), tStrainBase(p), p)}</div>
      <div class="row3"><span>Soak <b>${p.soak}</b></span><span>Def <b>${p.def[0]}/${p.def[1]}</b></span>${obChip(p)}</div></article>`;
  }
  function renderParty() {
    const crits = PARTY.reduce((n, p) => n + p.crits.length, 0), down = PARTY.filter(isDown).length;
    $("p-party").innerHTML = `<header><h2 id="h-party">Party<small>${PARTY.length} PCs</small></h2>
      <div class="summary">${down ? `<span class="tag bad">${down} down</span>` : ""}${crits ? `<span class="tag">${crits} crits</span>` : ""}
        <span class="tag" title="Total Obligation and Secret magnitude">Magnitude ${totalMag()}</span></div></header>
      <div class="pbody">${PARTY.map(pcCard).join("")}
        <p class="note">${gm() ? "Changes here show on each player's sheet. Tap a name for details." : "Tap a name to see what that player shares."}</p></div>`;
    S.flash = null;
  }

  // ---------- Session: Obligation check, quick links, notes ----------
  function obligationHTML() {
    const tot = totalMag(), r = S.obRoll;
    const segs = slices().map(({ p, lo, hi }) => `<span class="sl${S.due && S.due.id === p.id ? " hit" : ""}" style="--pc:${p.color};flex:${p.ob.mag}" title="${esc(p.name)} ${lo}–${hi}">${p.initials}</span>`).join("");
    const marker = r ? `<span class="marker" style="left:${Math.min(r, 100) - 0.5}%" aria-hidden="true"></span>` : "";
    const legend = slices().map(({ p, lo, hi }) => `<div style="--pc:${p.color}"><i></i><span>${esc(p.name)} · ${!gm() && isSecretOb(p) ? "Secret" : esc(p.obType.name)}</span><span class="rg">${String(lo).padStart(2, "0")}–${String(hi).padStart(2, "0")}</span></div>`).join("");
    let res = `<div class="due"><span class="label">Session ${S.session} check</span><p style="margin:0">Roll d100 against the party's magnitude ${tot}. A roll in someone's slice brings their attachment into this session.</p>
      ${gm() ? `<div><button type="button" class="btn primary" data-act="ob-roll">Roll d100</button></div>` : `<p class="note">Waiting for the GM to roll.</p>`}</div>`;
    if (r) {
      const p = S.due.id && pcById(S.due.id), dbl = S.due.doubles;
      res = `<div class="due${p ? " hit" : ""}"><span class="label">Session ${S.session} check</span>
        <div><b class="n">${String(r === 100 ? "00" : r).padStart(2, "0")}</b>${dbl ? ` <span class="tag warn">Doubles</span>` : ""}</div>
        <p style="margin:0">${p ? `<b>${esc(p.name)}</b>'s ${!gm() && isSecretOb(p) ? "Secret" : esc(p.obType.name.toLowerCase())} comes due. Strain threshold ${dbl ? "−4" : "−2"} for ${esc(p.name.split(" ")[0])}, ${dbl ? "−2" : "−1"} for everyone else this session.`
          : `Over ${tot}: nothing comes due this session.${dbl ? " Doubles, but nobody's slice was hit." : ""}`}</p>
        ${p ? gmBlock(`<p style="margin:0"><b>${esc(p.ob.att)}</b> shows up. ${esc(p.ob.why)}</p>`) : ""}
        ${gm() ? `<div><button type="button" class="linkbtn" data-act="ob-reset">Undo the roll</button></div>` : ""}</div>`;
    }
    return `<div class="slices" role="img" aria-label="Magnitude slices out of 100">${segs}${tot < 100 ? `<span class="rest" style="flex:${100 - tot}">${tot + 1 < 100 ? `${tot + 1}–00: nothing` : ""}</span>` : ""}${marker}</div>
      <div class="scale"><span>01</span><span>00</span></div>${res}<div class="legend">${legend}</div>
      <p class="note">House rule: Secrets and Obligations ${cite({ book: "house" })}</p>`;
  }
  const linkRow = (kind, x, sub) => `<button type="button" class="lk" data-link="${kind}:${x.id}"><span>${esc(x.name)}<small>${esc(sub)}</small></span>${gm() ? `<span class="tag gm">GM</span>` : ""}</button>`;
  function renderSession() {
    $("p-session").innerHTML = `<header><h2 id="h-session">Session ${S.session}<small>${esc(S.act)}</small></h2>
      ${gm() ? `<div class="hdacts"><button type="button" class="btn" data-act="new-session" title="Next session: new Obligation check, fresh story points">Start session ${S.session + 1}</button></div>` : ""}</header>
      <div class="pbody">
        <details class="sect" open><summary><h3 class="sh">Obligation &amp; Secrets</h3></summary><div style="display:grid;gap:8px;margin-top:8px">${obligationHTML()}</div></details>
        <details class="sect" open><summary><h3 class="sh">People in play</h3></summary><div class="links" style="margin-top:8px">${NPCS.map((n) => linkRow("npc", n, n.role)).join("")}</div></details>
        <details class="sect"${isDesk() ? "" : " open"}><summary><h3 class="sh">Corps</h3></summary><div class="links" style="margin-top:8px">${CORPS.map((c) => linkRow("corp", c, c.pub)).join("")}</div></details>
        ${gm() ? `<details class="sect" open><summary><h3 class="sh">Session notes</h3><span class="tag gm">GM only</span></summary>
          <label class="sr" for="notes">Session notes</label><textarea id="notes" style="margin-top:8px">${esc(S.notes)}</textarea><p class="note">Saves as you type. The full session log lives in the campaign manager.</p></details>` : ""}
      </div>`;
  }
  function rollObligation() {
    const r = rand(100) + 1, hit = slices().find((s) => r >= s.lo && r <= s.hi);
    S.obRoll = r; S.due = { id: hit ? hit.p.id : null, doubles: r === 100 || (r > 10 && r % 11 === 0) };
    sysPost(hit ? `Obligation check ${String(r).padStart(2, "0")}: ${hit.p.name}'s ${isSecretOb(hit.p) ? "Secret" : hit.p.obType.name} comes due${S.due.doubles ? " (doubles)" : ""}`
      : `Obligation check ${String(r).padStart(2, "0")}: nothing comes due`);
    $("live").textContent = `Obligation check: ${r}. ${hit ? hit.p.name + " comes due" : "Nothing comes due"}`;
    renderAll();
  }
  function newSession() {
    S.session++; S.obRoll = null; S.due = null;
    // Genesys: one player point per player, two for the GM (CRB p.27).
    S.story = [...PARTY.map(() => "player"), "gm", "gm"];
    sysPost(`Session ${S.session} started. Story points reset: ${PARTY.length} player, 2 GM.`);
    toast(`Session ${S.session}: roll the Obligation check`);
    renderAll();
  }

  // ---------- Initiative ----------
  const npcByUid = (uid) => npcs.find((n) => n.uid === uid);
  const actedThisRound = () => new Set(Object.values(S.init.filled));
  const slotGone = (sl) => sl.side === "npc" && (!npcByUid(sl.ref) || npcDown(npcByUid(sl.ref)));
  const resHTML = (s, a) => `<span class="res" title="${s} success, ${a} advantage">${sym("s")}${s} ${sym("a")}${a}</span>`;
  function slotHTML(sl, i) {
    const it = S.init, isNow = i === it.cur, done = i < it.cur, n = sl.side === "npc" && npcByUid(sl.ref);
    let who;
    if (sl.side === "npc") who = n ? `<span>${esc(n.label)}</span>${n.adv.type === "minion" ? `<span class="tag">×${minionsLeft(n)}</span>` : ""}${isNow && gm() && !npcDown(n) ? `<button type="button" class="linkbtn" data-npcroll="${n.uid}">Roll for it</button>` : ""}` : `<span class="open">Removed</span>`;
    else {
      const f = it.filled[i] && pcById(it.filled[i]);
      const free = PARTY.filter((p) => !actedThisRound().has(p.id) && !isDown(p));
      who = f ? `<span>${esc(f.name)}</span>` : `<span class="open">Any PC who hasn't acted</span>`;
      if (isNow && !f) who += `<div class="fill">${free.length ? free.map((p) => `<button type="button" class="chip" style="--pc:${p.color}" data-fill="${p.id}"><span class="pcdot"></span>${esc(p.name.split(" ")[0])}</button>`).join("") : '<span class="note">Every PC has acted.</span>'}</div>`;
    }
    return `<li class="slot${isNow ? " now" : ""}${done ? " done" : ""}${slotGone(sl) ? " gone" : ""}"${isNow ? ' aria-current="step"' : ""}>
      <span class="ix">${i + 1}</span><span class="side ${sl.side}">${sl.side.toUpperCase()}</span><div class="who">${who}</div>${resHTML(sl.s, sl.a)}</li>`;
  }
  function npcCard(n) {
    const a = n.adv, [cond, tone] = npcCondition(n);
    const skills = (a.skills || []).filter((s) => n.adv.type === "minion" || s.rank).slice(0, 5);
    const nums = gm() ? `<div class="stats"><span>Wounds <b>${n.dmg}</b>/${npcThreshold(n)}</span>${a.strain ? `<span>Strain <b>${n.strainDmg}</b>/${a.strain}</span>` : ""}
        <span>Soak <b>${a.soak}</b></span><span>Def <b>${a.defense.melee}/${a.defense.ranged}</b></span></div>` : "";
    const mins = a.type === "minion" ? `<div class="minions" aria-label="${minionsLeft(n)} of ${n.count} standing">${Array.from({ length: n.count }, (_, i) => `<i class="${i < minionsLeft(n) ? "" : "dead"}"></i>`).join("")}</div>` : "";
    const ctl = gm() ? `<div class="hit"><label class="sr" for="hit-${n.uid}">Damage to ${esc(n.label)}</label><input id="hit-${n.uid}" type="number" inputmode="numeric" min="0" placeholder="Dmg" data-hitin="${n.uid}">
        <button type="button" class="btn" data-hit="${n.uid}">Hit</button>${a.strain ? `<button type="button" class="btn" data-hitstrain="${n.uid}">Strain</button>` : ""}
        <button type="button" class="linkbtn" data-npcopen="${n.uid}">Stat block</button></div>
      <div class="chips">${skills.map((s) => `<button type="button" class="chip" data-npcskill="${n.uid}|${esc(s.name)}">${esc(s.name)} <span style="display:inline-flex">${miniPool(npcPool(n, s.name))}</span></button>`).join("")}</div>` : "";
    return `<article class="foe${npcDown(n) ? " gone" : ""}"><div class="row1"><h3>${esc(n.label)}</h3><span class="tag">${a.type}${a.type === "minion" ? ` ×${n.count}` : ""}</span>${gm() ? cite(a.source) : ""}
      <span class="cond ${tone}">${cond}</span></div>${mins}${nums}${ctl}</article>`;
  }
  function renderInit() {
    const it = S.init;
    const head = `<header><h2 id="h-init">Initiative<small>${esc(S.scene)}</small></h2>${gm() ? `<div class="hdacts">${it.rolled
      ? `<button type="button" class="btn primary" data-act="next">Next turn</button><button type="button" class="btn" data-act="reroll">Re-roll</button><button type="button" class="btn" data-act="end-enc">End encounter</button>`
      : `<button type="button" class="btn" data-act="add-npc">Add NPCs</button>`}</div>` : ""}</header>`;
    const body = it.rolled ? `<div class="round"><span class="label">Round</span><b>${it.round}</b><span class="note" style="margin:0">Slot ${it.cur + 1} of ${it.slots.length}</span></div>
      <ol class="slots">${it.slots.map(slotHTML).join("")}</ol>
      <p class="note">PC slots are open: any PC who hasn't acted this round can take one ${cite({ book: "crb", page: 95 })}. A PC wins a tie with an NPC.</p>`
      : `<div class="empty"><span>No combat running. Add NPCs, then roll initiative: Cool if the side was ready, Vigilance if surprised.</span>
        ${gm() ? `<div class="chips"><button type="button" class="chip" data-surprise="pcs" aria-pressed="${it.surprised === "pcs"}">PCs surprised</button><button type="button" class="chip" data-surprise="npcs" aria-pressed="${it.surprised === "npcs"}">NPCs surprised</button></div>
        <div><button type="button" class="btn primary" data-act="roll-init" ${npcs.length ? "" : "disabled"}>Roll initiative for everyone</button></div>` : ""}</div>`;
    $("p-init").innerHTML = head + `<div class="pbody">${body}
      <div class="round" style="justify-content:space-between;margin-top:6px"><h3 class="sh">NPCs in this scene</h3>${gm() && it.rolled ? `<button type="button" class="btn" data-act="add-npc">Add NPCs</button>` : ""}</div>
      ${npcs.length ? `<div class="npcs">${npcs.map(npcCard).join("")}</div>` : `<p class="note">No NPCs yet.</p>`}
      ${gm() ? "" : `<p class="note">Players see each NPC's condition, not its numbers.</p>`}</div>`;
  }
  function rollInitiative() {
    const it = S.init, skillFor = (side) => (it.surprised === side ? "Vigilance" : "Cool");
    const pcs = PARTY.filter((p) => !isDown(p)).map((p) => ({ side: "pc", by: p.id, ...scoreOf(pcPool(p, skillFor("pcs"))) }));
    const ns = npcs.filter((n) => !npcDown(n)).map((n) => ({ side: "npc", ref: n.uid, ...scoreOf(npcPool(n, skillFor("npcs"))) }));
    it.slots = [...pcs, ...ns].sort((a, b) => b.s - a.s || b.a - a.a || (a.side === "pc" ? -1 : 1) - (b.side === "pc" ? -1 : 1));
    Object.assign(it, { rolled: true, round: 1, cur: 0, filled: {} });
    sysPost(`Initiative rolled (${it.surprised ? it.surprised.toUpperCase() + " surprised" : "everyone ready"}): ${pcs.length} PC slots, ${ns.length} NPC slots`);
    renderInit(); renderFeed(); renderTabs();
  }
  // Initiative is a Simple check: no difficulty dice, rank by successes then advantage.
  const scoreOf = (pool) => { const r = scoreDice(instantRoll(poolList(pool))); return { s: r.netS, a: r.netA }; };
  function nextTurn() {
    const it = S.init;
    do {
      it.cur++;
      if (it.cur >= it.slots.length) { it.cur = 0; it.round++; it.filled = {}; toast(`Round ${it.round}`); sysPost(`Round ${it.round}`); }
    } while (slotGone(it.slots[it.cur]) && it.slots.some((sl) => !slotGone(sl)));
    $("live").textContent = `Round ${it.round}, slot ${it.cur + 1}: ${it.slots[it.cur].side === "pc" ? "a PC" : esc(npcByUid(it.slots[it.cur].ref).label)}`;
    renderInit(); renderTabs(); renderFeed();
  }
  function hitNpc(uid, strainOnly) {
    const n = npcByUid(uid), raw = parseInt(($(`hit-${uid}`) || {}).value, 10);
    if (!(raw >= 0)) { toast("Enter the damage first"); return; }
    if (strainOnly) { n.strainDmg += raw; sysPost(`${n.label} suffers ${raw} strain`); }
    else {
      const dealt = Math.max(0, raw - n.adv.soak), before = minionsLeft(n);
      n.dmg += dealt;
      const fell = n.adv.type === "minion" ? before - minionsLeft(n) : 0;
      toast(dealt ? `${raw} − ${n.adv.soak} soak = ${dealt} wound${dealt === 1 ? "" : "s"}${fell ? `: ${fell} down` : ""}` : `Soak absorbed all ${raw}`);
      sysPost(`${n.label} hit for ${raw}, ${dealt} past soak${npcDown(n) ? ". Out of the fight." : fell ? `. ${fell} minion${fell > 1 ? "s" : ""} down.` : ""}`);
    }
    renderInit(); renderFeed();
  }

  // ---------- Feed ----------
  const threatLeft = (e) => {
    if (e.who === "sys" || e.secret) return { thr: 0, des: 0, any: false };
    const thr = Math.max(0, -e.netA) - e.spentT.reduce((n, s) => n + s.paidT, 0), des = e.despair - e.spentT.reduce((n, s) => n + s.paidD, 0);
    return { thr, des, any: thr > 0 || des > 0 };
  };
  function verdictText(r) {
    const a = r.netA > 0 ? (r.netS > 0 ? ", with Advantage" : ", but with Advantage") : r.netA < 0 ? (r.netS > 0 ? ", but with Threat" : ", with Threat") : "";
    return (r.netS > 0 ? "Success" : "Failure") + a;
  }
  const symCount = (s, n) => (n ? `<span class="syms" title="${n} ${SYM_NAME[s]}">${sym(s)}${n}</span>` : "");
  const netSyms = (e) => (e.netS > 0 ? symCount("s", e.netS) : symCount("f", -e.netS)) + (e.netA > 0 ? symCount("a", e.netA) : symCount("t", -e.netA)) + symCount("r", e.triumph) + symCount("d", e.despair);
  function whoOf(e) {
    if (e.who === "gm") return { name: "GM", initials: "GM", color: "var(--gm)" };
    if (e.npc) return { name: e.npc, initials: "NP", color: "var(--bad)" };
    return pcById(e.who);
  }
  function feedItem(e) {
    const fresh = e.id === S.freshId ? " fresh" : "";
    if (e.who === "sys") return `<li class="sys${fresh}"><span class="av" aria-hidden="true" style="--pc:var(--muted)">·</span><div><div class="t"><span>${esc(e.text)}</span><time>${ago(e.at)}</time></div></div></li>`;
    const w = whoOf(e), tl = threatLeft(e), pend = gm() && tl.any && !e.tDone;
    if (e.secret && !gm()) return `<li class="${fresh}"><span class="av" aria-hidden="true" style="--pc:var(--gm)">GM</span><div><div class="t"><b>GM</b> made a secret check<time>${ago(e.at)}</time></div><div class="secret">Only the GM sees this result.</div></div></li>`;
    const spent = (e.spent || []).map((s) => `<span>${esc(s)}</span>`).join("") + e.spentT.map((s) => `<span class="gm">GM: ${esc(s.label)}</span>`).join("");
    return `<li class="${pend ? "pending" : ""}${fresh}"><span class="av" aria-hidden="true" style="--pc:${w.color}">${w.initials}</span><div>
      <div class="t"><b>${esc(w.name)}</b>${e.secret ? '<span class="lock">Secret</span>' : ""}<time>${ago(e.at)}</time></div>
      <div class="chk">${esc(e.check)}</div>
      <div class="r"><span class="v ${e.netS > 0 ? "ok" : "no"}">${verdictText(e)}</span>${netSyms(e)}
        ${pend ? `<button type="button" class="btn danger" data-threat="${e.id}">Spend ${tl.thr ? `${tl.thr} Threat` : ""}${tl.thr && tl.des ? " + " : ""}${tl.des ? `${tl.des} Despair` : ""}</button>` : ""}</div>
      ${spent ? `<div class="spent">${spent}</div>` : ""}</div></li>`;
  }
  function renderFeed() {
    const pend = pendingThreat();
    $("p-feed").innerHTML = `<header><h2 id="h-feed">Roll feed${gm() && pend ? ` <span class="badge" title="Rolls with Threat to spend">${pend}</span>` : ""}</h2>
      ${gm() ? `<div class="hdacts"><button type="button" class="btn primary" data-act="gm-roll">GM roll</button><button type="button" class="btn" data-act="secret-roll">Secret check</button></div>` : ""}</header>
      <div class="pbody">${gm() && pend ? `<p class="note" style="margin:0">Red rolls have Threat or Despair for you to spend.</p>` : ""}<ul class="feed" aria-live="off">${S.feed.slice(0, 40).map(feedItem).join("")}</ul></div>`;
    S.freshId = 0;
  }

  // ---------- Drawer ----------
  function openDrawer(kind, data) {
    S.d = { open: true, kind, ...data };
    renderDrawer();
    $("scrim").hidden = false; $("drawer").hidden = false;
    const f = $("drawer").querySelector(".rollbtn, .search, .x"); if (f) f.focus();
  }
  function closeDrawer() { S.d = { open: false }; $("scrim").hidden = true; $("drawer").hidden = true; $("drawer").innerHTML = ""; }
  function renderDrawer() {
    if (!S.d.open) return;
    const [title, meta, body] = { pc: drawerPC, roll: drawerRoll, threat: drawerThreat, addnpc: drawerAddNpc, link: drawerLink, npc: drawerNpc }[S.d.kind]();
    $("drawer").innerHTML = `<div class="grab" aria-hidden="true"></div><header><div><h2 id="d-title">${title}</h2><div class="meta">${meta}</div></div>
      <button type="button" class="x" data-act="close" aria-label="Close">×</button></header><div class="body">${body}</div>`;
  }

  function drawerPC() {
    const p = pcById(S.d.id), secret = isSecretOb(p);
    const chars = `<div class="chars">${Object.entries(p.chars).map(([k, v]) => `<div><b>${v}</b><span>${k}</span></div>`).join("")}</div>`;
    const crits = p.crits.length ? p.crits.map((c) => `<div class="item"><div class="row1"><b>${esc(c.name)}</b><span class="tag">rolled ${c.rolled}</span>${cite(c.source)}</div><p>${rich(c.text)}</p></div>`).join("") : `<p class="note" style="margin:0">No critical injuries.</p>`;
    const ob = `<div class="item"><div class="row1"><b>${secret && !gm() ? "Secret" : esc(p.obType.name)}</b>${secret && !gm() ? "" : `<span class="tag">Magnitude ${p.ob.mag}</span>`}<span class="tag">${p.obType.kind}</span></div>
      ${gm() ? `<p>Attachment: <b>${esc(p.ob.att)}</b></p><p>${esc(p.ob.why)}</p>` : secret ? `<p class="note" style="margin:0">Only ${esc(p.name.split(" ")[0])} and the GM know what it is.</p>` : `<p>Tied to ${esc(p.ob.att)}.</p>`}</div>`;
    const s0 = gmBlock(`<p class="meta" style="margin:0">${esc(p.s0.q)}</p><p style="margin:4px 0">${esc(p.s0.a)}</p><p class="note" style="margin:0">Tied to ${esc(p.s0.act)}</p>`);
    const checks = gm() ? `<div><h4>Secret check for ${esc(p.name.split(" ")[0])}</h4><div class="chips">${["Perception", "Vigilance", "Cool", "Discipline"].map((s) =>
      `<button type="button" class="chip" data-secret="${p.id}|${s}">${s} <span style="display:inline-flex">${miniPool(pcPool(p, s))}</span></button>`).join("")}</div></div>` : "";
    return [esc(p.name), `${p.archetype.name} ${cite(p.archetype.source)} · ${p.careerE.name} ${cite(p.careerE.source)}`,
      `${chars}<dl class="kv"><dt>Wounds</dt><dd>${p.wounds} / ${tWounds(p)} <span class="note">(${p.archetype.wounds} + Brawn ${p.chars.Br}${p.bonus ? ` + ${p.bonus.wounds} ${p.bonus.why.split(",")[0]}` : ""})</span></dd>
        <dt>Strain</dt><dd>${p.strain} / ${tStrain(p)}${obMod(p) ? ` <span class="tag warn">${obMod(p)} Obligation</span>` : ""}</dd><dt>Soak · Def</dt><dd>${p.soak} · ${p.def[0]}/${p.def[1]}</dd></dl>
       <div><h4>Critical injuries</h4><div style="display:grid;gap:6px">${crits}</div></div>
       <div><h4>${secret ? "Secret" : "Obligation"}</h4>${ob}</div>${s0 ? `<div><h4>Session 0</h4>${s0}</div>` : ""}${checks}
       <div><button type="button" class="btn" data-act="open-sheet" data-pc="${p.id}">Open full sheet</button></div>`];
  }

  // GM rolls: NPC checks post openly; secret checks for a PC post as "GM made a secret check" to players.
  function openRoll(check, pool) { openDrawer("roll", { check, pool, base: { ...pool }, ladder: check.ladder ?? 2, result: null, secret: !!check.secret }); applyLadder(S.d.ladder); renderDrawer(); }
  function applyLadder(i) { S.d.ladder = i; S.d.pool.difficulty = LADDER[i].n; S.d.pool.challenge = 0; }
  const dTotal = () => ORDER.reduce((n, t) => n + S.d.pool[t], 0);
  function drawerRoll() {
    const c = S.d.check;
    return [esc(c.label), esc(c.meta), S.d.result ? rollResultHTML() : rollPoolHTML()];
  }
  function rollPoolHTML() {
    const p = S.d.pool;
    const pos = ORDER.filter((t) => DICE[t].pos).flatMap((t) => Array(p[t]).fill(t)), neg = ORDER.filter((t) => !DICE[t].pos).flatMap((t) => Array(p[t]).fill(t));
    const btn = (t) => `<button type="button" class="die" data-remove="${t}" title="Remove a ${DICE[t].name} die">${dieSVG(t, null, "Remove " + DICE[t].name + " die")}</button>`;
    const gmPts = S.story.length - playerPoints();
    return `<div class="tray" aria-label="Pool. Tap a die to remove it.">${dTotal() ? pos.map(btn).join("") + (pos.length && neg.length ? '<span class="vs">VS</span>' : "") + neg.map(btn).join("") : '<span class="meta">Empty pool</span>'}</div>
      <div><span class="label">Difficulty</span><div class="chips" style="margin-top:6px">${LADDER.map((l, i) =>
        `<button type="button" class="chip" data-ladder="${i}" aria-pressed="${S.d.ladder === i && p.difficulty + p.challenge === l.n}">${l.name}<span class="dp">${"<i></i>".repeat(l.n)}</span></button>`).join("")}</div></div>
      <div class="chips">
        <button type="button" class="chip" data-add="ability">${dieSVG("ability", null, "Ability")} + Ability</button>
        <button type="button" class="chip" data-add="boost">${dieSVG("boost", null, "Boost")} + Boost</button>
        <button type="button" class="chip" data-add="setback">${dieSVG("setback", null, "Setback")} + Setback</button>
        <button type="button" class="chip" data-up="ability">Upgrade ability</button>
        <button type="button" class="chip" data-up="difficulty">Upgrade difficulty</button>
        ${S.d.check.npc ? `<button type="button" class="chip" data-act="sp-upgrade" ${gmPts ? "" : "disabled"}>GM story point: upgrade (${gmPts} left)</button>` : ""}</div>
      <label class="toggle"><input type="checkbox" id="secret" ${S.d.secret ? "checked" : ""}> Secret check: players see that you rolled, not the result</label>
      <button type="button" class="rollbtn" data-act="roll" ${dTotal() && !Dice3D.busy ? "" : "disabled"}>Roll</button>`;
  }
  function rollResultHTML() {
    const r = S.d.result;
    const n = (s, count, name) => count ? `<span class="n">${sym(s)}<b>${count}</b> ${name}</span>` : "";
    return `<div class="verdict"><h3 class="${r.success ? "ok" : "no"}">${verdictText(r)}</h3>${S.d.secret ? '<p><span class="lock">Secret · GM only</span></p>' : ""}</div>
      <div class="net">${r.netS > 0 ? n("s", r.netS, "Success") : n("f", -r.netS, "Failure")}${r.netA > 0 ? n("a", r.netA, "Advantage") : n("t", -r.netA, "Threat")}${n("r", r.triumph, "Triumph")}${n("d", r.despair, "Despair")}</div>
      <div class="rolled${r.tumble ? " tumble" : ""}" aria-label="Dice rolled">${r.dice.map((d) => dieSVG(d.type, d.face)).join("")}</div>
      <p class="note" style="margin:0">Posted to the roll feed${S.d.secret ? " as a secret check" : ""}.</p>
      <div class="chips"><button type="button" class="btn" data-act="again">Roll again</button><button type="button" class="btn" data-act="close">Done</button></div>`;
  }
  function doRoll() {
    if (!dTotal() || Dice3D.busy) return;
    const types = poolList(S.d.pool);
    const done = (dice, tumble) => {
      const r = scoreDice(dice), c = S.d.check;
      S.d.result = { ...r, tumble };
      post({ who: c.npc ? "npc" : "gm", npc: c.npc, check: c.label, netS: r.netS, netA: r.netA, triumph: r.triumph, despair: r.despair, secret: S.d.secret, tDone: true });
      $("live").textContent = verdictText(r);
      renderDrawer(); renderFeed(); renderTabs();
    };
    if (Dice3D.ok && !window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      $("drawer").querySelector(".rollbtn").disabled = true;
      Dice3D.roll(types, (dice) => done(dice, false));
    } else done(instantRoll(types), true);
  }

  function drawerThreat() {
    const e = S.feed.find((x) => x.id === S.d.id), w = whoOf(e), tl = threatLeft(e);
    const pay = (o) => (o.despair ? (tl.des >= o.despair ? { paidT: 0, paidD: o.despair } : null)
      : tl.thr >= o.threat ? { paidT: o.threat, paidD: 0 } : o.orDespair && tl.des >= 1 ? { paidT: 0, paidD: 1 } : null);
    const cost = (o) => (o.despair ? sym("d", o.despair) : sym("t", o.threat));
    const opts = THREAT_OPTS.map((o, i) => `<button type="button" class="opt" data-spendt="${i}" ${pay(o) ? "" : "disabled"}><span class="cost">${cost(o)}</span><span>${rich(o.label)}</span></button>`).join("");
    S.d.pay = pay;
    return [`Spend Threat`, `${esc(w.name)} · ${esc(e.check)}`,
      `<div class="net"><span class="n">${sym("t")}<b>${tl.thr}</b> Threat left</span>${e.despair ? `<span class="n">${sym("d")}<b>${tl.des}</b> Despair left</span>` : ""}</div>
      <div>${opts}<p class="note">Options from Table I.6–2 ${cite({ book: "crb", page: 104 })}. What you pick shows on ${esc(w.name.split(" ")[0])}'s roll.</p></div>
      ${e.spentT.length ? `<div class="spent" style="display:flex;flex-wrap:wrap;gap:6px">${e.spentT.map((s) => `<span class="tag gm">${esc(s.label)}</span>`).join("")}<button type="button" class="linkbtn" data-act="undo-t">Undo last</button></div>` : ""}
      <div class="chips"><button type="button" class="btn primary" data-act="t-done">Done</button><button type="button" class="linkbtn" data-act="t-skip">Leave the rest unspent</button></div>`];
  }
  function spendThreat(i) {
    const e = S.feed.find((x) => x.id === S.d.id), o = THREAT_OPTS[i], p = S.d.pay(o);
    if (!p) return;
    e.spentT.push({ label: o.label.replace(/\.$/, ""), ...p });
    if (/suffers 1 strain/.test(o.label)) { const pc = pcById(e.who); if (pc) { pc.strain++; S.flash = pc.id; renderParty(); } }
    if (!threatLeft(e).any) e.tDone = true;
    renderDrawer(); renderFeed(); renderTabs();
  }

  function drawerAddNpc() {
    const q = (S.d.q || "").toLowerCase();
    const order = { minion: 0, rival: 1, nemesis: 2 };
    const list = CONTENT.adversaries.filter((a) => !q || a.name.toLowerCase().includes(q) || a.type.includes(q))
      .sort((a, b) => order[a.type] - order[b.type] || a.name.localeCompare(b.name));
    S.d.counts = S.d.counts || {};
    const row = (a) => {
      const c = S.d.counts[a.id] || 3;
      return `<div class="pick"><span><b>${esc(a.name)}</b> <span class="tag">${a.type}</span> ${cite(a.source)}<small>Wounds ${a.wounds}${a.strain ? ` · Strain ${a.strain}` : ""} · Soak ${a.soak} · ${esc(a.setting)}</small></span>
        <span class="stepper">${a.type === "minion" ? `<button type="button" class="btn step" data-cnt="${a.id}" data-by="-1" aria-label="Fewer">&minus;</button><b>${c}</b><button type="button" class="btn step" data-cnt="${a.id}" data-by="1" aria-label="More">+</button>` : ""}
        <button type="button" class="btn primary" data-addnpc="${a.id}">Add</button></span></div>`;
    };
    return ["Add NPCs", `${CONTENT.adversaries.length} adversaries in your library`,
      `<label class="sr" for="npcq">Search adversaries</label><input class="search" id="npcq" type="search" placeholder="Search: name, minion, rival, nemesis" value="${esc(S.d.q || "")}" autocomplete="off">
       <div id="npclist">${list.map(row).join("") || '<p class="note">No match.</p>'}</div>
       <p class="note">Minions act as one group with a shared wound threshold; each one that falls lowers the group's skill ranks.</p>`];
  }
  function drawerNpc() {
    const n = npcByUid(S.d.id), a = n.adv;
    const chars = `<div class="chars">${Object.entries(KEY_OF).map(([k, s]) => `<div><b>${a.characteristics[k]}</b><span>${s}</span></div>`).join("")}</div>`;
    const sk = (a.skills || []).map((s) => `${esc(s.name)}${s.rank ? " " + s.rank : ""}`).join(", ");
    return [esc(n.label), `${esc(a.name)} · ${a.type} ${cite(a.source)}`,
      `${chars}<dl class="kv"><dt>Wounds</dt><dd>${a.wounds}${a.type === "minion" ? ` each, ${npcThreshold(n)} for the group` : ""}</dd>${a.strain ? `<dt>Strain</dt><dd>${a.strain}</dd>` : ""}
        <dt>Soak · Def</dt><dd>${a.soak} · ${a.defense.melee}/${a.defense.ranged}</dd><dt>Skills</dt><dd>${sk || "None"}</dd></dl>
       ${a.talents && a.talents !== "None." ? `<div class="item"><b>Talents</b><p>${rich(a.talents)}</p></div>` : ""}
       ${a.abilities && a.abilities !== "None." ? `<div class="item"><b>Abilities</b><p>${rich(a.abilities)}</p></div>` : ""}
       <div class="item"><b>Equipment</b><p>${rich(a.equipment)}</p></div><p class="note" style="margin:0">${rich(a.text)}</p>
       <div><button type="button" class="btn danger" data-npcremove="${n.uid}">Remove from scene</button></div>`];
  }
  function drawerLink() {
    const [kind, id] = S.d.id.split(":"), x = (kind === "npc" ? NPCS : CORPS).find((y) => y.id === id);
    return [esc(x.name), kind === "npc" ? esc(x.role) : `Corp ${cite(x.source)}`,
      `<div class="item"><span class="label">${kind === "npc" ? "What players know" : "Public role"}</span><p>${esc(x.pub)}</p></div>
       ${gmBlock(`<span class="label">${kind === "npc" ? "Truth" : "Secret agenda"}</span><p style="margin:0">${esc(x.gm)}</p>`)}
       <p class="note">Sample text. You write these in the <a href="campaign-manager.html">campaign manager</a>, with the betrayal ledger and Session 0 questions.</p>`];
  }

  // ---------- Live demo: players rolling and taking hits at the table ----------
  function simulate() {
    if (!S.live || document.hidden) return;
    const p = PARTY[rand(PARTY.length)];
    if (rand(10) < 7) {
      const [skill, what] = p.sig[rand(p.sig.length)];
      const pool = { ...pcPool(p, skill), difficulty: 2 }, r = scoreDice(instantRoll(poolList(pool)));
      post({ who: p.id, check: `${skill}: ${what.toLowerCase()}`, netS: r.netS, netA: r.netA, triumph: r.triumph, despair: r.despair,
        spent: r.netA > 0 ? ["Recover 1 strain."].slice(0, r.netA) : [] });
      $("live").textContent = `${p.name} rolled ${skill}: ${verdictText(r)}`;
    } else {
      const key = rand(2) ? "strain" : "wounds", by = rand(3) + 1;
      p[key] += by; S.flash = p.id;
      sysPost(`${p.name} ${key === "wounds" ? `took ${by} wound${by > 1 ? "s" : ""}` : `suffered ${by} strain`} (from their sheet)`);
      renderParty();
    }
    renderFeed(); renderTabs();
  }

  function renderAll() { renderTop(); renderParty(); renderSession(); renderInit(); renderFeed(); renderTabs(); renderDrawer(); }

  // ---------- Events ----------
  document.addEventListener("click", (e) => {
    const t = e.target.closest("button");
    if (!t) {
      if (S.spMenu && !e.target.closest("#sp-menu")) { S.spMenu = false; renderTop(); }
      if (e.target.id === "scrim") closeDrawer();
      return;
    }
    const d = t.dataset;
    if (d.tok !== undefined) { if (gm() && S.story[Number(d.tok)] === "gm") { S.spMenu = !S.spMenu; renderTop(); } return; }
    if (S.spMenu && !t.closest("#sp-menu")) { S.spMenu = false; renderTop(); }
    if (d.view) { S.preview = d.view === "player"; if (S.d.open && ["roll", "threat", "addnpc"].includes(S.d.kind)) closeDrawer(); renderAll(); return; }
    if (d.tab) { S.tab = d.tab; if (d.tab === "feed") S.unread = 0; renderTabs(); window.scrollTo({ top: 0 }); return; }
    if (d.adj) { const p = pcById(d.pc); p[d.adj] = Math.max(0, p[d.adj] + Number(d.by)); renderParty(); renderDrawer(); return; }
    if (d.pcopen) { openDrawer("pc", { id: d.pcopen }); return; }
    if (d.link) { openDrawer("link", { id: d.link }); return; }
    if (d.fill) { S.init.filled[S.init.cur] = d.fill; sysPost(`${pcById(d.fill).name} takes PC slot ${S.init.cur + 1}`); renderInit(); renderFeed(); return; }
    if (d.surprise) { S.init.surprised = S.init.surprised === d.surprise ? false : d.surprise; renderInit(); return; }
    if (d.hit) { hitNpc(d.hit, false); return; }
    if (d.hitstrain) { hitNpc(d.hitstrain, true); return; }
    if (d.npcopen) { openDrawer("npc", { id: d.npcopen }); return; }
    if (d.npcremove) { npcs.splice(npcs.findIndex((n) => n.uid === d.npcremove), 1); closeDrawer(); renderInit(); return; }
    if (d.npcroll) { const n = npcByUid(d.npcroll), s = (n.adv.skills || []).find((x) => /Ranged|Melee|Brawl|Gunnery/.test(x.name)) || (n.adv.skills || [])[0];
      openRoll({ label: `${n.label}: ${s ? s.name : "check"}`, meta: `${n.adv.type} · ${s ? s.name : ""}`, npc: n.label }, s ? npcPool(n, s.name) : emptyPool()); return; }
    if (d.npcskill) { const [uid, skill] = d.npcskill.split("|"), n = npcByUid(uid);
      openRoll({ label: `${n.label}: ${skill}`, meta: `${n.adv.type}${n.adv.type === "minion" ? ` · ${minionsLeft(n)} in the group, rank ${npcSkillRank(n, skill)}` : ""}`, npc: n.label }, npcPool(n, skill)); return; }
    if (d.secret) { const [id, skill] = d.secret.split("|"), p = pcById(id);
      openRoll({ label: `${skill} for ${p.name.split(" ")[0]}`, meta: `Secret check · ${CHAR_NAMES[charOf(skill)]} ${p.chars[charOf(skill)]} · Rank ${p.ranks[skill] || 0}`, secret: true }, pcPool(p, skill)); return; }
    if (d.threat) { openDrawer("threat", { id: Number(d.threat) }); return; }
    if (d.spendt !== undefined) { spendThreat(Number(d.spendt)); return; }
    if (d.addnpc) { const a = find("adversaries", d.addnpc), n = makeNpc(d.addnpc, null, (S.d.counts || {})[d.addnpc] || 3); npcs.push(n);
      toast(`Added ${n.label}${a.type === "minion" ? ` ×${n.count}` : ""}${S.init.rolled ? ". Re-roll initiative to give it a slot." : ""}`); renderInit(); return; }
    if (d.cnt) { S.d.counts[d.cnt] = Math.max(1, Math.min(10, (S.d.counts[d.cnt] || 3) + Number(d.by))); renderDrawer(); return; }
    if (d.remove) { S.d.pool[d.remove]--; renderDrawer(); return; }
    if (d.add) { S.d.pool[d.add]++; renderDrawer(); return; }
    if (d.ladder !== undefined) { applyLadder(Number(d.ladder)); renderDrawer(); return; }
    if (d.up) { const [lo, hi] = d.up === "ability" ? ["ability", "proficiency"] : ["difficulty", "challenge"];
      if (S.d.pool[lo] > 0) { S.d.pool[lo]--; S.d.pool[hi]++; } else S.d.pool[lo]++; renderDrawer(); return; }
    const act = d.act;
    if (act === "close") closeDrawer();
    else if (act === "roll") doRoll();
    else if (act === "again") { S.d.result = null; S.d.pool = { ...S.d.base }; applyLadder(S.d.check.ladder ?? 2); renderDrawer(); }
    else if (act === "sp-upgrade") { if (flipGm(`upgraded ${S.d.check.label}`)) { const p = S.d.pool; if (p.ability > 0) { p.ability--; p.proficiency++; } else p.ability++; renderDrawer(); } }
    else if (act === "sp-complication") { if (flipGm("introduced a complication")) toast("The point goes to the players. Describe the complication."); }
    else if (act === "sp-npc") { S.spMenu = false; renderTop(); S.tab = "init"; renderTabs(); toast("Pick an NPC skill, then tap the story point chip in the roller."); }
    else if (act === "sp-cancel") { S.spMenu = false; renderTop(); }
    else if (act === "ob-roll") rollObligation();
    else if (act === "ob-reset") { S.obRoll = null; S.due = null; sysPost("GM undid the Obligation check"); renderAll(); }
    else if (act === "new-session") newSession();
    else if (act === "next") nextTurn();
    else if (act === "roll-init" || act === "reroll") rollInitiative();
    else if (act === "end-enc") { S.init.rolled = false; S.init.slots = []; sysPost("Encounter over. PCs recover strain on their sheets."); toast("Encounter over: players roll strain recovery on their sheets"); renderInit(); renderFeed(); renderTabs(); }
    else if (act === "add-npc") openDrawer("addnpc", { q: "" });
    else if (act === "gm-roll") openRoll({ label: "GM roll", meta: "Build any pool", ladder: 2 }, { ...emptyPool(), ability: 2 });
    else if (act === "secret-roll") { S.tab = "party"; renderTabs(); openDrawer("pc", { id: "prance" }); toast("Pick the PC, then the skill to check in secret"); }
    else if (act === "t-done" || act === "t-skip") { const ev = S.feed.find((x) => x.id === S.d.id); ev.tDone = true; closeDrawer(); renderFeed(); renderTabs(); }
    else if (act === "undo-t") { const ev = S.feed.find((x) => x.id === S.d.id); ev.spentT.pop(); ev.tDone = false; renderDrawer(); renderFeed(); renderTabs(); }
    else if (act === "open-sheet") { if (d.pc === "prance") window.location.href = "character-sheet.html"; else toast("Only Prance has a full sample sheet in the prototypes."); }
    else if (t.id === "live-btn") { S.live = !S.live; renderTop(); toast(S.live ? "Live demo on: players roll every few seconds" : "Live demo paused"); }
  });
  document.addEventListener("input", (e) => {
    if (e.target.id === "notes") { S.notes = e.target.value; try { localStorage.setItem("ttg-gm-notes", S.notes); } catch (err) {} }
    if (e.target.id === "npcq") { S.d.q = e.target.value; const pos = e.target.selectionStart; renderDrawer(); const q = $("npcq"); q.focus(); q.setSelectionRange(pos, pos); }
    if (e.target.id === "secret") S.d.secret = e.target.checked;
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && S.spMenu) { S.spMenu = false; renderTop(); return; }
    if (e.key === "Escape" && S.d.open) closeDrawer();
    if (e.key === "Enter" && e.target.dataset && e.target.dataset.hitin) hitNpc(e.target.dataset.hitin, false);
  });
  let wasDesk = isDesk();
  window.addEventListener("resize", () => { if (isDesk() !== wasDesk) { wasDesk = isDesk(); renderAll(); } });
  setInterval(simulate, 15000);
  setInterval(() => { if (!S.d.open || S.d.kind !== "threat") renderFeed(); }, 60000);

  Dice3D.init();
  renderAll();
})();
