from __future__ import annotations
import json
import re
from pathlib import Path
from .rules import HEADING_RE
from .text import normalize

try:
    import pymupdf
except ImportError:
    pymupdf = None

def read_pages(path: Path) -> list[tuple[int, str, str, bool]]:
    ext = path.suffix.lower()
    if ext == ".pdf":
        if pymupdf is None:
            raise RuntimeError("pymupdf is required for PDF extraction")
        doc = pymupdf.open(path)
        out = []
        for i, page in enumerate(doc):
            raw = page.get_text("text")
            clean, repaired = normalize(raw)
            out.append((i + 1, raw, clean, repaired))
        return out
    raw = path.read_text(encoding="utf-8", errors="replace")
    if ext in {".html", ".htm"}:
        raw = re.sub(r"<[^>]+>", " ", raw)
    elif ext == ".json":
        try:
            raw = json.dumps(json.loads(raw), ensure_ascii=False, indent=2)
        except json.JSONDecodeError:
            pass
    clean, repaired = normalize(raw)
    return [(1, raw, clean, repaired)]

def split_nodes(pages):
    for page_no, _raw, text, _repaired in pages:
        blocks = [x.strip() for x in re.split(r"\n\s*\n", text) if x.strip()]
        for block in blocks:
            if len(block) < 20:
                continue
            parts = [block] if len(block) <= 7000 else [block[i:i+6000] for i in range(0, len(block), 6000)]
            for part in parts:
                yield page_no, ("HEADING" if len(part) < 180 and HEADING_RE.match(part) else "PARAGRAPH"), part
