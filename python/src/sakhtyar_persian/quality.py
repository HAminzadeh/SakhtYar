from __future__ import annotations
import re
from dataclasses import dataclass, asdict

P = "\u0600-\u06ff"
_BAD = re.compile(r"[\ufffd\u0080-\u009f]")
_INJECTED_ZHE = re.compile(rf"(?<=[{P}])Ú˜(?=[{P}])")
_SPLIT = re.compile(rf"\b[{P}]{{2,}}\s+[{P}]\b")
_KNOWN = re.compile(r"(?:Ú©Ú˜Ù‡|ØµÚ˜Ø±Ù|Ø´\s+ÙˆØ±Ø§ÛŒ|ØªÙ‡Ø±\s+Ø§Ù†|Ù…Ù†Ø·\s+Ù‚Ù‡|Ø¶\s+ÙˆØ§Ø¨Ø·|Ù…\s+ØµÙˆØ¨Ù‡|Ù…Ùˆ\s+Ø±Ø®|Ø¹Ù†[.]ÙˆØ§Ù†|Ø¨Ø§Ø§Ù„)")

@dataclass(frozen=True)
class Quality:
    score: float
    persian_ratio: float
    suspect_chars: int
    injected_zhe: int
    split_words: int
    known_artifacts: int
    needs_ocr: bool
    needs_repair: bool
    def dict(self): return asdict(self)

def analyze(text: str) -> Quality:
    t = text or ""
    if not t.strip():
        return Quality(0.0, 0.0, 0, 0, 0, 0, True, True)
    letters = re.findall(r"[A-Za-z\u0600-\u06ff]", t)
    persian = re.findall(rf"[{P}]", t)
    ratio = len(persian) / max(1, len(letters))
    suspect = len(_BAD.findall(t))
    zhe = len(_INJECTED_ZHE.findall(t))
    split = len(_SPLIT.findall(t))
    known = len(_KNOWN.findall(t))
    penalty = suspect*0.08 + zhe*0.04 + min(split,20)*0.012 + known*0.06
    if len(t) > 80 and ratio < .35: penalty += .30
    score = max(0.0, min(1.0, 1.0-penalty))
    return Quality(round(score,4), round(ratio,4), suspect, zhe, split, known,
                   score < .55, score < .88)