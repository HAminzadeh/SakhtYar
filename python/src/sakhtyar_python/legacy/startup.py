from pathlib import Path as _SakhtYarPath
import sys as _sakhtyar_sys
_SAKHTYAR_PYTHON_SRC = _SakhtYarPath(__file__).resolve().parents[2]
if str(_SAKHTYAR_PYTHON_SRC) not in _sakhtyar_sys.path:
    _sakhtyar_sys.path.insert(0, str(_SAKHTYAR_PYTHON_SRC))
from sakhtyar_persian.startup import PersianIntelligenceStartupManager
if __name__=="__main__":
 m=PersianIntelligenceStartupManager();print(m.assert_ready())