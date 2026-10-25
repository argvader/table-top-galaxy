"""Extract a PDF into <out>/pages.jsonl: one JSON object per page.

Usage: extract.py <pdf> <out-dir> [--tables]
Each page: {"index", "printed", "lines": [{"text", "size", "bold", "x", "y", "col"}], "tables": [[[cell]]]}
Lines keep font size and bold so later steps can find headings. "printed" is the page number
printed in the running header or footer, when one is found.
"""
import json
import re
import sys
from pathlib import Path

import fitz  # pymupdf

BOLD = 16

# The Genesys dice font: result symbols map by codepoint alone; die glyphs share one codepoint
# per shape, and only the fill color tells a positive die from its negative partner.
SYMBOLS = {0xF22B0: "failure", 0xF22B1: "threat", 0xF22B2: "despair",
           0xF22B3: "success", 0xF22B4: "advantage", 0xF22B5: "triumph"}
DIE_SHAPES = {0xF22B7: ("ability", "difficulty"), 0xF22B8: ("boost", "setback"), 0xF22BB: ("proficiency", "challenge")}


def die_kind(code, color):
    r, g, b = (color >> 16) & 255, (color >> 8) & 255, color & 255
    pos, neg = DIE_SHAPES[code]
    if code == 0xF22B7:
        return pos if g > r else neg            # green vs purple
    if code == 0xF22B8:
        return pos if b > 120 else neg          # light blue vs black
    return pos if g > 150 else neg              # yellow vs red


def tokenize(span):
    out = []
    for ch in span["text"]:
        code = ord(ch)
        if code in SYMBOLS:
            out.append(f"[{SYMBOLS[code]}]")
        elif code in DIE_SHAPES:
            out.append(f"[{die_kind(code, span['color'])}]")
        else:
            out.append(ch)
    return "".join(out)


def line_of(spans, page_width):
    text = "".join(tokenize(s) for s in spans).strip()
    if not text:
        return None
    main = max(spans, key=lambda s: len(s["text"]))
    x0 = spans[0]["bbox"][0]
    return {"text": text, "size": round(main["size"], 1), "bold": bool(main["flags"] & BOLD),
            "font": main["font"], "x": round(x0, 1), "y": round(spans[0]["bbox"][1], 1),
            "col": 0 if x0 < page_width / 2 else 1}


def page_lines(page):
    out = []
    for block in page.get_text("dict")["blocks"]:
        for line in block.get("lines", []):
            ln = line_of(line["spans"], page.rect.width)
            if ln:
                out.append(ln)
    # Reading order for two-column pages: left column top to bottom, then right.
    return sorted(out, key=lambda l: (l["col"], l["y"], l["x"]))


def printed_number(lines, height):
    edge = [l for l in lines if l["y"] < 60 or l["y"] > height - 60]
    nums = [l["text"] for l in edge if re.fullmatch(r"\d{1,3}", l["text"])]
    return int(nums[0]) if nums else None


def cell_text(lines, bbox):
    """Tokenized text of the spans whose centre falls inside a cell. Spans keep the PDF's own order within a
    line (sorting by x scrambles ligature fragments: "Counteroffer" -> "Counterofefr"); lines join with a space."""
    if bbox is None:
        return None
    x0, y0, x1, y1 = bbox
    def inside(s):
        cx, cy = (s["bbox"][0] + s["bbox"][2]) / 2, (s["bbox"][1] + s["bbox"][3]) / 2
        return x0 <= cx <= x1 and y0 <= cy <= y1
    parts = ["".join(tokenize(s) for s in spans if inside(s)) for spans in lines]
    return re.sub(r"\s+", " ", " ".join(p for p in parts if p.strip())).strip() or None


def page_tables(page):
    # Cells are rebuilt from spans (not table.extract()) so dice glyphs get the same color-aware tokens as body text.
    try:
        tables = page.find_tables().tables
    except Exception:
        return []
    lines = [l["spans"] for b in page.get_text("dict")["blocks"] for l in b.get("lines", [])]
    return [[[cell_text(lines, c) for c in row.cells] for row in t.rows] for t in tables]


def extract(pdf, out, with_tables):
    doc = fitz.open(pdf)
    out.mkdir(parents=True, exist_ok=True)
    with (out / "pages.jsonl").open("w") as f:
        for page in doc:
            lines = page_lines(page)
            rec = {"index": page.number, "printed": printed_number(lines, page.rect.height), "lines": lines,
                   "tables": page_tables(page) if with_tables else []}
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")
    print(json.dumps({"pages": len(doc)}))


if __name__ == "__main__":
    extract(Path(sys.argv[1]), Path(sys.argv[2]), "--tables" in sys.argv[3:])
