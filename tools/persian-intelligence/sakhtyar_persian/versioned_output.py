from __future__ import annotations
import hashlib, json
from datetime import datetime, timezone
from pathlib import Path

class VersionedFileOutputStore:
    """Immutable local artifact versions; DB persistence is handled by knowledge version repository."""
    def __init__(self, repo_root: Path):
        self.root=Path(repo_root)/".local"/"versioned-artifacts"
        self.root.mkdir(parents=True,exist_ok=True)

    def write_json(self, stage: str, logical_key: str, payload, metadata=None):
        body=json.dumps(payload,ensure_ascii=False,sort_keys=True,indent=2)
        digest=hashlib.sha256(body.encode("utf-8")).hexdigest()
        safe="".join(c if c.isalnum() or c in "-_." else "_" for c in logical_key)[:100]
        folder=self.root/stage/safe
        folder.mkdir(parents=True,exist_ok=True)
        versions=sorted(folder.glob("v*.json"))
        if versions:
            try:
                old=json.loads(versions[-1].read_text(encoding="utf-8"))
                if old.get("sha256")==digest: return old
            except Exception: pass
        n=len(versions)+1
        record={"version":n,"stage":stage,"logical_key":logical_key,"sha256":digest,
                "created_at":datetime.now(timezone.utc).isoformat(),
                "metadata":metadata or {},"payload":payload}
        path=folder/f"v{n:04d}.json"
        path.write_text(json.dumps(record,ensure_ascii=False,indent=2),encoding="utf-8")
        current=folder/"current.json"
        current.write_text(json.dumps({"version":n,"path":str(path),"sha256":digest},ensure_ascii=False,indent=2),encoding="utf-8")
        return record | {"path":str(path)}
