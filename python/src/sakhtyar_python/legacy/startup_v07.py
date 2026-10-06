from pathlib import Path as _SakhtYarPath
import sys as _sakhtyar_sys
_SAKHTYAR_PYTHON_SRC = _SakhtYarPath(__file__).resolve().parents[2]
if str(_SAKHTYAR_PYTHON_SRC) not in _sakhtyar_sys.path:
    _sakhtyar_sys.path.insert(0, str(_SAKHTYAR_PYTHON_SRC))
from pathlib import Path
import json, subprocess, sys
from sakhtyar_persian.hardware import HardwareProfiler, ComputePolicyManager

def check_gpu_paddle(root:Path):
    py=root/'.local/venv-persian-ocr-paddle-gpu/Scripts/python.exe'
    if not py.exists(): return False, 'GPU venv absent'
    try:
        p=subprocess.run([str(py),'-c',"import paddle; print(paddle.__version__); print(paddle.device.is_compiled_with_cuda()); print(paddle.device.cuda.device_count() if paddle.device.is_compiled_with_cuda() else 0)"],capture_output=True,text=True,timeout=30)
        lines=p.stdout.strip().splitlines(); ok=len(lines)>=3 and lines[-2].strip()=='True' and int(lines[-1])>0
        return ok,(p.stdout+p.stderr)[-1000:]
    except Exception as e:return False,str(e)

def main():
    root=Path(__file__).resolve().parents[2]
    hw=HardwareProfiler(root).profile(); ready,detail=check_gpu_paddle(root); policy=ComputePolicyManager(root).build(hw,ready)
    print(json.dumps({'hardware':hw,'paddle_gpu_ready':ready,'policy':policy},ensure_ascii=False,indent=2)); return 0
if __name__=='__main__':raise SystemExit(main())