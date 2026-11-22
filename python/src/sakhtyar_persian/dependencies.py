from __future__ import annotations
import importlib.util, json, os, subprocess, sys
from dataclasses import dataclass, asdict
from pathlib import Path

@dataclass(frozen=True)
class CapabilityStatus:
    capability: str
    status: str
    detail: str = ""

class RuntimeDependencyManager:
    MODULES = {
        "CORE": ("hazm",),
        "NLP": ("dadmatools",),
        "OCR": ("hezar", "paddleocr"),
        "SEMANTIC": ("sentence_transformers", "transformers"),
    }
    def __init__(self, model_root: str | None = None):
        self.model_root = Path(model_root or os.getenv("SAKHTYAR_PERSIAN_MODEL_DIR", ".local/models/persian"))
    def module_available(self, name: str) -> bool:
        return importlib.util.find_spec(name) is not None
    def status(self) -> list[CapabilityStatus]:
        out=[]
        for capability, modules in self.MODULES.items():
            missing=[m for m in modules if not self.module_available(m)]
            out.append(CapabilityStatus(capability, "READY" if not missing else "MISSING",
                                        "" if not missing else "missing: "+",".join(missing)))
        return out
    def require(self, capability: str) -> None:
        item=next(x for x in self.status() if x.capability == capability)
        if item.status != "READY":
            raise RuntimeError(f"{capability} is not ready: {item.detail}")
    def json_status(self) -> str:
        return json.dumps([asdict(x) for x in self.status()], ensure_ascii=False, indent=2)