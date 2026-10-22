  // ---------- Genesys dice (face tables) ----------
  // s success, f failure, a advantage, t threat, r triumph, d despair
  const DICE = {
    proficiency: { name: "Proficiency", short: "d12", shape: "d12", color: "var(--d-prof)", hex: "#f2c230", ink: "#1b1b1d", pos: true,
      faces: ["", "s", "s", "ss", "ss", "a", "sa", "sa", "sa", "aa", "aa", "r"] },
    ability: { name: "Ability", short: "d8", shape: "d8", color: "var(--d-ability)", hex: "#3f9b46", ink: "#0d1f10", pos: true,
      faces: ["", "s", "s", "ss", "a", "a", "sa", "aa"] },
    boost: { name: "Boost", short: "d6", shape: "d6", color: "var(--d-boost)", hex: "#8fd0ea", ink: "#10222b", pos: true,
      faces: ["", "", "s", "sa", "aa", "a"] },
    challenge: { name: "Challenge", short: "d12", shape: "d12", color: "var(--d-chal)", hex: "#b8262f", ink: "#ffffff", pos: false,
      faces: ["", "f", "f", "ff", "ff", "t", "t", "ft", "ft", "tt", "tt", "d"] },
    difficulty: { name: "Difficulty", short: "d8", shape: "d8", color: "var(--d-diff)", hex: "#5b3c8f", ink: "#ffffff", pos: false,
      faces: ["", "f", "ff", "t", "t", "t", "tt", "ft"] },
    setback: { name: "Setback", short: "d6", shape: "d6", color: "var(--d-set)", hex: "#1b1b1d", ink: "#ffffff", pos: false,
      faces: ["", "", "f", "f", "t", "t"] },
  };
  const ORDER = ["proficiency", "ability", "boost", "challenge", "difficulty", "setback"];
  const SYM_NAME = { s: "Success", f: "Failure", a: "Advantage", t: "Threat", r: "Triumph", d: "Despair" };
  // Same glyphs as the <symbol> defs, reused to paint the 3D die faces.
  const GLYPH = {
    s: { d: "M12 1 L14.6 9.4 L23 12 L14.6 14.6 L12 23 L9.4 14.6 L1 12 L9.4 9.4 Z" },
    f: { d: "M4 6.5 L6.5 4 L12 9.5 L17.5 4 L20 6.5 L14.5 12 L20 17.5 L17.5 20 L12 14.5 L6.5 20 L4 17.5 L9.5 12 Z" },
    a: { d: "M12 2 L21 10 L18.2 12.6 L12 7.2 L5.8 12.6 L3 10 Z M12 10.5 L21 18.5 L18.2 21.1 L12 15.7 L5.8 21.1 L3 18.5 Z" },
    t: { d: "M12 22 L21 14 L18.2 11.4 L12 16.8 L5.8 11.4 L3 14 Z M12 13.5 L21 5.5 L18.2 2.9 L12 8.3 L5.8 2.9 L3 5.5 Z" },
    r: { ring: true, d: "M12 4.5 L13.7 10.3 L19.5 12 L13.7 13.7 L12 19.5 L10.3 13.7 L4.5 12 L10.3 10.3 Z" },
    d: { ring: true, d: "M7 8.6 L8.6 7 L12 10.4 L15.4 7 L17 8.6 L13.6 12 L17 15.4 L15.4 17 L12 13.6 L8.6 17 L7 15.4 L10.4 12 Z" },
  };

  const tally = (face) => {
    const c = { s: 0, f: 0, a: 0, t: 0, r: 0, d: 0 };
    for (const ch of face) c[ch]++;
    return c;
  };
  const rand = (n) => { const a = new Uint32Array(1); crypto.getRandomValues(a); return a[0] % n; };
  const randf = () => { const a = new Uint32Array(1); crypto.getRandomValues(a); return a[0] / 4294967296; };

  const poolList = (pool) => ORDER.flatMap((t) => Array(pool[t]).fill(t));
  function scoreDice(dice) {
    const t = dice.reduce((acc, d) => { const c = tally(d.face); for (const k in c) acc[k] += c[k]; return acc; },
      { s: 0, f: 0, a: 0, t: 0, r: 0, d: 0 });
    const netS = t.s + t.r - t.f - t.d;
    const netA = t.a - t.t;
    return { dice, raw: t, netS, netA, triumph: t.r, despair: t.d, success: netS >= 1 };
  }
  const instantRoll = (types) => types.map((type) => ({ type, face: DICE[type].faces[rand(DICE[type].faces.length)] }));

  // ---------- 3D dice: three.js render + cannon.js physics, walled in by the viewport ----------
  const Dice3D = (() => {
    const PX_PER_UNIT = 34, FOV = 30, GRAVITY = -60;
    const PHI = (1 + Math.sqrt(5)) / 2;
    let ok = false, renderer, scene, camera, world, floorMesh, walls = {}, live = [], raf = 0, fadeTimer = 0, bounds = { x: 6, z: 8 };
    const v3 = (x, y, z) => ({ x, y, z });
    const dot = (a, b) => a.x * b.x + a.y * b.y + a.z * b.z;
    const sub = (a, b) => v3(a.x - b.x, a.y - b.y, a.z - b.z);
    const cross = (a, b) => v3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
    const norm = (a) => { const l = Math.hypot(a.x, a.y, a.z); return v3(a.x / l, a.y / l, a.z / l); };
    const signs = (fn) => { const out = []; for (const i of [1, -1]) for (const j of [1, -1]) for (const k of [1, -1]) out.push(fn(i, j, k)); return out; };
    const cyc = (a, b) => [v3(0, a, b), v3(a, b, 0), v3(b, 0, a)];
    const pm = (fn) => [[1, 1], [1, -1], [-1, 1], [-1, -1]].flatMap(([i, j]) => fn(i, j));

    // Vertices + face normals per shape; faces are derived so every die shares one builder.
    const SOLIDS = {
      d6: { radius: 1.3, inset: 0.707, verts: signs((i, j, k) => v3(i, j, k)),
            normals: [v3(1, 0, 0), v3(-1, 0, 0), v3(0, 1, 0), v3(0, -1, 0), v3(0, 0, 1), v3(0, 0, -1)] },
      d8: { radius: 1.45, inset: 0.5, verts: [v3(1, 0, 0), v3(-1, 0, 0), v3(0, 1, 0), v3(0, -1, 0), v3(0, 0, 1), v3(0, 0, -1)],
            normals: signs((i, j, k) => v3(i, j, k)) },
      d12: { radius: 1.45, inset: 0.809,
             verts: [...signs((i, j, k) => v3(i, j, k)), ...pm((i, j) => cyc(i / PHI, j * PHI))],
             normals: pm((i, j) => cyc(i * PHI, j)) },
    };

    function faces(solid) {
      const verts = solid.verts.map(norm);
      return solid.normals.map((n0) => {
        const n = norm(n0);
        const best = Math.max(...verts.map((v) => dot(v, n)));
        const idx = verts.map((v, i) => [dot(v, n), i]).filter(([d]) => d > best - 1e-6).map(([, i]) => i);
        const c = idx.reduce((acc, i) => v3(acc.x + verts[i].x / idx.length, acc.y + verts[i].y / idx.length, acc.z + verts[i].z / idx.length), v3(0, 0, 0));
        const u = norm(sub(verts[idx[0]], c)), w = cross(n, u);
        const ang = (i) => { const p = sub(verts[i], c); return Math.atan2(dot(p, w), dot(p, u)); };
        idx.sort((a, b) => ang(a) - ang(b));
        const rFace = Math.max(...idx.map((i) => Math.hypot(...Object.values(sub(verts[i], c)))));
        return { n, idx, c, u, w, rFace };
      });
    }

    const texCache = new Map();
    function faceTexture(type, face, inset) {
      const key = `${type}|${face}`;
      if (texCache.has(key)) return texCache.get(key);
      const size = 256, cv = document.createElement("canvas");
      cv.width = cv.height = size;
      const g = cv.getContext("2d");
      g.fillStyle = DICE[type].hex; g.fillRect(0, 0, size, size);
      const syms = [...face];
      const room = size * 0.5 * inset * 0.8;
      const s = syms.length > 1 ? room * 0.95 : room * 1.35;
      syms.forEach((ch, i) => {
        const cx = size / 2 + (syms.length > 1 ? (i === 0 ? -s / 2 : s / 2) : 0);
        paintGlyph(g, GLYPH[ch], cx, size / 2, s, DICE[type].ink);
      });
      const tex = new THREE.CanvasTexture(cv);
      tex.anisotropy = 4;
      texCache.set(key, tex);
      return tex;
    }
    function paintGlyph(g, glyph, cx, cy, s, ink) {
      g.save();
      g.translate(cx - s / 2, cy - s / 2); g.scale(s / 24, s / 24);
      g.fillStyle = ink; g.strokeStyle = ink; g.lineWidth = 2.4;
      if (glyph.ring) { g.beginPath(); g.arc(12, 12, 10, 0, Math.PI * 2); g.stroke(); }
      g.fill(new Path2D(glyph.d));
      g.restore();
    }

    const shapeCache = {};
    function shape(kind) {
      if (shapeCache[kind]) return shapeCache[kind];
      const solid = SOLIDS[kind], fs = faces(solid), r = solid.radius;
      const verts = solid.verts.map(norm);
      const pos = [], nrm = [], uv = [], groups = [];
      fs.forEach((f, fi) => {
        const start = pos.length / 3;
        for (let k = 1; k < f.idx.length - 1; k++) {
          for (const vi of [f.idx[0], f.idx[k], f.idx[k + 1]]) {
            const v = verts[vi], p = sub(v, f.c);
            pos.push(v.x * r, v.y * r, v.z * r); nrm.push(f.n.x, f.n.y, f.n.z);
            uv.push(0.5 + 0.5 * dot(p, f.u) / f.rFace, 0.5 + 0.5 * dot(p, f.w) / f.rFace);
          }
        }
        groups.push([start, pos.length / 3 - start, fi]);
      });
      const geo = new THREE.BufferGeometry();
      geo.setAttribute("position", new THREE.Float32BufferAttribute(pos, 3));
      geo.setAttribute("normal", new THREE.Float32BufferAttribute(nrm, 3));
      geo.setAttribute("uv", new THREE.Float32BufferAttribute(uv, 2));
      groups.forEach(([s, c, m]) => geo.addGroup(s, c, m));
      const body = new CANNON.ConvexPolyhedron(verts.map((v) => new CANNON.Vec3(v.x * r, v.y * r, v.z * r)), fs.map((f) => f.idx.slice()));
      return (shapeCache[kind] = { geo, body, faces: fs, inset: solid.inset, radius: r });
    }

    function init() {
      if (!window.THREE || !window.CANNON) return false;
      try {
        const canvas = document.getElementById("dice3d");
        renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true });
      } catch (e) { return false; }
      renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
      renderer.shadowMap.enabled = true;
      scene = new THREE.Scene();
      camera = new THREE.PerspectiveCamera(FOV, 1, 0.1, 1000);
      scene.add(new THREE.HemisphereLight(0xffffff, 0x445566, 0.75));
      const sun = new THREE.DirectionalLight(0xffffff, 0.75);
      sun.position.set(-8, 30, 10); sun.castShadow = true;
      Object.assign(sun.shadow.camera, { left: -40, right: 40, top: 40, bottom: -40 });
      sun.shadow.mapSize.set(1024, 1024);
      sun.shadow.camera.updateProjectionMatrix();
      scene.add(sun);
      floorMesh = new THREE.Mesh(new THREE.PlaneGeometry(200, 200), new THREE.ShadowMaterial({ opacity: 0.22 }));
      floorMesh.rotation.x = -Math.PI / 2; floorMesh.receiveShadow = true;
      scene.add(floorMesh);
      buildWorld();
      resize();
      window.addEventListener("resize", resize);
      return (ok = true);
    }

    function buildWorld() {
      world = new CANNON.World();
      world.gravity.set(0, GRAVITY, 0);
      world.broadphase = new CANNON.NaiveBroadphase();
      world.solver.iterations = 12;
      const mat = new CANNON.Material("dice");
      world.addContactMaterial(new CANNON.ContactMaterial(mat, mat, { friction: 0.35, restitution: 0.35 }));
      world.defaultContactMaterial.friction = 0.35;
      world.defaultContactMaterial.restitution = 0.35;
      const plane = (axis, angle) => {
        const b = new CANNON.Body({ mass: 0, material: mat });
        b.addShape(new CANNON.Plane());
        if (axis) b.quaternion.setFromAxisAngle(axis, angle);
        world.addBody(b);
        return b;
      };
      const X = new CANNON.Vec3(1, 0, 0), Y = new CANNON.Vec3(0, 1, 0);
      walls = { floor: plane(X, -Math.PI / 2), ceil: plane(X, Math.PI / 2), left: plane(Y, Math.PI / 2),
                right: plane(Y, -Math.PI / 2), top: plane(null, 0), bottom: plane(Y, Math.PI) };
      walls.ceil.position.set(0, 9, 0);
      Dice3D.mat = mat;
    }

    // Size the world so one unit is PX_PER_UNIT pixels on any screen, then put the walls at the window edges.
    function resize() {
      const w = window.innerWidth, h = window.innerHeight;
      renderer.setSize(w, h, false);
      camera.aspect = w / h;
      const halfZ = h / 2 / PX_PER_UNIT, height = halfZ / Math.tan((FOV / 2) * Math.PI / 180);
      camera.position.set(0, height, 0); camera.up.set(0, 0, -1); camera.lookAt(0, 0, 0);
      camera.updateProjectionMatrix();
      bounds = { x: halfZ * camera.aspect - 0.4, z: halfZ - 0.4 };
      walls.left.position.set(-bounds.x, 0, 0); walls.right.position.set(bounds.x, 0, 0);
      walls.top.position.set(0, 0, -bounds.z); walls.bottom.position.set(0, 0, bounds.z);
      if (!raf) renderer.render(scene, camera);
    }

    function clear() {
      clearTimeout(fadeTimer);
      for (const d of live) { scene.remove(d.mesh); world.remove(d.body); }
      live = [];
      document.getElementById("dice3d").classList.remove("fade");
      renderer.render(scene, camera);
    }

    function spawn(type, i, n) {
      const sh = shape(DICE[type].shape);
      const mats = sh.faces.map((_, fi) => new THREE.MeshStandardMaterial({ map: faceTexture(type, DICE[type].faces[fi], sh.inset), roughness: 0.42, metalness: 0.05 }));
      const mesh = new THREE.Mesh(sh.geo, mats);
      mesh.castShadow = true;
      const body = new CANNON.Body({ mass: 1, material: Dice3D.mat, linearDamping: 0.12, angularDamping: 0.12 });
      body.addShape(sh.body);
      const cols = Math.max(1, Math.floor((bounds.x * 2 - 2) / 3));
      const col = i % cols, row = Math.floor(i / cols);
      body.position.set(-bounds.x + 1.5 + (col + 0.5) * ((bounds.x * 2 - 3) / cols), 2.5 + row * 1.5, bounds.z - 1.8 - row * 0.6);
      body.quaternion.setFromEuler(randf() * 6.28, randf() * 6.28, randf() * 6.28);
      body.velocity.set((randf() - 0.5) * 10, 2 + randf() * 4, -(22 + randf() * 14));
      body.angularVelocity.set((randf() - 0.5) * 40, (randf() - 0.5) * 40, (randf() - 0.5) * 40);
      world.addBody(body); scene.add(mesh);
      return { type, mesh, body, faces: sh.faces };
    }

    function upness(d) {
      let best = -2, at = 0;
      d.faces.forEach((f, i) => {
        const n = d.body.quaternion.vmult(new CANNON.Vec3(f.n.x, f.n.y, f.n.z));
        if (n.y > best) { best = n.y; at = i; }
      });
      return { best, at };
    }
    const topFace = (d) => DICE[d.type].faces[upness(d).at];
    // A die resting on an edge or against a wall gets a small hop so it lands flat.
    function nudgeCocked() {
      const cocked = live.filter((d) => upness(d).best < 0.9);
      for (const d of cocked) {
        d.body.velocity.set((randf() - 0.5) * 4, 6 + randf() * 3, (randf() - 0.5) * 4);
        d.body.angularVelocity.set((randf() - 0.5) * 12, (randf() - 0.5) * 12, (randf() - 0.5) * 12);
      }
      return cocked.length > 0;
    }
    const isCalm = (d) => d.body.velocity.length() < 0.12 && d.body.angularVelocity.length() < 0.12;

    function roll(types, onDone) {
      clear();
      live = types.map((t, i) => spawn(t, i, types.length));
      let last = performance.now(), started = last, still = 0, nudges = 0, damped = false;
      const step = (now) => {
        world.step(1 / 60, Math.min(0.05, (now - last) / 1000), 4);
        last = now;
        for (const d of live) { d.mesh.position.copy(d.body.position); d.mesh.quaternion.copy(d.body.quaternion); }
        renderer.render(scene, camera);
        if (!damped && now - started > 2200) {
          damped = true;
          for (const d of live) { d.body.linearDamping = 0.4; d.body.angularDamping = 0.4; }
        }
        still = live.every(isCalm) ? still + 1 : 0;
        if (still > 18 && nudges < 3 && nudgeCocked()) { nudges++; still = 0; }
        if (still > 18 || now - started > 8000) {
          raf = 0;
          onDone(live.map((d) => ({ type: d.type, face: topFace(d) })));
          fadeTimer = setTimeout(() => document.getElementById("dice3d").classList.add("fade"), 4500);
          return;
        }
        raf = requestAnimationFrame(step);
      };
      raf = requestAnimationFrame(step);
    }

    return { init, roll, clear, get ok() { return ok; }, get busy() { return raf !== 0; } };
  })();

