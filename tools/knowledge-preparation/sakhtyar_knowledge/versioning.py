from __future__ import annotations
import hashlib, json
from dataclasses import dataclass
from pathlib import Path
from psycopg.types.json import Jsonb

def _sha(v: bytes) -> str:
    return hashlib.sha256(v).hexdigest()

@dataclass
class VersionedOutput:
    stage_code: str
    output_type: str
    logical_key: str
    payload: dict | None = None
    text: str | None = None
    artifact_path: str | None = None
    producer: str | None = None
    producer_version: str | None = None
    model_name: str | None = None
    model_revision: str | None = None
    runtime_info: dict | None = None
    provenance: dict | None = None

class VersionedOutputRepository:
    """Append-only persistence. Never overwrites a previous pipeline output."""
    def __init__(self, conn, pipeline_version: str):
        self.conn, self.pipeline_version = conn, pipeline_version

    def append(self, *, run_id, value: VersionedOutput, workflow_id=None,
               source_document_id=None, page_id=None, node_id=None,
               correlation_id=None, created_by="system"):
        canonical = value.text if value.text is not None else json.dumps(value.payload or {},ensure_ascii=False,sort_keys=True,separators=(",",":"))
        content_hash = _sha(canonical.encode("utf-8"))
        artifact_sha = artifact_size = None
        if value.artifact_path:
            p=Path(value.artifact_path)
            if p.is_file():
                h=hashlib.sha256()
                with p.open("rb") as f:
                    for b in iter(lambda:f.read(1024*1024),b""): h.update(b)
                artifact_sha, artifact_size = h.hexdigest(), p.stat().st_size
        with self.conn.transaction():
            with self.conn.cursor() as c:
                c.execute("""select id,version_no,content_hash,artifact_sha256
                  from knowledge_output_version
                  where run_id=%s and stage_code=%s and output_type=%s and logical_key=%s and status='CURRENT'
                  for update""",(run_id,value.stage_code,value.output_type,value.logical_key))
                old=c.fetchone()
                if old and old[2]==content_hash and (not artifact_sha or old[3]==artifact_sha):
                    return {"id":str(old[0]),"version":old[1],"unchanged":True}
                version=(old[1]+1) if old else 1
                if old:
                    c.execute("update knowledge_output_version set status='SUPERSEDED' where id=%s",(old[0],))
                c.execute("""insert into knowledge_output_version
                  (workflow_id,run_id,source_document_id,page_id,node_id,correlation_id,
                   stage_code,output_type,logical_key,version_no,pipeline_version,producer,producer_version,
                   model_name,model_revision,runtime_info,content_hash,payload,text_payload,
                   artifact_path,artifact_sha256,artifact_size_bytes,status,supersedes_id,provenance,created_by)
                  values(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,'CURRENT',%s,%s,%s)
                  returning id""",
                  (workflow_id,run_id,source_document_id,page_id,node_id,correlation_id,
                   value.stage_code,value.output_type,value.logical_key,version,self.pipeline_version,
                   value.producer,value.producer_version,value.model_name,value.model_revision,
                   Jsonb(value.runtime_info or {}),content_hash,Jsonb(value.payload or {}),value.text,
                   value.artifact_path,artifact_sha,artifact_size,old[0] if old else None,
                   Jsonb(value.provenance or {}),created_by))
                oid=c.fetchone()[0]
        return {"id":str(oid),"version":version,"unchanged":False}
