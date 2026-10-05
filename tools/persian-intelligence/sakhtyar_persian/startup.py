from __future__ import annotations
import importlib.util,json,os,subprocess,sys
from pathlib import Path
class PersianIntelligenceStartupManager:
 def __init__(self,repo_root=None):
  self.root=Path(repo_root or Path(__file__).resolve().parents[3])
  self.main=self.root/".local/venv-persian-intelligence/Scripts/python.exe"
  self.ocr=self.root/".local/venv-persian-ocr-paddle/Scripts/python.exe"
 def status(self):
  return {"main_python":self.main.is_file(),"ocr_python":self.ocr.is_file(),
          "ocr_worker":(Path(__file__).with_name("paddle_worker_server.py")).is_file(),
          "cache":str(self.root/".local/persian-ocr-cache")}
 def assert_ready(self):
  bad=[k for k,v in self.status().items() if isinstance(v,bool) and not v]
  if bad:raise RuntimeError("Persian Intelligence startup missing: "+",".join(bad))
  return self.status()