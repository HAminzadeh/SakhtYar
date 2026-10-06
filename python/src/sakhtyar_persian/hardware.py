from __future__ import annotations
import json, os, platform, re, subprocess, time
from dataclasses import dataclass, asdict
from pathlib import Path

@dataclass
class GpuInfo:
    index:int; name:str; memory_total_mb:int=0; memory_free_mb:int=0; driver:str=""; cuda_driver:str=""; vendor:str="NVIDIA"

class HardwareProfiler:
    def __init__(self, repo_root: Path):
        self.repo_root=Path(repo_root); self.out=self.repo_root/'.local/hardware/hardware-profile.json'
    def _run(self, args):
        try: return subprocess.run(args,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=15).stdout.strip()
        except Exception: return ''
    def profile(self):
        gpus=[]
        smi=self._run(['nvidia-smi','--query-gpu=index,name,driver_version,memory.total,memory.free','--format=csv,noheader,nounits'])
        cuda=''
        top=self._run(['nvidia-smi'])
        m=re.search(r'CUDA Version:\s*([0-9.]+)',top); cuda=m.group(1) if m else ''
        for line in smi.splitlines():
            p=[x.strip() for x in line.split(',')]
            if len(p)>=5:
                try:gpus.append(asdict(GpuInfo(int(p[0]),p[1],int(float(p[3])),int(float(p[4])),p[2],cuda)))
                except Exception: pass
        intel=[]
        if platform.system()=='Windows':
            s=self._run(['powershell','-NoProfile','-Command',"Get-CimInstance Win32_VideoController | Select-Object -ExpandProperty Name"])
            intel=[x.strip() for x in s.splitlines() if 'Intel' in x]
        data={'generated_at':time.time(),'host':platform.node(),'platform':platform.platform(),'cpu_count':os.cpu_count() or 1,'nvidia_gpus':gpus,'other_gpus':intel}
        self.out.parent.mkdir(parents=True,exist_ok=True); self.out.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
        return data

class ComputePolicyManager:
    def __init__(self, repo_root: Path): self.root=Path(repo_root); self.path=self.root/'.local/hardware/compute-policy.json'
    def build(self, hw, paddle_gpu_ready=False):
        gpu=hw.get('nvidia_gpus',[None])[0] if hw.get('nvidia_gpus') else None
        # MX550 has only 2GB: keep one persistent GPU OCR worker and CPU pipeline workers.
        gpu_ocr=bool(gpu and paddle_gpu_ready and gpu.get('memory_total_mb',0)>=1500)
        cpu=max(1,int(hw.get('cpu_count',4))-2)
        policy={'mode':'HYBRID' if gpu_ocr else 'CPU_HYBRID_READY','native_extraction':'CPU','dedup':'CPU','quality_gate':'CPU',
                'fast_ocr':'GPU' if gpu_ocr else 'CPU','gpu_ocr_workers':1 if gpu_ocr else 0,'cpu_pipeline_workers':min(cpu,6),
                'cpu_ocr_assist':True,'cpu_ocr_workers':1,'ocr_queue_max':16,'intel_gpu_policy':'DETECTED_NOT_ASSIGNED',
                'notes':['OCR only for pages selected by page quality gate','Exact duplicate documents are skipped before expensive work','Low-value/broken documents are skipped before OCR']}
        self.path.parent.mkdir(parents=True,exist_ok=True); self.path.write_text(json.dumps(policy,ensure_ascii=False,indent=2),encoding='utf-8'); return policy