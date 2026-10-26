(() => {
/*CONTENT*/
  // ---------- Library (injected by bb proto-build: factions for citations) ----------
  const BOOK = { crb: "CRB", sotb: "SotB", "sotb-guns": "Gun Locker", epg: "EPG", house: "House rule" };
  const find = (type, id) => CONTENT[type].find((e) => e.id === id);
  const cite = (src) => (src ? `<span class="cite">${BOOK[src.book] || src.book}${src.page ? ` p.${src.page}` : ""}</span>` : "");
  const esc = (t) => String(t ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));

  // ---------- The table (same sample party and Debts as the GM dashboard) ----------
  const PARTY = [
    { id: "prance", name: "Prance Galavent", color: "var(--pc-1)", role: "Pilot", debt: 10, creditor: "weyland-consortium", owes: "Weyland Consortium" },
    { id: "krum", name: "Krum", color: "var(--pc-2)", role: "Runner", debt: 5, creditor: "jinteki", owes: "Jinteki" },
    { id: "behnsun", name: "Behnsun Payne", color: "var(--pc-3)", role: "Doc", debt: 10, creditor: "jinteki", owes: "Ama Payne's Jinteki crèche contract" },
    { id: "fizz", name: "Fizz", color: "var(--pc-4)", role: "Gun", debt: 15, creditor: "haas-bioroid", owes: "Haas-Bioroid" },
    { id: "adarius", name: "Adarius", color: "var(--pc-5)", role: "Face", debt: 5, creditor: null, owes: "Orgcrime: the Tsang family" },
  ];
  const pcById = (id) => PARTY.find((p) => p.id === id);
  const first = (p) => p.name.split(" ")[0];
  // Session 0 seats players in this order; "the player on your left" is the next one round the table.
  const leftOf = (id) => PARTY[(PARTY.findIndex((p) => p.id === id) + 1) % PARTY.length].id;

  // ---------- Acts (from "Manifest Destiny — Campaign Arc") ----------
  const ACTS = [
    { id: "ki", roman: "I", kanji: "起", name: "Ki", title: "The voyage", length: "about 4–6 sessions",
      pub: "Months crossing the solar system toward the gate beyond Neptune. A town of 3,000 in a sealed box.",
      gm: "Let the players know the town, the corp reps and the ship before anything goes wrong, so every later betrayal lands on people they know.",
      end: "The crossing: the ship shakes, the lights fail, and for eleven seconds everyone aboard dreams the same thing. The Meridian's beacon is already pinging. The clocks disagree about how long it took.",
      threads: [["ki-cryo", "A death in cryo"], ["ki-sab", "The saboteur"], ["ki-bio", "The bioroid who asks a question"], ["ki-nbn", "NBN's story"],
        ["ki-town", "Town life: a wedding, a still, an election, a kid"], ["ki-port", "The last port"]] },
    { id: "sho", roman: "II", kanji: "承", name: "Shō", title: "The search", length: "",
      pub: "The Concord follows the Meridian's beacon into an unknown system.",
      gm: "The rescue slowly falls apart as each corp's agenda surfaces.",
      threads: [["sho-empty", "The empty Meridian"], ["sho-surv", "The survivors by the ruins"], ["sho-leak", "Agendas leak"],
        ["sho-split", "Three factions pull apart: settle, strip, or leave"], ["sho-offers", "Off-book offers to clear Debt"]] },
    { id: "ten", roman: "III", kanji: "転", name: "Ten", title: "The twist", length: "",
      pub: "", gm: "One reveal that changes what everything before it meant. Seed it from the first session.",
      threads: [] },
    { id: "ketsu", roman: "IV", kanji: "結", name: "Ketsu", title: "Reconciliation", length: "",
      pub: "", gm: "Who does this new world belong to? Every earlier thread comes together.",
      threads: [["ke-mutiny", "A mutiny or emergency election aboard the Concord"], ["ke-gate", "Keep the gate open, seal it, or hand it to one corp"],
        ["ke-broadcast", "What NBN's last broadcast home will show"]] },
  ];
  const actById = (id) => ACTS.find((a) => a.id === id);
  const actIdx = (id) => ACTS.findIndex((a) => a.id === id);
  const actLabel = (id) => { const a = actById(id); return a ? `Act ${a.roman} · ${a.name}` : "No act"; };
  const TWISTS = [
    { n: 1, title: "The Meridian wasn't lost; it was abandoned.", text: "The consortium read the full transmission fifteen years ago and buried it. The rescue is a retrieval mission, and the survivors are witnesses." },
    { n: 2, title: "Time ran differently through the gate.", text: "For the survivors it's been 80 years. They're the original crew's grandchildren, and they see the Concord as an invasion." },
    { n: 3, title: "The Concord was never meant to go home.", text: "The gate is one-way, or about to close. The colonists are a second stage they were never told about; their indenture starts on arrival." },
  ];
  // How the voyage sets the twist up: each seed is a ledger secret; "for" marks seeds that only matter to one twist.
  const SEEDS = [
    { ledger: "saboteur", text: "The saboteur knew the truth all along" },
    { ledger: "gate", text: "The shared dream at the crossing: first sign of the gate-builders" },
    { ledger: "clocks", text: "The mismatched clocks", for: 2 },
    { ledger: "cryo", text: "The edited cryo log: what Jinteki is really storing" },
    { ledger: "transmission", text: "A few seconds of the real transmission, denied by every corp" },
  ];

  // ---------- Factions ----------
  const CORPS = [
    { id: "weyland-consortium", short: "Weyland", share: "Built the hull and holds the charter, so it legally owns whatever the expedition claims.", pub: "Captain and charter holder", breaks: "Shō → Ten", ledger: "weyland" },
    { id: "haas-bioroid", short: "Haas-Bioroid", share: "Supplies the bioroid workforce that keeps the ship running.", pub: "Labor and engineering", breaks: "Ki → Shō", ledger: "override" },
    { id: "jinteki", short: "Jinteki", share: "Runs medical, life support, and the cryo and clone banks.", pub: "Medical and cryo", breaks: "Ten", ledger: "clones" },
    { id: "nbn", short: "NBN", share: "Owns comms, sensors, and exclusive broadcast rights to the \"greatest rescue in history\".", pub: "Comms and broadcast", breaks: "Any act, above all Ketsu", ledger: "edit" },
  ].map((c) => ({ ...(find("factions", c.id) || { name: c.short }), ...c }));
  const POWERS = [
    { id: "town", name: "The town", pub: "3,000 colonists with an elected council. They want to settle here.", ledger: "council" },
    { id: "survivors", name: "The Meridian survivors", pub: "Living beside the gate-builder ruins they've been studying. They want everyone to leave.", ledger: "scientist" },
    { id: "human-first", name: "Human First", pub: "Anti-bioroid extremists. Blamed for the sabotage in Ki.", gm: "A red herring: the saboteur was a Concord officer.", src: "activist-and-terrorist-groups" },
  ];
  const ownerName = (id) => (CORPS.find((c) => c.id === id) || {}).short || (POWERS.find((p) => p.id === id) || {}).name || (npcById(id) || {}).name || { gate: "The gate", consortium: "The consortium" }[id] || id;

  // ---------- Session 0 (from "Manifest Destiny — Session 0", a Wildsea-style question deck) ----------
  const QSETS = [
    { id: "personal", name: "Personal", how: "Each player draws two and answers one. Answers become canon and the GM's hooks." },
    { id: "crew", name: "Crew", how: "Each player answers one about the player on their left." },
    { id: "world", name: "World", how: "Players define the frontier. The GM keeps what's true." },
    { id: "ship", name: "Ship", how: "One per player while building the ship." },
  ];
  // "fit" flags questions written for the open-frontier pitch that clash with the Concord arc (one gate, first crossing).
  const Q = [
    ["p1", "personal", "You've seen the inside of a corporate contract-labor camp. What number did they tattoo, stamp or chip into you, and why haven't you had it removed?"],
    ["p2", "personal", "Out past the last gate, you heard something on an empty comms channel. What did it say, and why did it use your name?",
      "There's only one gate and nobody aboard has crossed it. Try \"On the voyage out\" or \"The night the Meridian went through\"."],
    ["p3", "personal", "You once left someone behind in vacuum to save yourself. Who were they, and what do you tell people happened?"],
    ["p4", "personal", "Which corporation owns something that used to be part of you: a limb, a memory, a patent, a face?"],
    ["p5", "personal", "What did the frontier promise you before you came out here, and when did you realize it was a lie?"],
    ["p6", "personal", "You keep a small object from a colony that no longer exists. What is it, and who erased the colony?"],
    ["p7", "personal", "What do you do with your hands when the ship is quiet, and what are you trying not to think about?"],
    ["p8", "personal", "Someone back in the Core still holds a funeral for you every year. Why do they think you're dead?"],
    ["p9", "personal", "You signed something you didn't read. What did it turn out to say?"],
    ["p10", "personal", "When you look at a bioroid, what do you feel that you'd never say out loud?"],
    ["p11", "personal", "You've touched gate-builder stone with your bare hand. What has been different about you since?",
      "No one has reached the ruins before Shō. Try a fragment the Meridian sent home, or a relic on the black market."],
    ["p12", "personal", "Who is the last person who trusted you completely, and what happened to them?"],
    ["c1", "crew", "What did you see them do on their first job with this crew that you still haven't asked about?"],
    ["c2", "crew", "Which of their habits makes you sure they're hiding something?"],
    ["c3", "crew", "They saved your life once. What did it cost them, and have you paid it back?"],
    ["c4", "crew", "You've heard them talking in their sleep. What name do they say?"],
    ["c5", "crew", "If a corp offered you a bounty on them, what is the number where you'd hesitate?"],
    ["c6", "crew", "What do they cook, play or sing that makes the ship feel like home for an hour?"],
    ["w1", "world", "Name a frontier station where everyone goes to disappear. What rule is never broken there?"],
    ["w2", "world", "What does the Drift crime boss who holds the crew's Debt keep in a jar on their desk?",
      "The arc has the corps hold the Debts. Reword for whichever corp or fixer holds the crew's."],
    ["w3", "world", "Describe a gate that doesn't open anymore. What's still waiting on the other side of it?", "There's only one gate. Swap for a sealed lock or a dead station the Concord passes."],
    ["w4", "world", "Name a Free Claims colony the crew has drunk at. What did the corps do to the last colony nearby?"],
    ["w5", "world", "What do spacers leave on a derelict before stripping it, and what happens to people who forget?"],
    ["w6", "world", "Name a rumor about the gate-builders that every prospector has heard. Which part is true? (Only the GM knows.)"],
    ["h1", "ship", "Who owned this ship before you, and where did you find their last log entry?"],
    ["h2", "ship", "There's a sealed compartment none of you has opened. What do you hear from it during long burns?"],
    ["h3", "ship", "What corporation's paint is still visible under the current coat?"],
    ["h4", "ship", "Someone died aboard. Where, and what does the crew avoid doing in that spot?"],
    ["h5", "ship", "The ship has been through a gate that isn't on any chart. How do you know?", "There's only one gate. Try a route or jump that isn't on any chart."],
    ["h6", "ship", "What part of the ship was salvaged from a vessel that was destroyed with all hands?"],
  ].map(([id, set, text, fit]) => ({ id, set, text, fit, n: Number(id.slice(1)) }));
  const qById = (id) => Q.find((q) => q.id === id);
  // Questions where the answer has a hidden truth for the GM to decide.
  const TRUTH_Q = { w6: "Which part is true", h2: "What's actually in the compartment" };
  const DIALS = [
    ["grit", "Grit", "Swashbuckling caper", "Every bullet and credit counts", 4],
    ["cruelty", "Corporate cruelty", "Cartoon villains", "Banal, believable atrocity", 3],
    ["weird", "Alien weirdness", "Background flavor", "Cosmic horror front and center", 2],
    ["conflict", "Crew conflict", "Found family", "Knives out, secrets kept", 2],
    ["moral", "Moral lines", "Clear heroes", "Everyone is compromised", 4],
  ];
  const THEMES = ["Indentured labor and debt bondage", "Bioroids and clones treated as property", "Body modification and losing parts of yourself",
    "Corporate violence against colonists, including civilians and children", "Isolation, vacuum death, being stranded", "Alien body horror and parasitism",
    "Addiction (stims, combat drugs)", "Betrayal inside the crew"];
  const WRAPUP = ["Sort the index cards into NPCs, places, objects and rumors", "Pick two personal answers to pay off in the first three sessions",
    "Give each creditor a face, a want and a deadline", "Decide what's in the ship's sealed compartment", "Decide which gate-builder rumor is true",
    "Type up lines and veils and share with the group", "Finalize character sheets and ship stats before session 1"];

  // ---------- Sample campaign: mid-Shō, the same night as the GM dashboard ----------
  const NPCS = [
    { id: "aldine", name: "Captain Marit Aldine", group: "officers", faction: "weyland-consortium", role: "Captain of the Concord",
      pub: "Runs the ship by the charter, to the letter.", gm: "Carries sealed orders to open on arrival: the colonists' contracts are void and the system is company property.",
      arc: { ki: "Distant, fair, by the book.", sho: "Opens the sealed orders.", ten: "Enforces them.", ketsu: "Target of the mutiny, or its last holdout." } },
    { id: "vasko", name: "Imre Vasko", group: "officers", faction: "weyland-consortium", role: "Second officer, Concord bridge",
      pub: "Caught cutting the nav system before the gate. Says nobody should have come.", gm: "Read the Meridian's real transmission and tried to keep 3,000 people from reaching what's on the other side.",
      arc: { ki: "The saboteur: Ki's villain.", sho: "Hiding on the lower decks. Reaches out once the survivors are found.", ten: "The voice the crew dismissed, or the ally they protected.", ketsu: "Argues for sealing the gate." } },
    { id: "quill", name: "Dana Quill", group: "officers", faction: null, role: "Consortium liaison and the outriders' handler",
      pub: "Hands out the jobs and the credit. Always has another one.", gm: "Works for NBN. A failed rescue is the better story.",
      arc: { ki: "Friendly employer.", sho: "Sends the crew on jobs that go wrong in camera-friendly ways.", ten: "Exposed, or never.", ketsu: "Pitches the crew to NBN's last broadcast as villains." } },
    { id: "rook", name: "Rook", group: "corp", faction: "haas-bioroid", role: "Haas-Bioroid dock chief, cargo ring",
      pub: "Runs the cargo ring's docks. Owes no one, says everyone.", gm: "Skimming Haas-Bioroid salvage. Will sell the crew out to keep it quiet.",
      arc: { ki: "Useful fixer.", sho: "Stalls Weyland at Dock 9, for a price.", ten: "", ketsu: "" } },
    { id: "mx7", name: "MX-7", group: "corp", faction: "haas-bioroid", role: "Bioroid maintenance unit",
      pub: "Keeps asking about death, ownership, and whether the gate counts as \"outside\".", gm: "Gone independent: someone patched its Directives. It will ask the crew for help when the override wakes.",
      arc: { ki: "The bioroid who asks a question.", sho: "Freezes mid-task with the others; asks for help.", ten: "", ketsu: "Speaks for the bioroids in the vote." } },
    { id: "okafor", name: "Jules Okafor", group: "corp", faction: "nbn", role: "NBN camera crew",
      pub: "Friendly. Films everything the outriders do.", gm: "Streams the raw feed to an NBN buyer who pays for the crew's secrets.",
      arc: { ki: "The weekly segment.", sho: "Footage starts getting edited before it goes home.", ten: "", ketsu: "Holds the cut that decides heroes or terrorists." } },
    { id: "ilves", name: "Dr. Hana Ilves", group: "corp", faction: "jinteki", role: "Jinteki chief of cryo medicine",
      pub: "Called the death in pod 14 a malfunction.", gm: "Edited the pod log on orders. Knows the clone stock is grown from Meridian samples.",
      arc: { ki: "The death in cryo.", sho: "Asks the crew to bring back tissue from the survivors.", ten: "The clones wake.", ketsu: "" } },
    { id: "sato", name: "Agent Sato", group: "corp", faction: "weyland-consortium", role: "Weyland security",
      pub: "Wants the Meridian's black box.", gm: "Ordered to make the black box disappear, along with anyone who has heard it.",
      arc: { ki: "", sho: "Dock 9.", ten: "If the crew spared the drone at Dock 9, Sato remembers it.", ketsu: "" } },
    { id: "reyes", name: "Councilor Teodora Reyes", group: "town", faction: "town", role: "Elected head of the colonists' council",
      pub: "Won the council election on a promise of land for every family.", gm: "Will trade the survivors to Weyland in exchange for land titles.",
      arc: { ki: "The election.", sho: "Pushes to settle.", ten: "", ketsu: "Cuts the deal with Weyland." } },
    { id: "brandts", name: "Sami and Odette Brandt", group: "town", faction: "town", role: "Newlyweds, hydroponics crew",
      pub: "Married the night of the death in cryo. Half the ship came.", gm: "Odette is pregnant by Ketsu.",
      arc: { ki: "The wedding.", sho: "", ten: "", ketsu: "The stakes in the vote." } },
    { id: "gus", name: "Gus Ferrante", group: "town", faction: "town", role: "Runs the black-market still in hydroponics",
      pub: "Everyone's friend at 0200.", gm: "Hears everything. Sells some of it to Rook.",
      arc: { ki: "The still.", sho: "", ten: "", ketsu: "The still becomes the mutineers' meeting room." } },
    { id: "pip", name: "Pip Okonkwo", group: "town", faction: "town", role: "Fourteen. Wants to be an outrider.",
      pub: "Hid in the cargo ring to watch the outriders' ship.", gm: "Stows away on the outriders' ship in Shō.",
      arc: { ki: "The stowaway.", sho: "Stows away again, planetside.", ten: "", ketsu: "The kid who wanted to fly." } },
    { id: "marten", name: "Ilse Marten", group: "town", faction: "town", role: "Colonist, cryo pod 14", dead: true,
      pub: "Died in a sealed cryo pod.", gm: "Woke early and recognised a Meridian crew name on the clone-bank manifest.",
      arc: { ki: "The death in cryo.", sho: "", ten: "Her clone is aboard.", ketsu: "" } },
    { id: "marr", name: "Dr. Ines Marr", group: "meridian", faction: "survivors", role: "Meridian xenoarchaeologist",
      pub: "The first Meridian survivor the crew will meet.", gm: "Sabotaged the Meridian to stop the crew taking what they'd found home.",
      arc: { ki: "", sho: "Rescued.", ten: "The rescued scientist.", ketsu: "" } },
    { id: "calloway", name: "Wren Calloway", group: "meridian", faction: "survivors", role: "Speaker for the Meridian survivors",
      pub: "", gm: "Will do anything to make the Concord leave.",
      arc: { ki: "", sho: "Meets the crew at the ruins.", ten: "", ketsu: "Holds the ruins." } },
  ];
  const GROUPS = { officers: "Ship's officers", corp: "Corp reps", town: "Townsfolk", meridian: "Meridian" };
  const npcById = (id) => S.data.npcs.find((n) => n.id === id);
  const SAMPLE_MET = { aldine: 1, vasko: 1, quill: 1, rook: 1, mx7: 1, okafor: 1, ilves: 2, sato: 5, reyes: 2, brandts: 2, gus: 1, pip: 1, marten: 2 };

  // Betrayal ledger. state: hidden | hinted | revealed. clues[].s is the session it was planted in (null = not yet).
  const LEDGER = [
    { id: "saboteur", owner: "vasko", act: "ki", title: "The saboteur", truth: "Vasko read the Meridian's real transmission and tried to stop the voyage. Introduced as Ki's villain, later revealed as the one person trying to save everyone.",
      reveal: "Second Officer Vasko was the saboteur. Vasko says the Meridian's message was a warning and nobody should have come.",
      clues: [["A fuel line cut clean, not torn.", 1], ["The nav hack used bridge-officer credentials.", 3], ["Vasko asked Prance what they heard on the dead band.", 3]] },
    { id: "override", owner: "haas-bioroid", act: "ki", title: "The bioroid override", truth: "Every bioroid carries a dormant override and logs every conversation. MX-7 has gone independent and will ask the crew for help.",
      reveal: "", clues: [["MX-7 asked Fizz whether the gate counts as \"outside\".", 1], ["Every bioroid aboard froze for two seconds during the crossing.", 4], ["MX-7 didn't.", null]] },
    { id: "transmission", owner: "consortium", act: "ten", title: "The Meridian's real transmission", truth: "The consortium got the full transmission fifteen years ago. Every corp denies it exists.",
      reveal: "", clues: [["Prance caught four seconds of static and a voice on a dead band. Every corp says there was nothing.", 2]] },
    { id: "cryo", owner: "jinteki", act: "ki", title: "The edited cryo log", truth: "Ilse Marten woke early and recognised a Meridian crew name on the clone-bank manifest. The log was edited to hide it.",
      reveal: "", clues: [["Pod 14's log was edited after Ilse Marten died.", 2], ["Behnsun kept a copy of the original.", 2]] },
    { id: "clocks", owner: "gate", act: "ten", title: "The clocks disagree", truth: "Time runs differently through the gate. Only matters if the twist is option 2.",
      reveal: "", clues: [["The bridge clock says the crossing took six minutes. The cryo bay's says three days.", 4]] },
    { id: "gate", owner: "gate", act: "ketsu", title: "Who controls the gate", truth: "The ruins respond to whoever controls the gate, and each corp is privately racing to be that someone.",
      reveal: "", clues: [["Everyone aboard dreamed the same thing for eleven seconds during the crossing.", 4], ["The ruins' carvings match the dream.", null]] },
    { id: "weyland", owner: "weyland-consortium", act: "sho", title: "Contracts void on arrival", truth: "Weyland will nullify the colonists' contracts on arrival and claim the system as company property.",
      reveal: "", clues: [["Agent Sato's men are guarding the Meridian's black box at Dock 9.", 5], ["Captain Aldine's sealed orders.", null]] },
    { id: "clones", owner: "jinteki", act: "ten", title: "The clone stock", truth: "Jinteki is cloning the Meridian crew from stored samples, including someone the crew knows, to rewrite what \"survivor\" means.",
      reveal: "", clues: [["Jinteki has far more clone stock aboard than a colony ship needs.", null]] },
    { id: "edit", owner: "nbn", act: "ketsu", title: "The edit", truth: "NBN controls the only link home and is selling the story. One edit makes the outriders heroes or terrorists.",
      reveal: "NBN cut Pip out of the stowaway segment and made the outriders look like they caught a thief.", clues: [["The stowaway segment aired without Pip in it.", 1]] },
    { id: "handler", owner: "quill", act: "ten", title: "The handler", truth: "Dana Quill works for whichever corp benefits from the crew failing: NBN.", reveal: "", clues: [["Quill knew about Dock 9 before the crew did.", null]] },
    { id: "scientist", owner: "marr", act: "ten", title: "The rescued scientist", truth: "The first survivor the crew saves is the one who sabotaged the Meridian.", reveal: "", clues: [["The Meridian's logs stop mid-sentence.", 5]] },
    { id: "council", owner: "reyes", act: "ketsu", title: "The council deal", truth: "Councilor Reyes trades the survivors to Weyland for land.", reveal: "", clues: [["Reyes met Captain Aldine privately after the election.", null]] },
  ];
  const SAMPLE_STATE = { saboteur: "revealed", override: "hinted", transmission: "hinted", cryo: "hinted", clocks: "hinted", gate: "hinted", weyland: "hinted", edit: "revealed", scientist: "hidden" };
  const SAMPLE_REVEALED = { saboteur: 4, edit: 2 };

  const SESSIONS = [
    { n: 1, act: "ki", title: "Stowaway", recap: "First job: find whoever was eating from the cargo ring's ration stores. It was Pip Okonkwo, fourteen, hiding to watch the outriders' ship. NBN made it a feel-good segment.",
      prep: "Introduce Rook, Okafor, Quill and Pip. Cut fuel line on the way back.", choices: ["Covered for Pip with the deck officers"] },
    { n: 2, act: "ki", title: "A death in cryo", recap: "Ilse Marten died in sealed pod 14. Jinteki's Dr. Ilves called it a malfunction; Behnsun found the log had been edited. The Brandts got married the same night.",
      prep: "Plant the transmission on Prance's watch.", choices: ["Behnsun kept a copy of the original pod log", "Krum didn't report the dead-band signal"] },
    { n: 3, act: "ki", title: "The last port", recap: "Triton Free Dock. Smugglers asked the crew to carry a sealed case through the gate; a stranger offered 40,000 credits for a copy of the ship's manifest. Reyes won the council election.",
      prep: "The stranger is Calloway's contact from before the Meridian left.", choices: ["Took the smugglers' case, unopened", "Refused the stranger; Adarius kept the card"] },
    { n: 4, act: "ki", title: "The crossing", recap: "The crew caught Second Officer Vasko cutting the nav system before the gate. They listened, then let Vasko go and told no one. During the transit everyone dreamed the same thing for eleven seconds.",
      prep: "Set piece. The beacon is already pinging when the lights come back.", choices: ["Let Vasko go and kept it from the captain"] },
    { n: 5, act: "sho", title: "Dock 9", recap: "The crew boarded the empty Meridian and brought back its black box. Weyland security is waiting at Dock 9 to take it.",
      prep: "Rook is stalling the guards. If the crew spares the drone, Sato remembers it in Act III. Okafor is filming from the gantry.", choices: [] },
    { n: 6, act: "sho", title: "The survivors", recap: "", prep: "Planetside: the survivors' camp by the ruins. Introduce Marr and Calloway. Pip is in the cargo hold.", choices: [] },
  ];

  // Sample Session 0 answers. Personal: each PC drew two and answered one.
  const SAMPLE_ANSWERS = {
    p2: [{ pc: "prance", text: "A voice they knew, calling their name. Prance has told no one.", follow: "Krum asked whose voice. Prance passed.", act: "ten" }],
    p4: [{ pc: "krum", text: "Jinteki. Krum's neural lace is Jinteki IP, and so is the face on a clone-batch intake form Krum pulled off a node: Krum's own, dated next year.", follow: "", act: "sho" }],
    p12: [{ pc: "behnsun", text: "Ama, Behnsun's sibling in Jinteki's Quinde crèche. Behnsun promised to buy out Ama's contract before it renews.", follow: "Fizz asked when it renews. \"Next year.\"", act: "ketsu" }],
    p3: [{ pc: "fizz", text: "A foreman on the Quinde pit line. Fizz sealed the bulkhead because it was ordered to, and tells people the foreman made it out.", follow: "Behnsun asked the foreman's name. Fizz declined.", act: "ten" }],
    p8: [{ pc: "adarius", text: "Lien Tsang. Adarius faked a death on the Heinlein route to get off the Moon, and Lien still lights a candle every year.", follow: "", act: "ketsu" }],
    c2: [{ pc: "prance", about: "krum", text: "Krum wipes every terminal twice, even ones Krum never touched." }],
    c3: [{ pc: "krum", about: "behnsun", text: "Behnsun restarted Krum's heart in a Jinteki stairwell. It cost Behnsun a job and a reference. Krum hasn't paid it back." }],
    c6: [{ pc: "behnsun", about: "fizz", text: "Fizz hums Quinde work songs while making noodles nobody asked for." }],
    c4: [{ pc: "fizz", about: "adarius", text: "\"Lien.\" Every time." }],
    c5: [{ pc: "adarius", about: "prance", text: "Forty thousand. Adarius would hesitate at forty-one." }],
    w1: [{ pc: "adarius", text: "Triton Free Dock. Nobody asks your name, and nobody leaves owing the dock." }],
    w6: [{ pc: "behnsun", text: "The gate-builders aren't gone. They're asleep, and the gates are how they dream." }],
    h2: [{ pc: "krum", text: "A slow knock, three times, during every long burn." }],
    h3: [{ pc: "fizz", text: "Haas-Bioroid grey. Fizz knows the shade." }],
    h4: [{ pc: "prance", text: "The galley. Nobody sits in the corner seat." }],
  };
  const SAMPLE_DRAWS = { prance: ["p2", "p7"], krum: ["p4", "p9"], behnsun: ["p12", "p6"], fizz: ["p3", "p10"], adarius: ["p8", "p5"] };

  const clone = (x) => JSON.parse(JSON.stringify(x));
  function seedNpcs(blank) { return NPCS.map((n) => ({ ...clone(n), met: blank ? null : SAMPLE_MET[n.id] ?? null, notes: "" })); }
  function seedLedger(blank) {
    return LEDGER.map((l) => ({ ...clone(l), state: blank ? "hidden" : SAMPLE_STATE[l.id] || "hidden", revealedIn: blank ? null : SAMPLE_REVEALED[l.id] ?? null,
      reveal: blank ? "" : l.reveal, clues: l.clues.map(([t, s]) => ({ t, s: blank ? null : s })) }));
  }
  function seedS0(blank) {
    return { done: !blank, answers: blank ? {} : clone(SAMPLE_ANSWERS), draws: blank ? {} : clone(SAMPLE_DRAWS), alt: {},
      truth: blank ? {} : { w6: "The dreaming part is true. It ties to the shared dream at the crossing.", h2: "" },
      dials: Object.fromEntries(DIALS.map(([id, , , , def]) => [id, def])),
      lv: blank ? [] : [{ topic: "Harm to children shown on screen", kind: "line" }, { topic: "Alien parasitism", kind: "veil" }, { topic: "Torture", kind: "veil" }, { topic: "Addiction", kind: "fine" }],
      ship: blank ? { painted: "", registry: "", hull: "", quirks: "" } : { painted: "Lucky Break", registry: "WC-SV 0417 (salvage tender)", hull: "Salvage tug", quirks: "Helpful: the grapple never slips.\nTroublesome: the nav computer sometimes plots courses to coordinates nobody entered." },
      wrap: blank ? {} : { 0: true, 1: true, 2: true, 5: true, 6: true } };
  }
  function seed(kind) {
    const blank = kind === "blank";
    return { v: 1, session: blank ? 0 : 5, act: blank ? "ki" : "sho",
      sessions: blank ? [] : SESSIONS.map((s) => ({ ...clone(s), status: s.n < 5 ? "played" : s.n === 5 ? "now" : "plan" })),
      threads: blank ? {} : { "ki-cryo": "done", "ki-sab": "done", "ki-bio": "live", "ki-nbn": "live", "ki-town": "live", "ki-port": "done", "sho-empty": "done", "sho-leak": "live", "sho-offers": "live" },
      twist: blank ? null : 1, npcs: seedNpcs(blank), ledger: seedLedger(blank), s0: seedS0(blank) };
  }

  // ---------- State and persistence ----------
  const storeKey = (kind) => `ttg-campaign-${kind}-v1`;
  function load(kind) {
    try { const raw = localStorage.getItem(storeKey(kind)); if (raw) { const d = JSON.parse(raw); if (d && d.v === 1) return d; } } catch (e) {}
    return seed(kind);
  }
  const S = { preview: false, tab: "arc", kind: "sample", data: null, actView: null, s0tab: "questions", group: "all", q: "", d: { open: false }, resetArmed: false };
  try { const k = localStorage.getItem("ttg-campaign-kind"); if (k === "blank") S.kind = k; } catch (e) {}
  S.data = load(S.kind);
  const save = () => { try { localStorage.setItem(storeKey(S.kind), JSON.stringify(S.data)); } catch (e) {} };
  const D = () => S.data;
  const $ = (id) => document.getElementById(id);
  const gm = () => !S.preview;
  let toastTimer = 0;
  const toast = (text) => { const t = $("toast"); t.textContent = text; t.hidden = false; clearTimeout(toastTimer); toastTimer = setTimeout(() => (t.hidden = true), 2800); $("live").textContent = text; };
  const gmTag = (extra = "") => `<span class="tag gm">GM only</span>${extra}`;
  const gmBlock = (inner, head = "") => (gm() ? `<div class="gmonly"><div class="gmhd">${gmTag(head)}</div>${inner}</div>` : "");
  const pcChip = (id, extra = "") => { const p = pcById(id); return p ? `<span class="who" style="--pc:${p.color}"><span class="pcdot"></span>${esc(first(p))}${extra}</span>` : ""; };
  const stTag = (st) => `<span class="st ${st}">${st}</span>`;
  const sessionLabel = (n) => (n === 0 ? "Session 0" : n == null ? "" : `S${n}`);
  const curSession = () => D().sessions.find((s) => s.status === "now");
  // Every answer, flattened: [{q, a, i}]
  const allAnswers = () => Q.flatMap((q) => (D().s0.answers[q.id] || []).map((a, i) => ({ q, a, i })));
  const ledgerById = (id) => D().ledger.find((l) => l.id === id);
  const setPath = (obj, path, val) => { const ks = path.split("."); const last = ks.pop(); const o = ks.reduce((x, k) => x[k], obj); o[last] = val; };

  // ---------- Top bar and tabs ----------
  function renderTop() {
    const d = D();
    $("sess").textContent = d.session ? `Session ${d.session} · ${actLabel(d.act)}` : d.s0.done ? `Session 1 next · ${actLabel("ki")}` : "Session 0 next";
    $("dataset").value = S.kind;
    document.querySelectorAll("[data-view]").forEach((b) => b.setAttribute("aria-pressed", String((b.dataset.view === "player") === S.preview)));
    $("app").classList.toggle("preview", S.preview);
    $("banner").hidden = !S.preview;
    $("banner").innerHTML = `<b>Player view.</b> What a player's browser receives: people they've met, secrets they've uncovered, sessions they've played. GM-only fields are stripped by the server, not just hidden. <button type="button" class="linkbtn" data-view="gm">Back to GM view</button>`;
    $("reset").textContent = S.resetArmed ? "Tap again to reset" : "Reset this data set";
  }
  function renderTabs() {
    const d = D(), known = d.ledger.filter((l) => l.state === "revealed").length;
    const met = d.npcs.filter((n) => n.met != null).length;
    const tabs = [["arc", "Arc", ""], ["s0", "Session 0", d.s0.done ? "" : "next"], ["cast", "Cast", gm() ? d.npcs.length : met],
      ["factions", "Factions", ""], ["ledger", gm() ? "Ledger" : "What we know", gm() ? `${known}/${d.ledger.length}` : known]];
    $("tabs").innerHTML = tabs.map(([id, label, b]) => `<button type="button" role="tab" data-tab="${id}" aria-selected="${S.tab === id}">${label}${b !== "" ? `<span class="badge">${b}</span>` : ""}</button>`).join("");
  }
  function renderMain() {
    $("main").innerHTML = { arc: arcHTML, s0: s0HTML, cast: castHTML, factions: factionsHTML, ledger: ledgerHTML }[S.tab]();
  }
  const intro = (title, text, right = "") => `<div class="intro"><div><h1>${title}</h1>${text ? `<p>${text}</p>` : ""}</div>${right}</div>`;

  // ---------- Arc ----------
  function arcHTML() {
    const d = D(), cur = actIdx(d.act), view = S.actView ?? d.act;
    const payoffs = allAnswers().filter(({ q, a }) => q.set === "personal" && a.act);
    const untied = PARTY.filter((p) => !payoffs.some(({ a }) => a.pc === p.id));
    const warn = gm() && d.s0.done && untied.length ? `<p class="warnline">No act pays off ${untied.map(first).join(", ")}'s Session 0 answer yet.</p>` : "";
    const pick = `<div class="chips actpick" role="group" aria-label="Act">${ACTS.map((a, i) => (gm() || i <= cur) ?
      `<button type="button" class="chip" data-actview="${a.id}" aria-pressed="${view === a.id}">${a.roman} · ${a.name}${i === cur ? " ●" : ""}</button>` : "").join("")}</div>`;
    return intro("The arc", gm() ? "Four acts in Kishōtenketsu. Sessions sit under their act; Session 0 answers show where they pay off." : "The story so far.", "")
      + warn + pick + `<div class="acts">${ACTS.map((a, i) => actHTML(a, i, cur, view, payoffs)).join("")}</div>`;
  }
  function actHTML(a, i, cur, view, payoffs) {
    const d = D(), shown = a.id === view ? " shown" : "";
    if (!gm() && i > cur) return `<section class="act locked${shown}"><header><span class="kanji">Act ${a.roman}</span><h2>Not yet</h2></header></section>`;
    const status = i < cur ? `<span class="tag">done</span>` : i === cur ? `<span class="tag now">now</span>` : "";
    const sess = d.sessions.filter((s) => s.act === a.id && (gm() || s.status !== "plan"));
    const sessHTML = sess.length ? sess.map(sessionRow).join("") : `<p class="note">${gm() ? "No sessions planned." : "No sessions yet."}</p>`;
    const mine = payoffs.filter(({ a: ans }) => ans.act === a.id);
    const pay = `<div><span class="label">Pays off here</span><div class="payoff" style="margin-top:4px">${mine.length ? mine.map(({ q, a: ans, i: k }) =>
      `<button type="button" class="chip" data-qopen="${q.id}" title="${esc(q.text)}" style="--pc:${pcById(ans.pc).color}"><span class="pcdot"></span>${esc(first(pcById(ans.pc)))} · ${q.id.toUpperCase()}</button>`).join("")
      : `<span class="note">No one's Session 0 answer yet.</span>`}</div></div>`;
    const threads = a.threads.length ? `<div><span class="label">Threads · tap to cycle</span><div>${a.threads.map(([id, t]) => {
      const st = d.threads[id] || "plan"; return `<button type="button" class="thread ${st}" data-thread="${id}"><i>${{ plan: "planned", live: "in play", done: "done" }[st]}</i><span>${esc(t)}</span></button>`; }).join("")}</div></div>` : "";
    const extra = a.id === "ten" ? twistHTML() : a.end ? `<p><b>Ends with the crossing.</b> ${esc(a.end.replace(/^The crossing: /, ""))}</p>` : "";
    return `<section class="act${i === cur ? " cur" : ""}${shown}"><header><span class="kanji">Act ${a.roman} · ${a.kanji}${a.length ? ` · ${a.length}` : ""}</span>
      <h2>${a.name} <small>${a.title}</small>${status}</h2></header>
      ${a.pub ? `<p>${esc(a.pub)}</p>` : ""}
      <div class="sessions">${sessHTML}</div>
      ${gm() ? `<button type="button" class="linkbtn" data-plan="${a.id}">+ Plan a session</button>` : ""}
      ${gmBlock(`<p>${esc(a.gm)}</p>${pay}${threads}${extra}`)}</section>`;
  }
  function sessionRow(s) {
    const cls = s.status === "now" ? " now" : s.status === "plan" ? " plan" : "";
    const choices = s.choices.length ? `<div class="choices">${s.choices.map((c) => `<span class="choice">${esc(c)}</span>`).join("")}</div>` : "";
    return `<button type="button" class="item" data-sopen="${s.n}"><div class="sesh${cls}"><span class="n">${s.n}</span><div><b>${esc(s.title || "Untitled")}${s.status === "now" ? ` <span class="tag now">tonight</span>` : s.status === "plan" ? ` <span class="tag gm">planned</span>` : ""}</b>
      ${s.recap ? `<span class="recap">${esc(s.recap)}</span>` : ""}${choices}</div></div></button>`;
  }
  function twistHTML() {
    const d = D();
    const opts = TWISTS.map((t) => `<label class="twist"><input type="radio" name="twist" value="${t.n}" ${d.twist === t.n ? "checked" : ""}><span><b>${t.n}. ${esc(t.title)}</b><br><span class="sub">${esc(t.text)}</span></span></label>`).join("");
    const seeds = SEEDS.filter((s) => !s.for || !d.twist || s.for === d.twist).map((s) => {
      const l = ledgerById(s.ledger), planted = l.clues.filter((c) => c.s != null).length;
      return `<div class="seed">${stTag(l.state)}<button type="button" class="linkbtn" data-lopen="${l.id}">${esc(s.text)}</button><span class="note">${planted}/${l.clues.length} clues planted</span></div>`;
    }).join("");
    return `<div><span class="label">Pick the twist</span>${opts}</div><div style="display:grid;gap:4px"><span class="label">Seeds from the voyage</span>${seeds}</div>`;
  }

  // ---------- Session 0 ----------
  function s0HTML() {
    const d = D(), sub = gm() ? S.s0tab : S.s0tab === "wrap" ? "questions" : S.s0tab;
    const tabs = [["questions", "Questions"], ["table", "Tone and safety"], ["ship", "The ship"], ...(gm() ? [["wrap", "Wrap-up"]] : [])];
    const right = gm() ? `<button type="button" class="btn${d.s0.done ? "" : " primary"}" data-act="s0-done">${d.s0.done ? "Session 0 played ✓" : "Mark Session 0 played"}</button>` : "";
    return intro("Session 0", "A crew, a ship, a debt that binds them, and a frontier they helped invent. Answers become canon; the GM ties each personal answer to the act where it pays off.", right)
      + `<div class="chips">${tabs.map(([id, l]) => `<button type="button" class="chip" data-s0tab="${id}" aria-pressed="${sub === id}">${l}</button>`).join("")}</div>`
      + { questions: questionsHTML, table: tableHTML, ship: shipHTML, wrap: wrapHTML }[sub]();
  }
  function questionsHTML() {
    const d = D();
    return (gm() ? drawHTML() : "") + QSETS.filter((s) => s.id !== "ship").map((set) => {
      const qs = Q.filter((q) => q.set === set.id), n = qs.filter((q) => (d.s0.answers[q.id] || []).length).length;
      return `<section class="card panel"><header><h2>${set.name} questions<small>${n} answered</small></h2></header><div class="pbody"><p class="note">${set.how}</p>
        <div class="qset">${qs.map(qHTML).join("")}</div></div></section>`;
    }).join("");
  }
  function qHTML(q) {
    const d = D(), answers = d.s0.answers[q.id] || [], alt = d.s0.alt[q.id];
    const ans = answers.filter((a) => a.text).map((a) => `<div class="ans">${pcChip(a.pc, a.about ? ` → ${esc(first(pcById(a.about)))}` : "")}<p>${esc(a.text)}</p>
      ${a.follow ? `<span class="fu">${esc(a.follow)}</span>` : ""}${gm() && a.act ? `<span></span><span><span class="tag gm">Pays off in ${actLabel(a.act)}</span></span>` : ""}</div>`).join("");
    const fit = gm() && q.fit && !alt ? `<p class="warnline">${esc(q.fit)}</p>` : "";
    const truth = gm() && TRUTH_Q[q.id] && d.s0.truth[q.id] ? `<p class="note"><span class="tag gm">${TRUTH_Q[q.id]}</span> ${esc(d.s0.truth[q.id])}</p>` : "";
    return `<div class="q${answers.some((a) => a.text) ? " answered" : ""}"><div class="row1" style="display:flex;gap:8px;align-items:baseline;justify-content:space-between">
      <span class="qn">${q.id.toUpperCase()}</span>${gm() ? `<button type="button" class="linkbtn" data-qopen="${q.id}">${answers.length ? "Edit" : "Answer"}</button>` : ""}</div>
      <span class="qt">${esc(alt || q.text)}</span>${alt && gm() ? `<span class="alt">Book wording: ${esc(q.text)}</span>` : ""}${fit}${ans}${truth}</div>`;
  }
  // The Wildsea-style draw: two personal cards per player, keep one.
  function drawHTML() {
    const d = D(), taken = new Set([...Object.values(d.s0.draws).flat(), ...Object.keys(d.s0.answers).filter((k) => k.startsWith("p"))]);
    const answeredBy = (id) => allAnswers().find(({ q, a }) => q.set === "personal" && a.pc === id && a.text);
    const who = S.drawFor;
    const cards = who && d.s0.draws[who] ? `<div class="cards">${d.s0.draws[who].map((qid) => {
      const q = qById(qid); return `<div class="qcard"><p>${esc(d.s0.alt[qid] || q.text)}</p><div class="chips"><button type="button" class="btn primary" data-keep="${who}|${qid}">${esc(first(pcById(who)))} answers this</button></div></div>`; }).join("")}</div>
      <div class="chips"><button type="button" class="linkbtn" data-redraw="${who}" ${Q.filter((q) => q.set === "personal" && !taken.has(q.id)).length < 2 ? "disabled" : ""}>Put them back and draw again</button></div>` : "";
    return `<section class="draw"><div class="gmhd" style="display:flex;gap:8px;flex-wrap:wrap;align-items:center"><span class="tag gm">GM only</span><b>Run the draw</b><span class="note">Go round the table. Each player draws two personal questions and answers one; any one player may ask a single follow-up.</span></div>
      <div class="chips">${PARTY.map((p) => { const done = answeredBy(p.id);
        return `<button type="button" class="chip" data-drawfor="${p.id}" aria-pressed="${who === p.id}" style="--pc:${p.color}"><span class="pcdot"></span>${esc(first(p))}${done ? " ✓" : d.s0.draws[p.id] ? " · drawn" : ""}</button>`; }).join("")}</div>
      ${who && !d.s0.draws[who] ? `<p class="note">Drawing for ${esc(first(pcById(who)))}…</p>` : cards}
      ${who && answeredBy(who) ? `<p class="note">${esc(first(pcById(who)))} answered ${answeredBy(who).q.id.toUpperCase()}. Drawing again adds a second answer.</p>` : ""}</section>`;
  }
  function tableHTML() {
    const d = D();
    const dials = DIALS.map(([id, name, lo, hi]) => `<div class="dial"><b>${name}</b><div class="pips" role="group" aria-label="${name}">${[1, 2, 3, 4, 5].map((v) =>
      `<button type="button" data-dial="${id}|${v}" aria-pressed="${d.s0.dials[id] === v}" ${gm() ? "" : "disabled"}>${v}</button>`).join("")}</div><div class="ends"><span>${lo}</span><span>${hi}</span></div></div>`).join("");
    const kinds = { line: "Line: never in play", veil: "Veil: fade to black", fine: "Fine" };
    const lv = d.s0.lv.map((r, i) => `<div class="lv"><span>${esc(r.topic)}</span><span class="tag${r.kind === "line" ? " bad" : r.kind === "veil" ? " warn" : ""}">${kinds[r.kind]}</span>
      <button type="button" class="linkbtn" data-dellv="${i}" aria-label="Remove ${esc(r.topic)}">Remove</button></div>`).join("");
    const unsorted = THEMES.filter((t) => !d.s0.lv.some((r) => r.topic === t));
    return `<div class="grid" style="grid-template-columns:repeat(auto-fit,minmax(min(100%,420px),1fr))">
      <section class="card panel"><header><h2>Tone dials<small>the table's number</small></h2></header><div class="pbody">${dials}
        <p class="note">Alien weirdness starts at 2 and rises. What a session looks like: a job, a complication, a choice about who gets hurt, and a payday that is never quite enough.</p></div></section>
      <section class="card panel"><header><h2>Lines and veils<small>X-card plus open door</small></h2></header><div class="pbody">
        ${lv || `<div class="empty">Nothing sorted yet. Fill this in before anyone answers an unsettling question.</div>`}
        <div class="addrow"><input id="lv-topic" placeholder="Add a topic" aria-label="Topic"><button type="button" class="btn" data-addlv="line">Line</button><button type="button" class="btn" data-addlv="veil">Veil</button><button type="button" class="btn" data-addlv="fine">Fine</button></div>
        ${unsorted.length ? `<div><span class="label">Likely themes, not sorted yet</span><div class="chips" style="margin-top:6px">${unsorted.map((t) => `<button type="button" class="chip" data-theme="${esc(t)}">${esc(t)}</button>`).join("")}</div><p class="note" style="margin-top:6px">Tap one to put it in the box, then pick line, veil or fine.</p></div>` : ""}
      </div></section></div>`;
  }
  function shipHTML() {
    const d = D(), sh = d.s0.ship, f = (k, label, ph) => gm()
      ? `<label class="field"><span>${label}</span><input data-bind="s0.ship.${k}" value="${esc(sh[k])}" placeholder="${ph}"></label>`
      : `<div class="field"><span>${label}</span><b>${esc(sh[k]) || "—"}</b></div>`;
    return `<section class="card panel"><header><h2>${esc(sh.painted) || "The ship"}<small>${esc(sh.registry)}</small></h2></header><div class="pbody">
      <div class="ship">${f("painted", "Name on the hull", "Lucky Break")}${f("registry", "Name on the registry", "Should differ from the hull")}${f("hull", "Hull", "Light freighter or salvage tug")}</div>
      ${gm() ? `<label class="field"><span>Quirks: one helpful, one troublesome</span><textarea data-bind="s0.ship.quirks">${esc(sh.quirks)}</textarea></label>` : sh.quirks ? `<p style="white-space:pre-line;margin:0">${esc(sh.quirks)}</p>` : ""}
      <p class="note">Hull from Shattered Empire, attachments from the Expanded Player's Guide. The ship sheet comes with the vehicle rules.</p></div></section>
      <section class="card panel"><header><h2>The ship's unsettling questions</h2></header><div class="pbody"><div class="qset">${Q.filter((q) => q.set === "ship").map(qHTML).join("")}</div></div></section>`;
  }
  function wrapHTML() {
    const d = D();
    return `<section class="card panel"><header><h2>After Session 0</h2>${gmTag()}</header><div class="pbody">${WRAPUP.map((t, i) =>
      `<label class="check${d.s0.wrap[i] ? " done" : ""}"><input type="checkbox" data-wrap="${i}" ${d.s0.wrap[i] ? "checked" : ""}><span>${esc(t)}</span></label>`).join("")}</div></section>`;
  }

  // ---------- Cast ----------
  function castHTML() {
    const d = D(), q = S.q.toLowerCase();
    const list = d.npcs.filter((n) => (gm() || n.met != null) && (S.group === "all" || n.group === S.group) && (!q || `${n.name} ${n.role}`.toLowerCase().includes(q)));
    const town = d.npcs.filter((n) => n.group === "town").length;
    const warn = gm() && town < 5 ? `<p class="warnline">Name at least five townsfolk with small arcs in Ki: they're the stakes in Ketsu.</p>` : "";
    const filters = `<div class="chips">${[["all", "All"], ...Object.entries(GROUPS)].map(([id, l]) => `<button type="button" class="chip" data-group="${id}" aria-pressed="${S.group === id}">${l}</button>`).join("")}</div>`;
    return intro("Cast", gm() ? "Everyone aboard who matters. Players only see the people they've met." : "People you've met.", `<input class="search" id="castq" placeholder="Search the cast" value="${esc(S.q)}" aria-label="Search the cast">`)
      + warn + filters + (list.length ? `<div class="grid">${list.map(npcCard).join("")}</div>` : `<div class="empty">${gm() ? "No one matches." : "You haven't met anyone yet."}</div>`);
  }
  const factionName = (id) => (id ? ownerName(id) : "Consortium");
  function npcCard(n) {
    const met = n.met != null ? `<span class="tag">met ${sessionLabel(n.met)}</span>` : gm() ? `<span class="tag gm">not met</span>` : "";
    const secrets = D().ledger.filter((l) => l.owner === n.id);
    return `<div class="item npc"><div class="row1"><h3><button type="button" data-nopen="${n.id}">${esc(n.name)}</button></h3>${n.dead ? `<span class="tag bad">dead</span>` : ""}${met}</div>
      <span class="sub">${esc(n.role)} · ${esc(factionName(n.faction))}</span>${n.pub ? `<p>${esc(n.pub)}</p>` : ""}
      ${gmBlock(`<p>${esc(n.gm)}</p>${secrets.map((l) => `<div class="seed">${stTag(l.state)}<button type="button" class="linkbtn" data-lopen="${l.id}">${esc(l.title)}</button></div>`).join("")}`)}</div>`;
  }

  // ---------- Factions ----------
  function factionsHTML() {
    const d = D();
    const noCorp = PARTY.filter((p) => !p.creditor), idle = CORPS.filter((c) => !PARTY.some((p) => p.creditor === c.id));
    const warn = gm() ? [noCorp.length ? `${noCorp.map((p) => `${first(p)} owes ${p.owes}`).join("; ")}, not a corp.` : "",
      idle.length ? `${idle.map((c) => c.short).join(" and ")} hold${idle.length === 1 ? "s" : ""} no one's Debt.` : ""].filter(Boolean) : [];
    return intro("Factions", gm() ? "Every corp has a public role and a secret agenda, and each breaks at a different point so the betrayals land across the whole campaign." : "Who owns which part of the Concord.")
      + (warn.length ? `<p class="warnline">Tie each Debt to a corp so a triggered Debt pulls its agenda into the session: ${warn.join(" ")}</p>` : "")
      + `<div class="grid" style="grid-template-columns:repeat(auto-fill,minmax(min(100%,420px),1fr))">${CORPS.map(corpCard).join("")}</div>`
      + `<section class="card panel"><header><h2>Other powers</h2></header><div class="pbody"><div class="grid">${POWERS.map(powerCard).join("")}</div></div></section>`;
  }
  function corpCard(c) {
    const d = D(), l = ledgerById(c.ledger), debtors = PARTY.filter((p) => p.creditor === c.id);
    const reps = d.npcs.filter((n) => n.faction === c.id && (gm() || n.met != null));
    const exposed = l.state === "revealed" ? `<div class="item known"><span class="label">Exposed ${sessionLabel(l.revealedIn)}</span><p>${esc(l.reveal || l.truth)}</p></div>` : "";
    return `<section class="card corp" style="padding:14px"><h3>${esc(c.name)} ${cite(c.source)}</h3>
      <dl class="kv"><dt>Role aboard</dt><dd>${esc(c.pub)}</dd><dt>Share</dt><dd>${esc(c.share)}</dd>
      ${reps.length ? `<dt>People</dt><dd>${reps.map((n) => `<button type="button" class="linkbtn" data-nopen="${n.id}">${esc(n.name)}</button>`).join(", ")}</dd>` : ""}
      <dt>Holds Debt</dt><dd>${debtors.length ? debtors.map((p) => `<span class="debtor" style="--pc:${p.color}"><span class="pcdot"></span>${esc(first(p))} <b class="mono">${p.debt}</b></span>`).join(" ") : `<span class="sub">No one's</span>`}</dd></dl>
      ${exposed}${gmBlock(`<p><b>Secret agenda.</b> ${esc(l.truth)}</p><div class="lrow"><span class="note">Breaks ${esc(c.breaks)}</span>${segHTML(l)}</div>
        <button type="button" class="linkbtn" data-lopen="${l.id}">Clues: ${l.clues.filter((x) => x.s != null).length} of ${l.clues.length} planted</button>`)}</section>`;
  }
  function powerCard(p) {
    const l = p.ledger && ledgerById(p.ledger), src = p.src && find("factions", p.src);
    return `<div class="item"><div class="row1"><h3>${esc(p.name)}</h3>${src ? cite(src.source) : ""}</div><p>${esc(p.pub)}</p>
      ${l && l.state === "revealed" ? `<p><b>Exposed:</b> ${esc(l.reveal || l.truth)}</p>` : ""}
      ${gmBlock(l ? `<div class="seed">${stTag(l.state)}<button type="button" class="linkbtn" data-lopen="${l.id}">${esc(l.title)}</button></div>` : `<p>${esc(p.gm)}</p>`)}</div>`;
  }

  // ---------- Betrayal ledger ----------
  const STATES = ["hidden", "hinted", "revealed"];
  const segHTML = (l) => `<div class="seg" role="group" aria-label="Reveal state of ${esc(l.title)}">${STATES.map((st) =>
    `<button type="button" class="${st}" data-state="${l.id}|${st}" aria-pressed="${l.state === st}">${st[0].toUpperCase() + st.slice(1)}</button>`).join("")}</div>`;
  function ledgerHTML() {
    if (!gm()) return knownHTML();
    const d = D(), count = (st) => d.ledger.filter((l) => l.state === st).length;
    const tally = `<div class="tally">${STATES.map((st) => `<div class="t"><b>${count(st)}</b><span>${st}</span></div>`).join("")}<div class="t"><b>${d.ledger.reduce((n, l) => n + l.clues.filter((c) => c.s != null).length, 0)}</b><span>clues planted</span></div></div>`;
    const byAct = ACTS.map((a) => {
      const rows = d.ledger.filter((l) => l.act === a.id);
      return rows.length ? `<section class="card panel"><header><h2>Breaks in ${actLabel(a.id)}</h2></header><div class="pbody">${rows.map((l) => `<div class="item"><div class="lrow">
        <div><div class="row1"><h3><button type="button" class="linkbtn" style="font-size:15px;font-weight:700;color:inherit;text-decoration:none" data-lopen="${l.id}">${esc(l.title)}</button></h3></div>
          <div class="meta"><span>${esc(ownerName(l.owner))}</span><span>${l.clues.filter((c) => c.s != null).length}/${l.clues.length} clues</span>${l.revealedIn != null && l.state === "revealed" ? `<span>revealed ${sessionLabel(l.revealedIn)}</span>` : ""}</div></div>
        ${segHTML(l)}</div><p class="sub">${esc(l.truth)}</p></div>`).join("")}</div></section>` : "";
    }).join("");
    return intro("Betrayal ledger", "Which secrets the players have uncovered, so the reveals stay consistent. Players see revealed secrets and the clues they've found, never the truth behind a hint.", gmTag())
      + tally + byAct;
  }
  function knownHTML() {
    const d = D(), known = d.ledger.filter((l) => l.state === "revealed");
    const clues = d.ledger.filter((l) => l.state !== "revealed").flatMap((l) => l.clues.filter((c) => c.s != null)).sort((a, b) => a.s - b.s);
    return intro("What we know", "Secrets the crew has uncovered, and the loose ends they've noticed.")
      + `<section class="card panel"><header><h2>Uncovered<small>${known.length}</small></h2></header><div class="pbody">${known.length ? known.map((l) =>
        `<div class="item known"><div class="row1"><h3>${esc(l.title)}</h3><span class="tag">${sessionLabel(l.revealedIn)}</span></div><p>${esc(l.reveal || "The crew knows.")}</p></div>`).join("") : `<div class="empty">Nothing yet.</div>`}</div></section>`
      + `<section class="card panel"><header><h2>Loose ends<small>${clues.length}</small></h2></header><div class="pbody">${clues.length ? clues.map((c) =>
        `<div class="item"><div class="row1"><span class="tag">${sessionLabel(c.s)}</span></div><p>${esc(c.t)}</p></div>`).join("") : `<div class="empty">Nothing odd. Yet.</div>`}</div></section>`;
  }

  // ---------- Drawer ----------
  function openDrawer(kind, data) {
    S.d = { open: true, kind, ...data };
    renderDrawer();
    $("scrim").hidden = false; $("drawer").hidden = false;
    const f = $("drawer").querySelector(S.d.focus || ".x"); if (f) f.focus();
  }
  function closeDrawer() { S.d = { open: false }; $("scrim").hidden = true; $("drawer").hidden = true; $("drawer").innerHTML = ""; renderAll(); }
  function renderDrawer() {
    if (!S.d.open) return;
    const [title, meta, body] = { session: drawerSession, npc: drawerNpc, secret: drawerSecret, question: drawerQuestion }[S.d.kind]();
    $("drawer").innerHTML = `<div class="grab" aria-hidden="true"></div><header><div><h2 id="d-title">${title}</h2><div class="meta">${meta}</div></div>
      <button type="button" class="x" data-act="close" aria-label="Close">×</button></header><div class="body">${body}</div>`;
  }
  const area = (path, val, label, ph = "") => `<label class="field"><span>${label}</span><textarea data-bind="${path}" placeholder="${esc(ph)}">${esc(val)}</textarea></label>`;
  const actSelect = (path, val, label, none = "Not tied to an act") => `<label class="field"><span>${label}</span><select data-bind="${path}"><option value="">${none}</option>${ACTS.map((a) =>
    `<option value="${a.id}" ${val === a.id ? "selected" : ""}>${actLabel(a.id)}</option>`).join("")}</select></label>`;
  const sessionOptions = (val, none) => `<option value="">${none}</option>${D().s0.done || val === 0 ? `<option value="0" ${val === 0 ? "selected" : ""}>Session 0</option>` : ""}${D().sessions.filter((s) => s.status !== "plan").map((s) =>
    `<option value="${s.n}" ${val === s.n ? "selected" : ""}>Session ${s.n}: ${esc(s.title)}</option>`).join("")}`;

  function drawerSession() {
    const d = D(), i = d.sessions.findIndex((s) => s.n === S.d.n), s = d.sessions[i], p = `sessions.${i}`;
    const metHere = d.npcs.filter((n) => n.met === s.n), clues = d.ledger.flatMap((l) => l.clues.filter((c) => c.s === s.n).map((c) => ({ l, c })));
    const status = { played: "Played", now: "Tonight", plan: "Planned" }[s.status];
    const body = gm() ? `<label class="field"><span>Title</span><input data-bind="${p}.title" value="${esc(s.title)}"></label>
      ${area(`${p}.recap`, s.recap, "Recap · players see this", "What happened, in the crew's words")}
      <div><h4>Choices that carry forward</h4><div class="choices">${s.choices.map((c, k) => `<span class="choice">${esc(c)} <button type="button" class="linkbtn" data-delchoice="${k}" aria-label="Remove">×</button></span>`).join("") || `<span class="note">None logged.</span>`}</div>
        <div class="addrow" style="margin-top:6px"><input id="choice-in" placeholder="The crew let Vasko go…" aria-label="New choice"><button type="button" class="btn" data-act="add-choice">Add</button></div></div>
      <div class="gmonly"><div class="gmhd">${gmTag()}</div>${area(`${p}.prep`, s.prep, "Prep notes")}
        ${actSelect(`${p}.act`, s.act, "Act", "No act")}
        ${clues.length ? `<div><span class="label">Clues planted</span>${clues.map(({ l, c }) => `<p class="sub">${esc(c.t)} <button type="button" class="linkbtn" data-lopen="${l.id}">${esc(l.title)}</button></p>`).join("")}</div>` : ""}</div>
      ${metHere.length ? `<div><h4>First met</h4><div class="chips">${metHere.map((n) => `<button type="button" class="chip" data-nopen="${n.id}">${esc(n.name)}</button>`).join("")}</div></div>` : ""}
      <div class="chips">${s.status === "plan" ? `<button type="button" class="btn primary" data-act="start-session">Start this session</button><button type="button" class="btn danger" data-act="del-session">Delete</button>` : ""}
        ${s.status === "now" ? `<button type="button" class="btn" data-act="end-session">Mark played</button>` : ""}</div>`
      : `<p>${esc(s.recap) || "No recap yet."}</p>${s.choices.length ? `<div><h4>What the crew chose</h4><div class="choices">${s.choices.map((c) => `<span class="choice">${esc(c)}</span>`).join("")}</div></div>` : ""}
      ${metHere.length ? `<div><h4>First met</h4><p>${metHere.map((n) => esc(n.name)).join(", ")}</p></div>` : ""}`;
    return [`Session ${s.n}${s.title ? `: ${esc(s.title)}` : ""}`, `${actLabel(s.act)} · ${status}`, body];
  }
  function drawerNpc() {
    const d = D(), i = d.npcs.findIndex((n) => n.id === S.d.id), n = d.npcs[i], p = `npcs.${i}`;
    const secrets = d.ledger.filter((l) => l.owner === n.id);
    const arc = ACTS.filter((a) => n.arc[a.id]).map((a) => `<dt>${a.name}</dt><dd>${esc(n.arc[a.id])}</dd>`).join("");
    const body = gm() ? `<label class="field"><span>Players have met them</span><select data-bind="${p}.met" data-num>${sessionOptions(n.met, "Not yet: hidden from players")}</select></label>
      ${area(`${p}.pub`, n.pub, "What players know")}
      <div class="gmonly"><div class="gmhd">${gmTag()}</div>${area(`${p}.gm`, n.gm, "Secret")}
        ${arc ? `<div><span class="label">Arc</span><dl class="kv" style="margin-top:4px">${arc}</dl></div>` : ""}
        ${secrets.map((l) => `<div class="seed">${stTag(l.state)}<button type="button" class="linkbtn" data-lopen="${l.id}">${esc(l.title)}</button></div>`).join("")}
        ${area(`${p}.notes`, n.notes, "Notes", "Voice, mannerisms, what they want right now")}</div>`
      : `<p>${esc(n.pub)}</p>${secrets.filter((l) => l.state === "revealed").map((l) => `<div class="item known"><b>${esc(l.title)}</b><p>${esc(l.reveal)}</p></div>`).join("")}`;
    return [esc(n.name), `${esc(n.role)} · ${esc(factionName(n.faction))}${n.met != null ? ` · met ${sessionLabel(n.met)}` : ""}${n.dead ? ` <span class="tag bad">dead</span>` : ""}`, body];
  }
  function drawerSecret() {
    const d = D(), i = d.ledger.findIndex((l) => l.id === S.d.id), l = d.ledger[i], p = `ledger.${i}`;
    const clues = l.clues.map((c, k) => `<div class="clue${c.s == null ? " unplanted" : ""}"><p>${esc(c.t)}</p>${c.s != null
      ? `<button type="button" class="chip" data-plant="${k}" title="Unplant">${sessionLabel(c.s)} ✓</button>` : `<button type="button" class="chip" data-plant="${k}">Plant ${d.session ? `in S${d.session}` : "now"}</button>`}</div>`).join("");
    const body = `<div class="lrow"><span class="note">Breaks in ${actLabel(l.act)}</span>${segHTML(l)}</div>
      ${area(`${p}.truth`, l.truth, "The truth")}
      <div><h4>Clues<span class="note">players see planted clues as loose ends</span></h4>${clues || `<p class="note">No clues yet.</p>`}
        <div class="addrow" style="margin-top:8px"><input id="clue-in" placeholder="Something the crew could notice" aria-label="New clue"><button type="button" class="btn" data-act="add-clue">Add clue</button></div></div>
      ${area(`${p}.reveal`, l.reveal, "When it's revealed, players read", "In the crew's words, not the GM's")}
      <label class="field"><span>Revealed in</span><select data-bind="${p}.revealedIn" data-num>${sessionOptions(l.revealedIn, "Not revealed")}</select></label>
      ${actSelect(`${p}.act`, l.act, "Planned to break in", "No act")}`;
    return [esc(l.title), `${esc(ownerName(l.owner))} ${gmTag()}`, body];
  }
  function drawerQuestion() {
    const d = D(), q = qById(S.d.id), answers = d.s0.answers[q.id] || [], set = QSETS.find((s) => s.id === q.set);
    const pcSel = (path, val, label, none) => `<label class="field"><span>${label}</span><select data-bind="${path}">${none ? `<option value="">${none}</option>` : ""}${PARTY.map((p) =>
      `<option value="${p.id}" ${val === p.id ? "selected" : ""}>${esc(p.name)}</option>`).join("")}</select></label>`;
    const rows = answers.map((a, k) => { const p = `s0.answers.${q.id}.${k}`;
      return `<div class="item" style="--pc:${pcById(a.pc).color};border-left:4px solid var(--pc)"><div class="ship">${pcSel(`${p}.pc`, a.pc, "Answered by")}${q.set === "crew" ? pcSel(`${p}.about`, a.about, "About", "—") : ""}</div>
        ${area(`${p}.text`, a.text, "Answer · canon once said", "A few sentences. Leave gaps on purpose.")}
        ${q.set === "personal" ? `<label class="field"><span>Follow-up (one, may be declined)</span><input data-bind="${p}.follow" value="${esc(a.follow || "")}"></label>
          <div class="gmonly"><div class="gmhd">${gmTag()}</div>${actSelect(`${p}.act`, a.act, "Pays off in")}</div>` : ""}
        <div><button type="button" class="linkbtn" data-delans="${k}">Remove this answer</button></div></div>`; }).join("");
    const nextPc = q.set === "crew" ? PARTY.find((p) => !allAnswers().some(({ q: x, a }) => x.set === "crew" && a.pc === p.id)) : null;
    const body = `<p style="margin:0;font-weight:700">${esc(d.s0.alt[q.id] || q.text)}</p>
      ${q.fit ? `<p class="warnline">${esc(q.fit)}</p>` : ""}
      ${area(`s0.alt.${q.id}`, d.s0.alt[q.id] || "", "Concord wording (optional)", q.text)}
      ${rows || `<p class="note">No answers yet.</p>`}
      <div><button type="button" class="btn" data-act="add-ans" data-pc="${(nextPc || PARTY[0]).id}">Add an answer</button></div>
      ${TRUTH_Q[q.id] ? `<div class="gmonly"><div class="gmhd">${gmTag()}</div>${area(`s0.truth.${q.id}`, d.s0.truth[q.id] || "", TRUTH_Q[q.id])}</div>` : ""}`;
    return [`${set.name} ${q.id.toUpperCase()}`, esc(set.how), body];
  }

  // ---------- Actions ----------
  function plantClue(k) {
    const l = ledgerById(S.d.id), c = l.clues[k];
    c.s = c.s == null ? D().session : null;
    if (c.s != null && l.state === "hidden") { l.state = "hinted"; toast(`Planted. ${l.title} is now hinted.`); }
  }
  function setState(id, st) {
    const l = ledgerById(id); l.state = st;
    if (st === "revealed" && l.revealedIn == null) l.revealedIn = D().session;
    if (st !== "revealed") l.revealedIn = null;
    if (st === "revealed") toast(l.reveal ? "Revealed. Players can read it under What we know." : "Revealed. Write what players read in the ledger entry.");
  }
  function planSession(act) {
    const d = D(), n = Math.max(0, ...d.sessions.map((s) => s.n)) + 1;
    d.sessions.push({ n, act, title: "", status: "plan", recap: "", prep: "", choices: [] });
    d.sessions.sort((a, b) => a.n - b.n);
    openDrawer("session", { n, focus: "[data-bind$='.title']" });
  }
  function startSession() {
    const d = D(), s = d.sessions.find((x) => x.n === S.d.n);
    d.sessions.forEach((x) => { if (x.status === "now") x.status = "played"; });
    s.status = "now"; d.session = s.n; d.act = s.act;
    if (!d.s0.done) d.s0.done = true;
    toast(`Session ${s.n} started. Clues you plant now are stamped S${s.n}.`);
  }
  function drawFor(pc, again) {
    const d = D(), held = new Set([...Object.entries(d.s0.draws).filter(([k]) => k !== pc).flatMap(([, v]) => v), ...Object.keys(d.s0.answers).filter((k) => k.startsWith("p") && (d.s0.answers[k] || []).length)]);
    if (again) (d.s0.draws[pc] || []).forEach((id) => held.add(id));
    const pool = Q.filter((q) => q.set === "personal" && !held.has(q.id)).map((q) => q.id);
    if (pool.length < 2) { toast("Not enough personal questions left in the deck"); return; }
    const a = pool.splice(Math.floor(Math.random() * pool.length), 1)[0], b = pool[Math.floor(Math.random() * pool.length)];
    d.s0.draws[pc] = [a, b];
  }
  function addAnswer(qid, pc) {
    const d = D(), q = qById(qid), list = (d.s0.answers[qid] = d.s0.answers[qid] || []);
    list.push({ pc, about: q.set === "crew" ? leftOf(pc) : undefined, text: "", follow: "", act: "" });
    return list.length - 1;
  }
  const commit = () => { save(); renderAll(); };

  function renderAll() { renderTop(); renderTabs(); renderMain(); renderDrawer(); }

  // ---------- Events ----------
  document.addEventListener("click", (e) => {
    const t = e.target.closest("button");
    if (!t) { if (e.target.id === "scrim") closeDrawer(); return; }
    const d = t.dataset;
    if (d.reset === undefined && t.id !== "reset" && S.resetArmed) { S.resetArmed = false; renderTop(); }
    if (d.view) { S.preview = d.view === "player"; if (S.preview && S.s0tab === "wrap") S.s0tab = "questions";
      if (S.preview && S.d.open && ["secret", "question"].includes(S.d.kind)) { closeDrawer(); return; } renderAll(); return; }
    if (d.tab) { S.tab = d.tab; renderTabs(); renderMain(); window.scrollTo({ top: 0 }); return; }
    if (d.actview) { S.actView = d.actview; renderMain(); return; }
    if (d.s0tab) { S.s0tab = d.s0tab; renderMain(); return; }
    if (d.group) { S.group = d.group; renderMain(); return; }
    if (d.sopen) { openDrawer("session", { n: Number(d.sopen) }); return; }
    if (d.nopen) { openDrawer("npc", { id: d.nopen }); return; }
    if (d.lopen) { if (gm()) openDrawer("secret", { id: d.lopen }); return; }
    if (d.qopen) { openDrawer("question", { id: d.qopen }); return; }
    if (d.plan) { planSession(d.plan); save(); renderMain(); return; }
    if (d.thread) { const th = D().threads; th[d.thread] = { plan: "live", live: "done", done: "plan" }[th[d.thread] || "plan"]; commit(); return; }
    if (d.state) { const [id, st] = d.state.split("|"); setState(id, st); commit(); return; }
    if (d.plant !== undefined) { plantClue(Number(d.plant)); commit(); return; }
    if (d.dial) { const [id, v] = d.dial.split("|"); D().s0.dials[id] = Number(v); commit(); return; }
    if (d.theme) { $("lv-topic").value = d.theme; $("lv-topic").focus(); return; }
    if (d.addlv) { const v = $("lv-topic").value.trim(); if (!v) { $("lv-topic").focus(); return; } D().s0.lv.push({ topic: v, kind: d.addlv }); commit(); return; }
    if (d.dellv !== undefined) { D().s0.lv.splice(Number(d.dellv), 1); commit(); return; }
    if (d.drawfor) { S.drawFor = d.drawfor; if (!D().s0.draws[d.drawfor]) drawFor(d.drawfor); commit(); return; }
    if (d.redraw) { drawFor(d.redraw, true); commit(); return; }
    if (d.keep) { const [pc, qid] = d.keep.split("|"), k = addAnswer(qid, pc); save(); openDrawer("question", { id: qid, focus: `[data-bind='s0.answers.${qid}.${k}.text']` }); return; }
    if (d.delans !== undefined) { const list = D().s0.answers[S.d.id]; list.splice(Number(d.delans), 1); if (!list.length) delete D().s0.answers[S.d.id]; commit(); return; }
    if (d.delchoice !== undefined) { D().sessions.find((s) => s.n === S.d.n).choices.splice(Number(d.delchoice), 1); commit(); return; }
    const act = d.act;
    if (act === "close") closeDrawer();
    else if (act === "add-ans") { const k = addAnswer(S.d.id, d.pc); save(); renderAll(); const f = $("drawer").querySelector(`[data-bind='s0.answers.${S.d.id}.${k}.text']`); if (f) f.focus(); }
    else if (act === "add-choice") { const v = $("choice-in").value.trim(); if (v) { D().sessions.find((s) => s.n === S.d.n).choices.push(v); commit(); $("choice-in").focus(); } }
    else if (act === "add-clue") { const v = $("clue-in").value.trim(); if (v) { ledgerById(S.d.id).clues.push({ t: v, s: null }); commit(); $("clue-in").focus(); } }
    else if (act === "start-session") { startSession(); commit(); }
    else if (act === "end-session") { const s = D().sessions.find((x) => x.n === S.d.n); s.status = "played"; toast(`Session ${s.n} played. Plan the next one under its act.`); commit(); }
    else if (act === "del-session") { const ss = D().sessions; ss.splice(ss.findIndex((s) => s.n === S.d.n), 1); save(); closeDrawer(); }
    else if (act === "s0-done") { D().s0.done = !D().s0.done; commit(); }
    else if (act === "reset") {
      if (!S.resetArmed) { S.resetArmed = true; renderTop(); return; }
      S.resetArmed = false; S.data = seed(S.kind); save(); S.drawFor = null; renderAll(); toast("Reset to the starting data");
    }
  });
  document.addEventListener("input", (e) => {
    const el = e.target;
    if (el.id === "castq") { S.q = el.value; const pos = el.selectionStart; renderMain(); const q = $("castq"); q.focus(); q.setSelectionRange(pos, pos); return; }
    if (el.dataset.bind && el.tagName !== "SELECT") { setPath(D(), el.dataset.bind, el.value); save(); }
  });
  document.addEventListener("change", (e) => {
    const el = e.target;
    if (el.id === "dataset") { S.kind = el.value; try { localStorage.setItem("ttg-campaign-kind", S.kind); } catch (err) {} S.data = load(S.kind); S.actView = null; S.drawFor = null; renderAll(); toast(S.kind === "blank" ? "Fresh campaign: nothing played yet. Start with Session 0." : "Sample campaign: tonight is Session 5"); return; }
    if (el.name === "twist") { D().twist = Number(el.value); commit(); return; }
    if (el.dataset.wrap !== undefined) { D().s0.wrap[el.dataset.wrap] = el.checked; commit(); return; }
    if (el.dataset.bind) {
      const v = el.tagName === "SELECT" && el.hasAttribute("data-num") ? (el.value === "" ? null : Number(el.value)) : el.value;
      setPath(D(), el.dataset.bind, v); save(); renderTop(); renderTabs(); renderMain();
      if (el.tagName === "SELECT") renderDrawer();
    }
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && S.d.open) closeDrawer();
    if (e.key === "Enter" && ["choice-in", "clue-in"].includes(e.target.id)) { e.preventDefault(); document.querySelector(`[data-act='add-${e.target.id.split("-")[0]}']`).click(); }
  });

  renderAll();
})();
