  // ---------- SVG (2D dice in the tray, steppers and result card) ----------
  const SHAPES = {
    d6: '<rect x="4" y="4" width="40" height="40" rx="7"/>',
    d8: '<polygon points="24,1.5 46.5,24 24,46.5 1.5,24"/>',
    d12: '<polygon points="24,2 43.5,13 43.5,35 24,46 4.5,35 4.5,13"/>',
  };
  function dieSVG(type, face, label) {
    const d = DICE[type];
    const syms = face ? [...face] : [];
    const size = syms.length > 1 ? 15 : 20;
    const xs = syms.length > 1 ? [24 - size - 0.5, 24 + 0.5] : [24 - size / 2];
    const glyphs = syms.map((s, i) => `<use href="#sym-${s}" x="${xs[i]}" y="${24 - size / 2}" width="${size}" height="${size}"/>`).join("");
    const title = label || `${d.name} die${face ? ": " + syms.map((s) => SYM_NAME[s]).join(", ") : face === "" ? ": blank" : ""}`;
    return `<svg viewBox="0 0 48 48" role="img" aria-label="${title}"><g fill="${d.color}" stroke="var(--die-stroke)" stroke-width="1.5">${SHAPES[d.shape]}</g><g style="color:${d.ink}">${glyphs}</g></svg>`;
  }
  const sym = (s, n = 1) => Array.from({ length: n }, () => `<svg viewBox="0 0 24 24" aria-hidden="true"><use href="#sym-${s}"/></svg>`).join("");

