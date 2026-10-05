from __future__ import annotations
import argparse, os, subprocess, sys
from pathlib import Path

def parse():
    p=argparse.ArgumentParser()
    p.add_argument("--run-id",required=True)
    p.add_argument("--workflow-id",required=True)
    p.add_argument("--correlation-id",required=True)
    p.add_argument("--input-root",required=True)
    p.add_argument("--output-root",required=True)
    return p.parse_args()

def main():
    a=parse()
    here=Path(__file__).resolve().parent
    deep=here/"deep_prepare.py"
    enrich=here/"enrich_v13.py"
    if not deep.exists(): raise SystemExit(f"missing {deep}")
    if not enrich.exists(): raise SystemExit(f"missing {enrich}")

    # Existing extractor remains the source of page/node creation.
    # It must receive the exact run identity; no "latest run" lookup is allowed after this migration.
    env=os.environ.copy()
    env["SAKHTYAR_RUN_ID"]=a.run_id
    env["SAKHTYAR_WORKFLOW_ID"]=a.workflow_id
    env["SAKHTYAR_CORRELATION_ID"]=a.correlation_id

    # Compatibility: current deep_prepare CLI is invoked by the existing runner.
    # pipeline.py intentionally refuses to guess its arguments until adapter mode is installed.
    # For now enrichment can safely target an explicit existing run.
    if env.get("SAKHTYAR_SKIP_EXTRACT")!="1":
        raise SystemExit(
            "pipeline.py installed successfully. Deep extractor explicit-run adapter is required "
            "before API execution; use SAKHTYAR_SKIP_EXTRACT=1 only for an already prepared run."
        )
    subprocess.run([sys.executable,str(enrich),a.run_id],check=True,env=env)

if __name__=="__main__":
    main()
