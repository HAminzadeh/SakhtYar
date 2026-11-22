#!/usr/bin/env python3
from __future__ import annotations
import argparse
import sys
from pathlib import Path
from uuid import UUID
from sakhtyar_knowledge.config import PipelineConfig
from sakhtyar_knowledge.db import Database
from sakhtyar_knowledge.engine import PreparationEngine
from sakhtyar_knowledge.export import export_package

def parse_args():
    p = argparse.ArgumentParser(description="SakhtYar Knowledge Preparation v3")
    p.add_argument("--run-id", required=True, type=UUID)
    p.add_argument("--workflow-id", required=True, type=UUID)
    p.add_argument("--correlation-id", required=True, type=UUID)
    p.add_argument("--input-root", required=True, type=Path)
    p.add_argument("--output-root", required=True, type=Path)
    p.add_argument("--no-export", action="store_true")
    return p.parse_args()

def main():
    a = parse_args()
    cfg = PipelineConfig(a.run_id,a.workflow_id,a.correlation_id,a.input_root.resolve(),a.output_root.resolve())
    db = Database()
    try:
        db.validate_identity(cfg)
        db.event(cfg.run_id,"PIPELINE_STARTED","Knowledge Preparation v3 started",
                 details={"input":str(cfg.input_root),"output":str(cfg.output_root)})
        stats = PreparationEngine(db,cfg).run()
        package = None if a.no_export else export_package(db,cfg)
        stats["export"] = str(package) if package else None
        db.mark_completed(cfg.run_id,stats)
        db.event(cfg.run_id,"PIPELINE_COMPLETED","Knowledge Preparation v3 completed",details=stats)
        print(f"RUN_ID={cfg.run_id}")
        print(f"STATUS=COMPLETED")
        if package:
            print(f"EXPORT={package}")
        print(f"STATS={stats}")
        return 0
    except Exception as exc:
        try:
            db.conn.rollback()
            db.mark_failed(cfg.run_id,str(exc))
            db.event(cfg.run_id,"PIPELINE_FAILED",str(exc),"ERROR")
        except Exception:
            pass
        print(f"Knowledge preparation failed: {exc}",file=sys.stderr)
        return 1
    finally:
        db.close()

if __name__ == "__main__":
    raise SystemExit(main())
