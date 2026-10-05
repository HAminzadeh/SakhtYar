param(
    [string]$RepoRoot = ".",
    [ValidateSet("All","Preflight","Core","Python","Models","Knowledge","Java","Test")]
    [string]$Phase = "All",
    [switch]$InstallNlp,
    [switch]$InstallOcr,
    [switch]$InstallEmbedding,
    [switch]$InstallReranker,
    [switch]$DownloadModels,
    [switch]$SkipMaven
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Step([string]$Title) {
    Write-Host ""
    Write-Host ("=" * 78) -ForegroundColor DarkCyan
    Write-Host (" SakhtYar Persian Intelligence :: " + $Title) -ForegroundColor Cyan
    Write-Host ("=" * 78) -ForegroundColor DarkCyan
}
function Ensure-Dir([string]$Path) {
    if (-not (Test-Path $Path)) { New-Item -ItemType Directory -Force -Path $Path | Out-Null }
}
function Write-Utf8NoBom([string]$Path, [string]$Content) {
    Ensure-Dir (Split-Path -Parent $Path)
    $enc = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $enc)
}
function Backup-File([string]$Path) {
    if (Test-Path $Path) {
        $rel = $Path.Substring($script:Root.Length).TrimStart('\','/')
        $dst = Join-Path $script:BackupRoot $rel
        Ensure-Dir (Split-Path -Parent $dst)
        Copy-Item $Path $dst -Force
    }
}
function Replace-Once([string]$Path,[string]$Old,[string]$New) {
    if (-not (Test-Path $Path)) { throw "Required file not found: $Path" }
    Backup-File $Path
    $txt = [System.IO.File]::ReadAllText($Path)
    if ($txt.Contains($New)) { return }
    if (-not $txt.Contains($Old)) { throw "Expected anchor not found in $Path" }
    $txt = $txt.Replace($Old,$New)
    Write-Utf8NoBom $Path $txt
}
function Run-Python([string[]]$Args) {
    & $script:PythonExe @Args
    if ($LASTEXITCODE -ne 0) { throw "Python failed with exit code $LASTEXITCODE" }
}
function Want([string]$Name) { return ($Phase -eq "All" -or $Phase -eq $Name) }

$Root = (Resolve-Path $RepoRoot).Path
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$BackupRoot = Join-Path $Root ".local\patch-backups\persian-intelligence-$stamp"
Ensure-Dir $BackupRoot

$Tools = Join-Path $Root "tools\persian-intelligence"
$Pkg = Join-Path $Tools "sakhtyar_persian"
$Tests = Join-Path $Tools "tests"
$Models = Join-Path $Root "models\persian"
$KnowledgePkg = Join-Path $Root "tools\knowledge-preparation\sakhtyar_knowledge"
$BackendPom = Join-Path $Root "backend\pom.xml"
$AppPom = Join-Path $Root "backend\app\pom.xml"
$JavaModule = Join-Path $Root "backend\modules\persian-intelligence"
$Venv = Join-Path $Root ".local\venv-persian-intelligence"
$PythonExe = Join-Path $Venv "Scripts\python.exe"

Step "PRE-FLIGHT"
if (-not (Test-Path $BackendPom)) { throw "backend/pom.xml not found. Run from SakhtYar repository root or pass -RepoRoot." }
if (-not (Test-Path $KnowledgePkg)) { throw "Current Knowledge Preparation package was not found." }
Write-Host "Repository : $Root"
Write-Host "Backup     : $BackupRoot"
try {
    $branch = (& git -C $Root branch --show-current).Trim()
    $head = (& git -C $Root rev-parse --short HEAD).Trim()
    Write-Host "Git        : $branch @ $head"
} catch { Write-Warning "Git metadata unavailable." }

if (Want "Preflight") { exit 0 }

if (Want "Core") {
Step "PHASE 1/7 - CORE + MODEL REGISTRY"
Ensure-Dir $Pkg; Ensure-Dir $Tests; Ensure-Dir $Models

Write-Utf8NoBom (Join-Path $Pkg "__init__.py") @'
"""SakhtYar shared Persian Intelligence core."""
__version__ = "0.1.0"
'@

Write-Utf8NoBom (Join-Path $Pkg "legal_guard.py") @'
from __future__ import annotations
import re
from dataclasses import dataclass

_PATTERNS = [
    r"\b[RSKMC]\s*[-_]?\s*\d{2,5}\b",
    r"(?:ماده|تبصره|بند|جزء)\s*[۰-۹0-9]+(?:\s*[-–]\s*[۰-۹0-9]+)?",
    r"[۰-۹0-9]+(?:[./][۰-۹0-9]+){1,2}",
    r"[۰-۹0-9]+(?:[٫.][۰-۹0-9]+)?\s*(?:درصد|%|متر(?:مربع)?|سانتی.?متر|طبقه)",
]
_RX = re.compile("|".join(f"(?:{p})" for p in _PATTERNS), re.IGNORECASE)

@dataclass(frozen=True)
class ProtectedText:
    text: str
    spans: dict[str, str]

def protect(text: str) -> ProtectedText:
    spans: dict[str, str] = {}
    def repl(m: re.Match) -> str:
        token = f"__SYPROTECTED_{len(spans):04d}__"
        spans[token] = m.group(0)
        return token
    return ProtectedText(_RX.sub(repl, text or ""), spans)

def restore(text: str, spans: dict[str, str]) -> str:
    out = text
    for token, value in spans.items():
        out = out.replace(token, value)
    return out

def protected_spans(text: str) -> list[str]:
    return [m.group(0) for m in _RX.finditer(text or "")]

def preserves(before: str, after: str) -> bool:
    return protected_spans(before) == protected_spans(after)
'@

Write-Utf8NoBom (Join-Path $Pkg "normalizer.py") @'
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
'@

Write-Utf8NoBom (Join-Path $Pkg "quality.py") @'
from __future__ import annotations
import re
from dataclasses import dataclass, asdict

P = "\u0600-\u06ff"
_BAD = re.compile(r"[\ufffd\u0080-\u009f]")
_INJECTED_ZHE = re.compile(rf"(?<=[{P}])ژ(?=[{P}])")
_SPLIT = re.compile(rf"\b[{P}]{{2,}}\s+[{P}]\b")
_KNOWN = re.compile(r"(?:کژه|صژرف|ش\s+ورای|تهر\s+ان|منط\s+قه|ض\s+وابط|م\s+صوبه|مو\s+رخ|عن[.]وان|باال)")

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
'@

Write-Utf8NoBom (Join-Path $Pkg "terminology.py") @'
PROTECTED_TERMS = {
    "کمیسیون ماده پنج","شورای عالی شهرسازی و معماری","طرح تفصیلی","طرح جامع",
    "سطح اشغال","تراکم ساختمانی","پهنه مسکونی","پهنه فعالیت","پهنه مختلط",
    "پروانه ساختمانی","پایان کار","پلاک ثبتی","عقب‌نشینی","پارکینگ"
}
'@

Write-Utf8NoBom (Join-Path $Pkg "providers.py") @'
from __future__ import annotations
from functools import lru_cache

@lru_cache(maxsize=1)
def embedding_model():
    from sentence_transformers import SentenceTransformer
    return SentenceTransformer("Alibaba-NLP/gte-multilingual-base", trust_remote_code=True)

def embed(texts: list[str]):
    return embedding_model().encode(texts, normalize_embeddings=True)

@lru_cache(maxsize=1)
def reranker_model():
    from transformers import AutoModelForSequenceClassification, AutoTokenizer
    name="Alibaba-NLP/gte-multilingual-reranker-base"
    return AutoTokenizer.from_pretrained(name), AutoModelForSequenceClassification.from_pretrained(
        name, trust_remote_code=True)

@lru_cache(maxsize=1)
def hezar_ocr():
    from hezar.models import Model
    return Model.load("hezarai/crnn-base-fa-v2")

def ocr_images(paths: list[str]):
    return hezar_ocr().predict(paths)
'@

Write-Utf8NoBom (Join-Path $Pkg "service.py") @'
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
'@

Write-Utf8NoBom (Join-Path $Tools "model-registry.json") @'
{
  "schemaVersion": 1,
  "models": {
    "persian-ocr": {
      "provider": "huggingface",
      "id": "hezarai/crnn-base-fa-v2",
      "license": "apache-2.0",
      "optional": true,
      "purpose": "Persian printed/scanned OCR fallback"
    },
    "embedding": {
      "provider": "huggingface",
      "id": "Alibaba-NLP/gte-multilingual-base",
      "license": "apache-2.0",
      "optional": true,
      "purpose": "Multilingual semantic retrieval"
    },
    "reranker": {
      "provider": "huggingface",
      "id": "Alibaba-NLP/gte-multilingual-reranker-base",
      "license": "apache-2.0",
      "optional": true,
      "purpose": "Second-stage retrieval reranking"
    }
  }
}
'@

Write-Utf8NoBom (Join-Path $Tools "requirements-core.txt") @'
hazm>=0.10
'@
Write-Utf8NoBom (Join-Path $Tools "requirements-nlp.txt") @'
dadmatools[full]
'@
Write-Utf8NoBom (Join-Path $Tools "requirements-ocr.txt") @'
hezar
paddleocr
'@
Write-Utf8NoBom (Join-Path $Tools "requirements-semantic.txt") @'
sentence-transformers>=2.7
transformers>=4.36
huggingface-hub
'@

Write-Utf8NoBom (Join-Path $Tests "test_core.py") @'
import unittest
from sakhtyar_persian.normalizer import normalize_light
from sakhtyar_persian.legal_guard import preserves
from sakhtyar_persian.quality import analyze

class CoreTests(unittest.TestCase):
    def test_arabic_chars(self):
        self.assertEqual(normalize_light("كتاب شهري"), "کتاب شهری")
    def test_legal_spans(self):
        x="طبق ماده ۱۴ در پهنه R122 سطح اشغال 60 درصد است."
        self.assertTrue(preserves(x, normalize_light(x)))
    def test_bad_text_scores_lower(self):
        self.assertLess(analyze("تهر ان و ش ورای و کژه").score, analyze("تهران و شورای و که").score)

if __name__ == "__main__":
    unittest.main()
'@
}

if (Want "Python") {
Step "PHASE 2/7 - PYTHON ENVIRONMENT"
if (-not (Test-Path $PythonExe)) {
    $py = Get-Command py -ErrorAction SilentlyContinue
    if ($py) { & py -3.12 -m venv $Venv }
    else { & python -m venv $Venv }
}
if (-not (Test-Path $PythonExe)) { throw "Could not create Python virtual environment." }
& $PythonExe -m pip install --upgrade pip
& $PythonExe -m pip install -r (Join-Path $Tools "requirements-core.txt")
if ($InstallNlp) { & $PythonExe -m pip install -r (Join-Path $Tools "requirements-nlp.txt") }
if ($InstallOcr) { & $PythonExe -m pip install -r (Join-Path $Tools "requirements-ocr.txt") }
if ($InstallEmbedding -or $InstallReranker) {
    & $PythonExe -m pip install -r (Join-Path $Tools "requirements-semantic.txt")
}
if ($LASTEXITCODE -ne 0) { throw "Dependency installation failed." }
}

if (Want "Models") {
Step "PHASE 3/7 - OPTIONAL MODEL CACHE"
Ensure-Dir $Models
$env:HF_HOME = Join-Path $Models "huggingface"
if ($DownloadModels) {
    if (-not (Test-Path $PythonExe)) { throw "Run Python phase first." }
    if ($InstallEmbedding) {
        & $PythonExe -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('Alibaba-NLP/gte-multilingual-base', trust_remote_code=True); print('embedding cached')"
        if ($LASTEXITCODE -ne 0) { throw "Embedding model download failed." }
    }
    if ($InstallReranker) {
        & $PythonExe -c "from transformers import AutoTokenizer,AutoModelForSequenceClassification; n='Alibaba-NLP/gte-multilingual-reranker-base'; AutoTokenizer.from_pretrained(n); AutoModelForSequenceClassification.from_pretrained(n,trust_remote_code=True); print('reranker cached')"
        if ($LASTEXITCODE -ne 0) { throw "Reranker model download failed." }
    }
    if ($InstallOcr) {
        & $PythonExe -c "from hezar.models import Model; Model.load('hezarai/crnn-base-fa-v2'); print('hezar OCR cached')"
        if ($LASTEXITCODE -ne 0) { throw "OCR model download failed." }
    }
} else {
    Write-Host "Model download skipped. Use -DownloadModels with the relevant install switches when ready."
}
}

if (Want "Knowledge") {
Step "PHASE 4/7 - KNOWLEDGE PREPARATION ADAPTER"
$adapter = Join-Path $KnowledgePkg "persian_intelligence.py"
Write-Utf8NoBom $adapter @'
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
'@
Write-Host "Knowledge adapter installed. Existing pipeline remains backward compatible."
Write-Host "Integration is opt-in at this phase; no destructive rewrite of extraction history is performed."
}

if (Want "Java") {
Step "PHASE 5/7 - JAVA SHARED MODULE"
Ensure-Dir $JavaModule
Write-Utf8NoBom (Join-Path $JavaModule "pom.xml") @'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
 <modelVersion>4.0.0</modelVersion>
 <parent>
  <groupId>com.sakhtyar</groupId><artifactId>sakhtyar-backend-parent</artifactId>
  <version>0.2.0-SNAPSHOT</version><relativePath>../../pom.xml</relativePath>
 </parent>
 <artifactId>sakhtyar-persian-intelligence</artifactId>
 <dependencies>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
 </dependencies>
</project>
'@
$javaBase = Join-Path $JavaModule "src\main\java\com\sakhtyar\persian"
Write-Utf8NoBom (Join-Path $javaBase "PersianTextNormalizer.java") @'
package com.sakhtyar.persian;

import org.springframework.stereotype.Component;
import java.text.Normalizer;

@Component
public final class PersianTextNormalizer {
    public String normalize(String input) {
        if (input == null || input.isBlank()) return input;
        return Normalizer.normalize(input, Normalizer.Form.NFKC)
                .replace('ي','ی').replace('ى','ی').replace('ك','ک')
                .replace("\u200E","").replace("\u200F","").replace("\uFEFF","")
                .replaceAll("[\\t\\u00A0 ]+", " ").trim();
    }
}
'@
Replace-Once $BackendPom '    <module>modules/knowledge</module>' "    <module>modules/persian-intelligence</module>`r`n    <module>modules/knowledge</module>"
Replace-Once $BackendPom @'
      <dependency>
        <groupId>com.sakhtyar</groupId>
        <artifactId>sakhtyar-knowledge</artifactId>
        <version>${project.version}</version>
      </dependency>
'@ @'
      <dependency>
        <groupId>com.sakhtyar</groupId>
        <artifactId>sakhtyar-persian-intelligence</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>com.sakhtyar</groupId>
        <artifactId>sakhtyar-knowledge</artifactId>
        <version>${project.version}</version>
      </dependency>
'@
Replace-Once $AppPom '    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-knowledge</artifactId></dependency>' "    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-persian-intelligence</artifactId></dependency>`r`n    <dependency><groupId>com.sakhtyar</groupId><artifactId>sakhtyar-knowledge</artifactId></dependency>"
}

if (Want "Test") {
Step "PHASE 6/7 - TESTS + VALIDATION"
if (-not (Test-Path $PythonExe)) { throw "Python environment missing. Run -Phase Python first or -Phase All." }
$env:PYTHONPATH = $Tools
& $PythonExe -m unittest discover -s $Tests -p "test_*.py" -v
if ($LASTEXITCODE -ne 0) { throw "Persian Intelligence Python tests failed." }
& $PythonExe -m py_compile (Join-Path $Pkg "__init__.py") (Join-Path $Pkg "legal_guard.py") (Join-Path $Pkg "normalizer.py") (Join-Path $Pkg "quality.py") (Join-Path $Pkg "providers.py") (Join-Path $Pkg "service.py")
if ($LASTEXITCODE -ne 0) { throw "Python compile validation failed." }

if (-not $SkipMaven) {
    Push-Location (Join-Path $Root "backend")
    try {
        & mvn -pl modules/persian-intelligence -am test
        if ($LASTEXITCODE -ne 0) { throw "Maven tests failed." }
    } finally { Pop-Location }
}
}

if ($Phase -eq "All") {
Step "PHASE 7/7 - GIT SAFETY + SUMMARY"
$gitignore = Join-Path $Root ".gitignore"
Backup-File $gitignore
$g = [System.IO.File]::ReadAllText($gitignore)
$rules = @"

# =========================
# Persian Intelligence local model cache
# =========================
models/persian/
!models/persian/.gitkeep
"@
if (-not $g.Contains("models/persian/")) { Write-Utf8NoBom $gitignore ($g.TrimEnd() + $rules + "`r`n") }
Write-Utf8NoBom (Join-Path $Models ".gitkeep") ""

Write-Host ""
Write-Host "Installed successfully." -ForegroundColor Green
Write-Host "Core        : tools\persian-intelligence"
Write-Host "Java module : backend\modules\persian-intelligence"
Write-Host "Models      : models\persian (gitignored)"
Write-Host "Backup      : $BackupRoot"
Write-Host ""
Write-Host "Optional model install examples:"
Write-Host "  .\apply-sakhtyar-persian-intelligence.ps1 -Phase Python -InstallNlp"
Write-Host "  .\apply-sakhtyar-persian-intelligence.ps1 -Phase All -InstallOcr -InstallEmbedding -DownloadModels"
Write-Host ""
Write-Host "Next acceptance gate: benchmark the existing Tehran detailed-plan PDF before enabling OCR/model corrections in production."
}
