from __future__ import annotations
import hashlib,json,os,subprocess,threading,time
from pathlib import Path
class PersistentPaddleOcr:
 def __init__(self,python_exe=None,worker=None,cache_root=None):
  root=Path(__file__).resolve().parents[3]
  self.python=Path(python_exe or os.getenv("SAKHTYAR_PADDLE_PYTHON",root/".local/venv-persian-ocr-paddle/Scripts/python.exe"))
  self.worker=Path(worker or Path(__file__).with_name("paddle_worker_server.py"))
  self.cache=Path(cache_root or root/".local/persian-ocr-cache");self.cache.mkdir(parents=True,exist_ok=True)
  self.p=None;self.lock=threading.Lock()
 def start(self):
  if self.p and self.p.poll() is None:return self
  env=os.environ.copy();env["PYTHONUTF8"]="1";env["PYTHONIOENCODING"]="utf-8"
  self.p=subprocess.Popen([str(self.python),"-u",str(self.worker)],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,encoding="utf-8",errors="replace",env=env,bufsize=1)
  ready=self.p.stdout.readline().strip()
  if not ready.startswith("__SAKHTYAR_READY__"): raise RuntimeError("OCR worker failed to start: "+ready)
  return self
 def recognize(self,image):
  image=Path(image);key=hashlib.sha256(image.read_bytes()).hexdigest();f=self.cache/(key+".json")
  if f.exists():return json.loads(f.read_text(encoding="utf-8"))|{"cached":True}
  self.start()
  with self.lock:
   self.p.stdin.write(json.dumps({"path":str(image)},ensure_ascii=True)+"\n");self.p.stdin.flush()
   line=self.p.stdout.readline()
  if not line:raise RuntimeError("OCR worker stopped: "+self.p.stderr.read()[-4000:])
  r=json.loads(line);r["cached"]=False
  f.write_text(json.dumps(r,ensure_ascii=False),encoding="utf-8");return r
 def close(self):
  if self.p and self.p.poll() is None:
   try:self.p.stdin.write('{"cmd":"quit"}\n');self.p.stdin.flush();self.p.wait(timeout=5)
   except Exception:self.p.kill()
  self.p=None
 def __enter__(self):return self.start()
 def __exit__(self,*_):self.close()