from __future__ import annotations
import re
from dataclasses import dataclass
_PATTERNS=[
 r"\b[RSKMC]\s*[-_]?\s*\d{2,5}\b",
 r"(?:ماده|تبصره|بند|جزء)\s*[۰-۹0-9]+(?:\s*[-–]\s*[۰-۹0-9]+)?",
 r"[۰-۹0-9]+(?:[./][۰-۹0-9]+){1,2}",
 r"[۰-۹0-9]+(?:[٫.][۰-۹0-9]+)?\s*(?:درصد|%|متر(?:\s*مربع)?|سانتی.?متر|طبقه)",
]
_RX=re.compile("|".join(f"(?:{p})" for p in _PATTERNS),re.I)
@dataclass(frozen=True)
class ProtectedText:
 text:str
 spans:dict[str,str]
def protect(text:str)->ProtectedText:
 spans={}
 def repl(m):
  # Hazm normalizes digits and inserts spaces into alphanumeric placeholders.
  # Use private-use Unicode sentinels with no letters/digits instead.
  idx=len(spans)
  token="\ue000"+chr(0xE100+idx)+"\ue001"
  spans[token]=m.group(0);return token
 return ProtectedText(_RX.sub(repl,text or ""),spans)
def restore(text:str,spans:dict[str,str])->str:
 out=text
 for token,value in spans.items(): out=out.replace(token,value)
 return out
def protected_spans(text:str)->list[str]:return [m.group(0) for m in _RX.finditer(text or "")]
def _canon(s:str)->str:
 return re.sub(r"\s+","",s).replace("ي","ی").replace("ك","ک").casefold()
def preserves(before:str,after:str)->bool:
 return [_canon(x) for x in protected_spans(before)]==[_canon(x) for x in protected_spans(after)]