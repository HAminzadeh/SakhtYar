from __future__ import annotations
import json
import uuid
import psycopg
from psycopg.types.json import Jsonb
from .config import PIPELINE_VERSION, db_dsn

class Database:
    def __init__(self):
        dsn, user, password = db_dsn()
        self.conn = psycopg.connect(dsn, user=user, password=password)

    def close(self):
        self.conn.close()

    def validate_identity(self, cfg):
        with self.conn.cursor() as c:
            c.execute("select workflow_id, correlation_id from knowledge_pipeline_run where id=%s", (cfg.run_id,))
            row = c.fetchone()
            if not row:
                raise RuntimeError(f"run_id not found: {cfg.run_id}")
            if row[0] != cfg.workflow_id or row[1] != cfg.correlation_id:
                raise RuntimeError("run identity mismatch; refusing to process a different workflow/correlation")
            c.execute("""update knowledge_pipeline_run
                         set status='RUNNING', stage='PREPARING', pipeline_version=%s, started_at=coalesce(started_at,now())
                         where id=%s""", (PIPELINE_VERSION, cfg.run_id))
        self.conn.commit()

    def event(self, run_id, event_type, message, level="INFO", details=None, source_document_id=None):
        with self.conn.cursor() as c:
            c.execute("""insert into knowledge_pipeline_event(run_id,source_document_id,event_type,level,message,details)
                         values(%s,%s,%s,%s,%s,%s)""",
                      (run_id, source_document_id, event_type, level, message, Jsonb(details or {})))
        self.conn.commit()

    def step_start(self, run_id, step, source_document_id=None):
        with self.conn.cursor() as c:
            c.execute("""select coalesce(max(attempt),0)+1 from knowledge_pipeline_step_run
                         where run_id=%s and source_document_id is not distinct from %s and step_code=%s""",
                      (run_id, source_document_id, step))
            attempt = c.fetchone()[0]
            c.execute("""insert into knowledge_pipeline_step_run(run_id,source_document_id,step_code,status,attempt,started_at)
                         values(%s,%s,%s,'RUNNING',%s,now()) returning id""",
                      (run_id, source_document_id, step, attempt))
            step_id = c.fetchone()[0]
        self.conn.commit()
        return step_id

    def step_finish(self, step_id, status="COMPLETED", stats=None, error=None):
        with self.conn.cursor() as c:
            c.execute("""update knowledge_pipeline_step_run
                         set status=%s, statistics=%s, error_message=%s, finished_at=now() where id=%s""",
                      (status, Jsonb(stats or {}), error, step_id))
        self.conn.commit()

    def source_version(self, path, digest, size):
        canonical = str(path.resolve())
        with self.conn.cursor() as c:
            c.execute("""select id,correlation_id,document_version,sha256
                         from knowledge_source_document where canonical_path=%s
                         order by document_version desc limit 1""", (canonical,))
            old = c.fetchone()
            if old and old[3] == digest:
                return old[0], old[1], old[2], True
            version = old[2] + 1 if old else 1
            corr = old[1] if old else uuid.uuid4()
            sid = uuid.uuid4()
            c.execute("""insert into knowledge_source_document
                         (id,correlation_id,canonical_path,filename,extension,sha256,size_bytes,document_version,
                          previous_document_id,status,extraction_version)
                         values(%s,%s,%s,%s,%s,%s,%s,%s,%s,'DISCOVERED',%s)""",
                      (sid,corr,canonical,path.name,path.suffix.lower(),digest,size,version,
                       old[0] if old else None,PIPELINE_VERSION))
        self.conn.commit()
        return sid, corr, version, False

    def mark_failed(self, run_id, message):
        with self.conn.cursor() as c:
            c.execute("""update knowledge_pipeline_run
                         set status='FAILED', stage='FAILED', error_message=%s, finished_at=now()
                         where id=%s""", (message[:8000], run_id))
        self.conn.commit()

    def mark_completed(self, run_id, stats):
        with self.conn.cursor() as c:
            c.execute("""update knowledge_pipeline_run
                         set status='COMPLETED', stage='ENRICHED', finished_at=now(),
                             statistics=coalesce(statistics,'{}'::jsonb) || %s
                         where id=%s""", (Jsonb(stats), run_id))
        self.conn.commit()
