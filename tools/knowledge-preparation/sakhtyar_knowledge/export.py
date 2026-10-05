from __future__ import annotations
import json
import shutil
import uuid
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from psycopg.types.json import Jsonb
from .config import PIPELINE_VERSION
from .text import sha256_bytes

def export_package(db, cfg):
    out = cfg.output_root
    out.mkdir(parents=True, exist_ok=True)
    tmp = out / f"llm-input-{cfg.run_id}"
    if tmp.exists():
        shutil.rmtree(tmp)
    tmp.mkdir()
    files = []

    def dump(name, sql):
        fp = tmp / name
        count = 0
        with fp.open("w", encoding="utf-8", newline="\n") as f, db.conn.cursor() as c:
            c.execute(sql, (cfg.run_id,))
            cols = [d.name for d in c.description]
            for row in c:
                obj = {k:(str(v) if isinstance(v,uuid.UUID) else v) for k,v in zip(cols,row)}
                f.write(json.dumps(obj, ensure_ascii=False, default=str) + "\n")
                count += 1
        files.append({"path":name,"sha256":sha256_bytes(fp.read_bytes()),"records":count,"size_bytes":fp.stat().st_size})

    dump("source-documents.jsonl", """select d.id,d.correlation_id,d.canonical_path,d.filename,d.sha256,
         d.document_version,d.page_count,d.text_chars from knowledge_source_document d
         join knowledge_pipeline_run_document rd on rd.source_document_id=d.id where rd.run_id=%s""")
    dump("nodes.jsonl", """select id,source_document_id,correlation_id,node_index,node_type,normalized_text,
         text_hash,page_from,page_to,information_density,importance_score,metadata
         from knowledge_document_node where run_id=%s order by source_document_id,node_index""")
    dump("knowledge-candidates.jsonl", """select id,source_document_id,node_id,correlation_id,candidate_type,
         statement,statement_hash,domain,topics,applicability,authority,legal_refs,confidence,
         importance_score,provenance from knowledge_preparation_candidate where run_id=%s
         order by source_document_id,importance_score desc""")

    manifest = {"package_type":"SAKHTYAR_LLM_INPUT","schema_version":"1","pipeline_version":PIPELINE_VERSION,
                "workflow_id":str(cfg.workflow_id),"correlation_id":str(cfg.correlation_id),
                "run_id":str(cfg.run_id),"generated_at":datetime.now(timezone.utc).isoformat(),"files":files,
                "result_contract":"Return a SakhtYar V32 dataset package and preserve all provenance identifiers."}
    (tmp/"manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf-8")
    (tmp/"command.txt").write_text(
        "Process this corpus as evidence-grounded construction/legal knowledge. Never invent rules. "
        "Preserve workflow_id, correlation_id, run_id, source_document_id, source_node_id and page provenance. "
        "Return a SakhtYar V32-compatible dataset package. Keep uncertain items PENDING_REVIEW.\n", encoding="utf-8")

    target = out / f"SakhtYar-LLM-Input-{cfg.run_id}.zip"
    with zipfile.ZipFile(target,"w",zipfile.ZIP_DEFLATED) as z:
        for fp in tmp.iterdir():
            z.write(fp,fp.name)
    digest = sha256_bytes(target.read_bytes())
    with db.conn.cursor() as c:
        c.execute("""insert into knowledge_llm_export
                     (workflow_id,run_id,export_version,output_path,sha256,size_bytes,manifest,statistics)
                     values(%s,%s,%s,%s,%s,%s,%s,%s)""",
                  (cfg.workflow_id,cfg.run_id,datetime.now().strftime("%Y%m%d%H%M%S"),str(target),digest,
                   target.stat().st_size,Jsonb(manifest),Jsonb({"files":len(files)})))
    db.conn.commit()
    return target
