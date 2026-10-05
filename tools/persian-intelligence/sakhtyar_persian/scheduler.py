from __future__ import annotations
from concurrent.futures import ThreadPoolExecutor, Future
from queue import Queue
class AdaptiveWorkScheduler:
    """Bounded hybrid scheduler. CPU prepares documents/pages while one low-VRAM GPU worker handles selected OCR pages."""
    def __init__(self, cpu_workers=4, queue_max=16):
        self.cpu=ThreadPoolExecutor(max_workers=max(1,cpu_workers),thread_name_prefix='sakhtyar-cpu'); self.ocr_queue=Queue(maxsize=max(2,queue_max))
    def submit_cpu(self, fn, *a, **kw)->Future:return self.cpu.submit(fn,*a,**kw)
    def close(self):self.cpu.shutdown(wait=True,cancel_futures=False)