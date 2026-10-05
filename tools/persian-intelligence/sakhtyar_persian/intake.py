from __future__ import annotations
import hashlib, json, re
from dataclasses import dataclass, asdict
from pathlib import Path

@dataclass
class DocumentDecision:
    path:str; sha256:str; status:str; reason:str; page_count:int=0; native_chars:int=0; suspicious_pages:list|None=None

class DocumentIntakeEngine:
    """Cheap-first intake: exact dedup -> file health -> native sample -> page gate. No OCR here."""
    def __init__(self, repo_root: Path):
        self.root=Path(repo_root); self.index_path=self.root/'.local/persian-intake/exact-dedup-index.json'
        self.index_path.parent.mkdir(parents=True,exist_ok=True)
        try:self.index=json.loads(self.index_path.read_text(encoding='utf-8'))
        except Exception:self.index={}
    def sha256(self,p:Path):
        h=hashlib.sha256()
        with p.open('rb') as f:
            for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
        return h.hexdigest()
    def inspect_pdf(self,p:Path):
        import fitz
        p=Path(p); sha=self.sha256(p)
        old=self.index.get(sha)
        if old and Path(old).resolve()!=p.resolve(): return DocumentDecision(str(p),sha,'SKIP_DUPLICATE_EXACT',f'exact duplicate of {old}')
        if p.stat().st_size<1024:return DocumentDecision(str(p),sha,'SKIP_LOW_QUALITY','file too small')
        try: doc=fitz.open(p)
        except Exception as e:return DocumentDecision(str(p),sha,'SKIP_BROKEN',str(e)[:200])
        if doc.page_count==0:return DocumentDecision(str(p),sha,'SKIP_LOW_QUALITY','zero pages')
        native=0; suspicious=[]
        for i,page in enumerate(doc):
            text=page.get_text('text') or ''; native+=len(text.strip())
            # conservative: empty/fragmented pages become OCR candidates, not document rejection.
            pers=len(re.findall(r'[\u0600-\u06FF]',text)); ratio=pers/max(1,len(text))
            if len(text.strip())<80 or (len(text)>150 and ratio<0.15): suspicious.append(i+1)
        self.index[sha]=str(p); self.index_path.write_text(json.dumps(self.index,ensure_ascii=False,indent=2),encoding='utf-8')
        status='VALID_SCANNED' if native<max(100,doc.page_count*30) else ('VALID_HYBRID' if suspicious else 'VALID_NATIVE')
        return DocumentDecision(str(p),sha,status,'accepted',doc.page_count,native,suspicious)