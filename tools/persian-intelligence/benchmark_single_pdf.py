from __future__ import annotations
import argparse, html, json, sys, time
from pathlib import Path
import pymupdf
ROOT=Path(__file__).resolve().parent; sys.path.insert(0,str(ROOT))
from sakhtyar_persian.normalizer import normalize_light, normalize_with_hazm
from sakhtyar_persian.quality import analyze
from sakhtyar_persian.legal_guard import preserves, protected_spans

BAD=("تهر ان","ش ورای","ض وابط","م صوبه","مو رخ","منط قه","صژرف","کژه","باال","عن.وان","پ ژ س")
def artifacts(t): return {x:t.count(x) for x in BAD if x in t}
def qdict(t):
    q=analyze(t)
    return q.dict() if hasattr(q,"dict") else dict(q.__dict__)
def main():
    a=argparse.ArgumentParser();a.add_argument("--pdf",required=True);a.add_argument("--out",required=True);a.add_argument("--max-pages",type=int,default=0);x=a.parse_args()
    out=Path(x.out);out.mkdir(parents=True,exist_ok=True);doc=pymupdf.open(x.pdf);limit=min(len(doc),x.max_pages or len(doc));rows=[];tall=time.time()
    for i in range(limit):
        t=time.time();raw=doc[i].get_text("text") or ""; light=normalize_light(raw); hazm=normalize_with_hazm(raw)
        candidates=[("raw",raw),("light",light),("hazm",hazm)]
        safe=[c for c in candidates if preserves(raw,c[1])]
        if not safe:safe=[("raw",raw)]
        scored=[(name,text,qdict(text)) for name,text in safe]
        selected=max(scored,key=lambda z:z[2]["score"])
        row={"page":i+1,"raw_chars":len(raw),"protected_spans":protected_spans(raw),
             "quality_raw":qdict(raw),"quality_light":qdict(light),"quality_hazm":qdict(hazm),
             "legal_light":preserves(raw,light),"legal_hazm":preserves(raw,hazm),
             "selected":selected[0],"selected_quality":selected[2],
             "artifacts_before":artifacts(raw),"artifacts_after":artifacts(selected[1]),
             "raw_text":raw,"light_text":light,"hazm_text":hazm,"selected_text":selected[1],
             "elapsed_ms":round((time.time()-t)*1000,1)}
        rows.append(row);print(f"[{i+1}/{limit}] selected={selected[0]} score={selected[2]['score']} artifacts={sum(row['artifacts_after'].values())}")
    summary={"pdf":str(Path(x.pdf).resolve()),"pages":limit,"elapsed_sec":round(time.time()-tall,2),
      "selected":{"raw":sum(r["selected"]=="raw" for r in rows),"light":sum(r["selected"]=="light" for r in rows),"hazm":sum(r["selected"]=="hazm" for r in rows)},
      "artifact_hits_before":sum(sum(r["artifacts_before"].values()) for r in rows),
      "artifact_hits_after":sum(sum(r["artifacts_after"].values()) for r in rows),
      "legal_light_failures":sum(not r["legal_light"] for r in rows),"legal_hazm_failures":sum(not r["legal_hazm"] for r in rows),
      "needs_ocr_pages":[r["page"] for r in rows if r["selected_quality"]["needs_ocr"]],
      "needs_repair_pages":[r["page"] for r in rows if r["selected_quality"]["needs_repair"]]}
    (out/"report.json").write_text(json.dumps({"summary":summary,"pages":rows},ensure_ascii=False,indent=2),encoding="utf-8")
    cards=[]
    for r in rows:
      cards.append(f"<section><h2>صفحه {r['page']} — {r['selected']}</h2><p>امتیاز: {r['selected_quality']['score']} | OCR: {r['selected_quality']['needs_ocr']} | Repair: {r['selected_quality']['needs_repair']}</p><h3>خام</h3><pre>{html.escape(r['raw_text'])}</pre><h3>منتخب</h3><pre>{html.escape(r['selected_text'])}</pre></section>")
    css="body{font-family:Tahoma,Arial;max-width:1400px;margin:auto;padding:24px;line-height:1.9;background:#f5f6f8}section{background:#fff;margin:18px 0;padding:20px;border-radius:12px}pre{white-space:pre-wrap;direction:rtl;text-align:right;background:#f0f1f3;padding:14px;border-radius:8px}"
    (out/"report.html").write_text(f'<!doctype html><html lang="fa" dir="rtl"><meta charset="utf-8"><title>SakhtYar Benchmark</title><style>{css}</style><body><h1>Benchmark تک‌فایل SakhtYar</h1><pre>{html.escape(json.dumps(summary,ensure_ascii=False,indent=2))}</pre>{"".join(cards)}</body></html>',encoding="utf-8")
    print("\nSUMMARY\n"+json.dumps(summary,ensure_ascii=False,indent=2));print("HTML:",out/"report.html");print("JSON:",out/"report.json")
if __name__=="__main__":main()