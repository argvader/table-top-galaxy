"""Probe a PDF: page count, per-page text layer, outline. Writes <out>/manifest.json.

Usage: probe.py <pdf> <out-dir>
A page counts as having text when it yields at least MIN_CHARS characters of extractable text.
"""
import json
import sys
from pathlib import Path

import fitz  # pymupdf

MIN_CHARS = 40


def page_info(page):
    text = page.get_text("text")
    return {"index": page.number, "label": page.get_label() or None,
            "chars": len(text.strip()), "images": len(page.get_images(full=False))}


def probe(pdf, out):
    doc = fitz.open(pdf)
    pages = [page_info(p) for p in doc]
    no_text = [p["index"] for p in pages if p["chars"] < MIN_CHARS]
    manifest = {"file": str(pdf), "pages": len(pages), "no_text_pages": no_text,
                "toc": [{"level": l, "title": t, "page": pg} for l, t, pg in doc.get_toc()],
                "page_detail": pages}
    out.mkdir(parents=True, exist_ok=True)
    (out / "manifest.json").write_text(json.dumps(manifest, indent=1))
    print(json.dumps({"pages": len(pages), "no_text": len(no_text), "toc_entries": len(manifest["toc"])}))


if __name__ == "__main__":
    probe(Path(sys.argv[1]), Path(sys.argv[2]))
