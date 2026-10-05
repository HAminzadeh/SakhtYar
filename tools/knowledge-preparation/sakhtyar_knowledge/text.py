from __future__ import annotations
import hashlib
import re
import unicodedata

_PERSIAN = re.compile(r"[\u0600-\u06ff]")
_SUSPECT = re.compile(r"[\u00c0-\u00ff\u0080-\u009f\ufffd]")
_WS = re.compile(r"[ \t\xa0]+")
_PLETTER = "\u0600-\u06ff"

# Common genuine Persian words containing ژ. We protect them before repairing
# the extraction artifact where PDF glyph mapping injects ژ inside words.
_REAL_ZHE = re.compile(
    r"(پروژه(?:ها|های|ای)?|ویژه(?:ها|های|ای)?|واژه(?:ها|های|ای)?|"
    r"انرژی|ژئوتکنیک|ژئودزی|ژئومتری|ژنتیک|ژورنال|ماژول|مونتاژ|گاراژ|"
    r"دژ|مژه|نژاد|کژ|ژرف)"
)

# High-confidence broken legal/construction phrases observed in extracted PDFs.
_PHRASE_FIXES = {
    "اس ت": "است",
    "اس.ت": "است",
    "الزم": "لازم",
    "االجرا": "الاجرا",
    "االحداث": "الاحداث",
    "عدم خالف": "عدم خلاف",
    "ذیصالح": "ذی‌صلاح",
    "تاییژد": "تایید",
    "تأییژد": "تأیید",
    "تصژویب": "تصویب",
    "کمیسژیون": "کمیسیون",
    "شژهر": "شهر",
    "شژهری": "شهری",
    "شژورای": "شورای",
    "مژاده": "ماده",
    "مژورخ": "مورخ",
    "مژورد": "مورد",
    "مژدارک": "مدارک",
    "مژذکور": "مذکور",
    "مژتناسب": "متناسب",
    "مژحل": "محل",
    "مژراجع": "مراجع",
    "صژدور": "صدور",
    "صژرفاً": "صرفاً",
    "ضژوابط": "ضوابط",
    "قطعژه": "قطعه",
    "طبقژه": "طبقه",
    "طبقژات": "طبقات",
    "منطقژه": "منطقه",
    "پژس": "پس",
    "پژایین": "پایین",
    "بژا": "با",
    "بژه": "به",
    "کژه": "که",
    "خ واهد": "خواهد",
    "م یزان": "میزان",
    "م ذکور": "مذکور",
    "مربو ط": "مربوط",
    "منو ط": "منوط",
    "ش ورای": "شورای",
    "تهر ان": "تهران",
}

def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def sha256_text(text: str) -> str:
    return sha256_bytes(text.encode("utf-8"))

def _score(text: str) -> tuple[int, int]:
    return (len(_SUSPECT.findall(text)), -len(_PERSIAN.findall(text)))

def repair_mojibake(text: str) -> tuple[str, bool]:
    if not text or not _SUSPECT.search(text):
        return text, False
    best = text
    changed = False
    for _ in range(3):
        candidates = [best]
        for enc in ("latin1", "cp1252"):
            try:
                candidates.append(best.encode(enc).decode("utf-8"))
            except (UnicodeEncodeError, UnicodeDecodeError):
                pass
        candidate = min(candidates, key=_score)
        if _score(candidate) < _score(best):
            best, changed = candidate, True
        else:
            break
    return best, changed

def repair_pdf_glyph_artifacts(text: str) -> tuple[str, int]:
    if not text:
        return text, 0
    original = text
    # Protect real words containing ژ.
    protected = {}
    def protect(m):
        key = f"\ue000{len(protected)}\ue001"
        protected[key] = m.group(0)
        return key
    s = _REAL_ZHE.sub(protect, text)

    # First apply high-confidence phrase corrections.
    for bad, good in _PHRASE_FIXES.items():
        s = s.replace(bad, good)

    # In this PDF family, ژ is frequently an injected glyph between Persian
    # letters. Remove it only inside a Persian token, never standalone.
    s = re.sub(rf"(?<=[{_PLETTER}])ژ(?=[{_PLETTER}])", "", s)

    # Repair a conservative set of split high-frequency function/legal words.
    joins = {
        "می باشد":"می‌باشد", "می شود":"می‌شود", "می گردد":"می‌گردد",
        "می تواند":"می‌تواند", "می توانند":"می‌توانند",
        "الزام ی":"الزامی", "ساختمان ها":"ساختمان‌ها",
        "پهنه ها":"پهنه‌ها", "پارکینگ ها":"پارکینگ‌ها",
        "مالکین ":"مالکین ", "همجواری ها":"همجواری‌ها",
    }
    for bad, good in joins.items():
        s = s.replace(bad, good)

    for key, value in protected.items():
        s = s.replace(key, value)
    changes = 0 if s == original else 1
    return s, changes

def normalize(text: str) -> tuple[str, bool]:
    repaired, mojibake_changed = repair_mojibake(text or "")
    s = unicodedata.normalize("NFKC", repaired)
    s = s.replace("\u064a", "\u06cc").replace("\u0649", "\u06cc").replace("\u0643", "\u06a9")
    s = s.replace("\u200f", "").replace("\ufeff", "")
    s, glyph_changed = repair_pdf_glyph_artifacts(s)
    s = _WS.sub(" ", s)
    s = re.sub(r"\n{3,}", "\n\n", s)
    return s.strip(), bool(mojibake_changed or glyph_changed)

def simhash64(text: str) -> str:
    tokens = re.findall(r"[\w\u0600-\u06ff]+", text.lower())
    if not tokens:
        return "0" * 16
    vector = [0] * 64
    for token in tokens:
        h = int(hashlib.blake2b(token.encode("utf-8"), digest_size=8).hexdigest(), 16)
        for i in range(64):
            vector[i] += 1 if h & (1 << i) else -1
    value = sum((1 << i) for i, n in enumerate(vector) if n >= 0)
    return f"{value:016x}"

def hamming(a: str, b: str) -> int:
    return (int(a, 16) ^ int(b, 16)).bit_count()

def persian_ratio(text: str) -> float:
    visible = [c for c in text if not c.isspace()]
    return 0.0 if not visible else len(_PERSIAN.findall(text)) / len(visible)

def suspect_count(text: str) -> int:
    return len(_SUSPECT.findall(text))

def extraction_artifact_count(text: str) -> int:
    # Signals not covered by mojibake detector: injected ژ and suspicious
    # one-letter splits inside Persian prose.
    injected = len(re.findall(rf"(?<=[{_PLETTER}])ژ(?=[{_PLETTER}])", text))
    split = len(re.findall(rf"\b[{_PLETTER}]{{2,}}\s[{_PLETTER}]\b", text))
    return injected + split
