from __future__ import annotations
from dataclasses import dataclass
from .normalizer import normalize_with_hazm
from .quality import analyze
from .legal_guard import preserves

@dataclass
class PersianResult:
    raw_text: str
    normalized_text: str
    quality_before: dict
    quality_after: dict
    protected_spans_preserved: bool

def process_text(text: str) -> PersianResult:
    before=analyze(text)
    normalized=normalize_with_hazm(text)
    after=analyze(normalized)
    return PersianResult(text, normalized, before.dict(), after.dict(), preserves(text, normalized))