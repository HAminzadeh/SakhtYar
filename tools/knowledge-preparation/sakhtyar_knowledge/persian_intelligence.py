from __future__ import annotations
import sys
from pathlib import Path

_ROOT = Path(__file__).resolve().parents[3]
_PI = _ROOT / "persian-intelligence"
if str(_PI) not in sys.path:
    sys.path.insert(0, str(_PI))

from sakhtyar_persian.service import process_text
from sakhtyar_persian.quality import analyze

def enhance(text: str) -> tuple[str, dict]:
    result = process_text(text)
    meta = {
        "persian_intelligence": "0.1.0",
        "quality_before": result.quality_before,
        "quality_after": result.quality_after,
        "protected_spans_preserved": result.protected_spans_preserved,
    }
    if not result.protected_spans_preserved:
        return text, {**meta, "rejected": True}
    return result.normalized_text, meta