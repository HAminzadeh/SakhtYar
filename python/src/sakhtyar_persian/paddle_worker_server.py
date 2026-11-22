from __future__ import annotations
import contextlib,io,json,sys,time
from paddleocr import PaddleOCR
def collect(o,out):
 if o is None:return
 if isinstance(o,str):
  if o.strip():out.append(o.strip())
 elif isinstance(o,dict):
  hit=False
  for k in ("rec_texts","texts","text","rec_text"):
   if k in o:collect(o[k],out);hit=True
  if not hit:
   for v in o.values():collect(v,out)
 elif isinstance(o,(list,tuple)):
  if len(o)==2 and isinstance(o[1],(list,tuple)) and o[1] and isinstance(o[1][0],str):collect(o[1][0],out)
  else:
   for v in o:collect(v,out)
 else:
  for a in ("json","res"):
   try:v=getattr(o,a);v=v() if callable(v) else v;collect(v,out);return
   except Exception:pass
with contextlib.redirect_stdout(sys.stderr):
 try: engine=PaddleOCR(lang="fa",use_doc_orientation_classify=False,use_doc_unwarping=False,use_textline_orientation=False)
 except Exception: engine=PaddleOCR(lang="ar",use_doc_orientation_classify=False,use_doc_unwarping=False,use_textline_orientation=False)
print("__SAKHTYAR_READY__",flush=True)
for line in sys.stdin:
 try:
  q=json.loads(line)
  if q.get("cmd")=="quit":break
  t=time.perf_counter()
  with contextlib.redirect_stdout(sys.stderr):
   try:r=engine.predict(q["path"])
   except Exception:r=engine.ocr(q["path"])
  out=[];collect(r,out);seen=set();clean=[]
  for x in out:
   if x not in seen:seen.add(x);clean.append(x)
  print(json.dumps({"ok":True,"text":"\n".join(clean),"elapsed_sec":round(time.perf_counter()-t,3)},ensure_ascii=True),flush=True)
 except Exception as e:print(json.dumps({"ok":False,"text":"","error":repr(e)},ensure_ascii=True),flush=True)