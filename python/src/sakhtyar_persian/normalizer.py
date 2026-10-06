from __future__ import annotations
import re, unicodedata
from .legal_guard import protect, restore

_WS = re.compile(r"[ \t\u00a0]+")
_ARABIC = str.maketrans({"ي":"ی","ى":"ی","ك":"ک","ۀ":"هٔ","ة":"ه"})
_BAD_CONTROLS = re.compile(r"[\u200e\u200f\ufeff]")
_PUNCT_SPACE = re.compile(r"\s+([،؛:,.!?؟)\]»])")
_OPEN_SPACE = re.compile(r"([(\[«])\s+")

def normalize_light(text: str) -> str:
    protected = protect(text or "")
    s = unicodedata.normalize("NFKC", protected.text)
    s = s.translate(_ARABIC)
    s = _BAD_CONTROLS.sub("", s)
    s = s.replace("\r\n","\n").replace("\r","\n")
    s = "\n".join(_WS.sub(" ", line).strip() for line in s.split("\n"))
    s = _PUNCT_SPACE.sub(r"\1", s)
    s = _OPEN_SPACE.sub(r"\1", s)
    s = re.sub(r"\n{3,}", "\n\n", s).strip()
    return restore(s, protected.spans)

def normalize_with_hazm(text: str) -> str:
    base = normalize_light(text)
    try:
        from hazm import Normalizer
    except Exception:
        return base
    protected = protect(base)
    result = Normalizer().normalize(protected.text)
    return restore(result, protected.spans)