from __future__ import annotations
from pathlib import Path as _SakhtYarPath
import sys as _sakhtyar_sys
_SAKHTYAR_PYTHON_SRC = _SakhtYarPath(__file__).resolve().parents[2]
if str(_SAKHTYAR_PYTHON_SRC) not in _sakhtyar_sys.path:
    _sakhtyar_sys.path.insert(0, str(_SAKHTYAR_PYTHON_SRC))
import argparse, json, os, sys, uuid
from pathlib import Path

REPO=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(REPO/"tools"/"knowledge-preparation"))
sys.path.insert(0,str(REPO/"tools"/"persian-intelligence"))

from sakhtyar_knowledge.db import Database
from sakhtyar_knowledge.config import PIPELINE_VERSION
from sakhtyar_knowledge.versioning import VersionedOutput, VersionedOutputRepository
from sakhtyar_persian.intake import DocumentIntakeEngine

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--execution-id",required=True)
    a=ap.parse_args()
    eid=uuid.UUID(a.execution_id)
    db=Database()
    vr=VersionedOutputRepository(db.conn,PIPELINE_VERSION)
    try:
        with db.conn.cursor() as c:
            c.execute("""select e.selection_version_id,s.selected_document_ids
                         from knowledge_intake_execution e
                         join knowledge_intake_selection_version s on s.id=e.selection_version_id
                         where e.id=%s for update""",(eid,))
            row=c.fetchone()
            if not row: raise RuntimeError("execution not found")
            selection_id, selected_json=row
            selected=selected_json if isinstance(selected_json,list) else json.loads(selected_json)
            c.execute("""update knowledge_intake_execution set status='RUNNING',
                         current_stage='INTAKE_REVALIDATION',started_at=now() where id=%s""",(eid,))
        db.conn.commit()

        # This runner intentionally revalidates selected docs only. OCR is not launched for unselected docs.
        intake=DocumentIntakeEngine(REPO)
        completed=failed=0
        for sid in selected:
            try:
                # Selection IDs are versioned in DB; detailed document execution is wired by the main preparation run.
                vr.append(run_id=run_id, value=VersionedOutput(
                    stage_code="USER_SELECTION",
                    output_type="DOCUMENT_SELECTION",
                    logical_key=str(sid),
                    payload={"selected":True,"selection_version_id":str(selection_id)},
                    producer="selected_execution_v010",
                    producer_version="0.10.0",
                    provenance={"execution_id":str(eid)}
                ))
                completed+=1
            except Exception:
                failed+=1
        with db.conn.cursor() as c:
            c.execute("""update knowledge_intake_execution set status=%s,current_stage='SELECTION_VERSIONED',
                         completed_documents=%s,failed_documents=%s,finished_at=now(),
                         statistics=cast(%s as jsonb) where id=%s""",
                      ("COMPLETED" if failed==0 else "COMPLETED_WITH_ERRORS",completed,failed,
                       json.dumps({"selected_only":True,"ocr_started":False}),eid))
        db.conn.commit()
    except Exception as exc:
        db.conn.rollback()
        with db.conn.cursor() as c:
            c.execute("""update knowledge_intake_execution set status='FAILED',current_stage='FAILED',
                         error_message=%s,finished_at=now() where id=%s""",(str(exc)[:8000],eid))
        db.conn.commit()
        raise
    finally: db.close()

if __name__=="__main__": main()