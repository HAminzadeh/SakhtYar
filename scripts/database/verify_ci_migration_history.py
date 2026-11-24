#!/usr/bin/env python3
"""CI-only verifier for the disposable DB; derive exact inventory from checked-out SQL."""
import argparse, sys
from migration_lifecycle import ROOT, MIG, inventory, history, history_gate, require, GateError

def main():
    p=argparse.ArgumentParser(); p.add_argument('--container',required=True); a=p.parse_args()
    require(a.container=='sakhtyar-ci-db','Only disposable CI container accepted')
    entries=inventory(ROOT/MIG); rows=history(a.container,'sakhtyar')
    require(not history_gate(rows,entries),'CI has pending migrations')
    print(f'PASS: all {len(entries)} source migrations through V{entries[-1]["version"]} applied in CI.')
if __name__=='__main__':
    try: main()
    except GateError as e: print(str(e),file=sys.stderr); sys.exit(1)
