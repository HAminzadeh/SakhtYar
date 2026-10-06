from __future__ import annotations
from pathlib import Path as _SakhtYarPath
import sys as _sakhtyar_sys
_SAKHTYAR_PYTHON_SRC = _SakhtYarPath(__file__).resolve().parents[2]
if str(_SAKHTYAR_PYTHON_SRC) not in _sakhtyar_sys.path:
    _sakhtyar_sys.path.insert(0, str(_SAKHTYAR_PYTHON_SRC))
import argparse,html,json,sys,time,tempfile
from pathlib import Path
import pymupdf
ROOT=Path(__file__).resolve().parent;sys.path.insert(0,str(ROOT))
from sakhtyar_persian.normalizer import normalize_light,normalize_with_hazm
from sakhtyar_persian.quality import analyze
from sakhtyar_persian.legal_guard import preserves,protected_spans
BAD=("تهر ان","ش ورای","ض وابط","م صوبه","مو رخ","منط قه","صژرف","کژه","باال","عن.وان","پ ژ س")
def art(t):return {x:t.count(x) for x in BAD if x in t}
def q(t):
 if not (t or "").strip():
  return {"score":0.0,"persian_ratio":0.0,"suspect_chars":0,"injected_zhe":0,"split_words":0,"known_artifacts":0,"needs_ocr":True,"needs_repair":True}
 return analyze(t).dict()
def ocr_image(path):
 import subprocess,os,json
 cp=subprocess.run([os.environ["SAKHTYAR_PADDLE_PYTHON"],os.environ["SAKHTYAR_PADDLE_WORKER"],str(path)],
                   capture_output=True,text=True,encoding="utf-8",errors="replace")
 if cp.returncode!=0: raise RuntimeError("Paddle worker failed: "+cp.stderr[-3000:])
 marker="__SAKHTYAR_OCR_JSON__"
 for line in reversed(cp.stdout.splitlines()):
  if line.startswith(marker): return json.loads(line[len(marker):]).get("text","")
 return ""
def candidate_score(text):
 x=q(text); penalty=sum(art(text).values())*.05
 return max(0,float(x["score"])-penalty),x
def main():
 a=argparse.ArgumentParser();a.add_argument("--pdf",required=True);a.add_argument("--out",required=True);a.add_argument("--dpi",type=int,default=220);a.add_argument("--max-ocr-pages",type=int,default=0);x=a.parse_args()
 out=Path(x.out);out.mkdir(parents=True,exist_ok=True);doc=pymupdf.open(x.pdf);rows=[];ocr_count=0;tall=time.time()
 for i,p in enumerate(doc):
  raw=p.get_text("text") or "";base=normalize_light(raw);bscore,bq=candidate_score(base)
  need=bool(bq["needs_ocr"] or bq["needs_repair"])
  otext="";oscore=-1;oq=None;selected=base;source="native";attempted=False;legal_ok=None;length_ok=None;score_ok=None;reject_reason=None
  if need and (x.max_ocr_pages<=0 or ocr_count<x.max_ocr_pages):
   ocr_count+=1;attempted=True
   with tempfile.TemporaryDirectory() as td:
    img=Path(td)/f"p{i+1}.png";pix=p.get_pixmap(dpi=x.dpi,alpha=False);pix.save(str(img));otext=normalize_with_hazm(ocr_image(img))
   oscore,oq=candidate_score(otext)
   native_spans=protected_spans(raw);legal_ok=True
   if native_spans: legal_ok=all(any(s.replace(" ","").casefold() in z.replace(" ","").casefold() for z in protected_spans(otext)) for s in native_spans)
   length_ok=len(otext)>=max(20,int(len(base)*.35));score_ok=oscore>=bscore+.08
   if legal_ok and length_ok and score_ok:selected=otext;source="ocr"
   else:
    reasons=[]
    if not legal_ok:reasons.append("legal")
    if not length_ok:reasons.append("length")
    if not score_ok:reasons.append("score")
    reject_reason=",".join(reasons)
  rows.append({"page":i+1,"source":source,"ocr_attempted":attempted,"reject_reason":reject_reason,"legal_ok":legal_ok,"length_ok":length_ok,"score_ok":score_ok,"native_quality":bq,"native_score":bscore,"ocr_quality":oq,"ocr_score":oscore if oq else None,
   "artifacts_native":art(base),"artifacts_ocr":art(otext) if otext else {},"protected_native":protected_spans(raw),"protected_selected":protected_spans(selected),
   "raw_text":raw,"native_text":base,"ocr_text":otext,"selected_text":selected})
  if attempted:
   preview=(otext[:100].replace("\n"," ") if otext else "<EMPTY>")
   print(f"[{i+1}/{len(doc)}] OCR ATTEMPT selected={source} native={bscore:.3f} ocr={oscore:.3f} chars={len(otext)} legal={legal_ok} length={length_ok} score={score_ok} reject={reject_reason} preview={preview}")
  else: print(f"[{i+1}/{len(doc)}] need={need} selected={source} native={bscore:.3f}")
 summary={"pages":len(rows),"ocr_attempted":sum(bool(r["ocr_attempted"]) for r in rows),"ocr_nonempty":sum(bool(r["ocr_text"]) for r in rows),"ocr_selected":sum(r["source"]=="ocr" for r in rows),
 "native_selected":sum(r["source"]=="native" for r in rows),"artifact_native":sum(sum(r["artifacts_native"].values()) for r in rows),
 "artifact_selected":sum(sum(art(r["selected_text"]).values()) for r in rows),"elapsed_sec":round(time.time()-tall,2)}
 (out/"report.json").write_text(json.dumps({"summary":summary,"pages":rows},ensure_ascii=False,indent=2),encoding="utf8")
 cards="".join(f"<section><h2>صفحه {r['page']} — {r['source']}</h2><h3>Native</h3><pre>{html.escape(r['native_text'])}</pre><h3>OCR</h3><pre>{html.escape(r['ocr_text'])}</pre><h3>Selected</h3><pre>{html.escape(r['selected_text'])}</pre></section>" for r in rows)
 css="body{font-family:Tahoma,Arial;max-width:1400px;margin:auto;padding:24px;direction:rtl;background:#f5f6f8}section{background:white;padding:18px;margin:18px 0;border-radius:12px}pre{white-space:pre-wrap;direction:rtl;text-align:right;background:#eee;padding:12px}"
 (out/"report.html").write_text(f'<!doctype html><html lang="fa" dir="rtl"><meta charset="utf-8"><style>{css}</style><body><h1>OCR Benchmark v0.4</h1><pre>{html.escape(json.dumps(summary,ensure_ascii=False,indent=2))}</pre>{cards}</body></html>',encoding="utf8")
 print("\nSUMMARY\n"+json.dumps(summary,ensure_ascii=False,indent=2));print("HTML:",out/"report.html")
if __name__=="__main__":main()