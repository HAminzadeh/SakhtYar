"""Verify runtime resource matches the source-of-truth catalog. Read-only."""
import json, sys
from pathlib import Path
root=Path(__file__).resolve().parents[1]
a=root/'quality-tests/catalog.json'
b=root/'backend/modules/quality/src/main/resources/quality/catalog.json'
try:
    ca=json.loads(a.read_text(encoding='utf-8'))
    cb=json.loads(b.read_text(encoding='utf-8'))
    assert ca==cb, 'Catalog mismatch: run scripts/sync_quality_catalog.py'
    ids=[x['id'] for x in ca]
    assert len(set(ids))==len(ids), 'Duplicate scenario ids'
    assert len(ids)>20, 'Empty scenario catalog'
    assert all(x['kind'] in {'ROUTE','SQL','EXTERNAL'} for x in ca)
    assert all(x['description'] and x['expected'] for x in ca)
    assert all(x['kind']!='EXTERNAL' or x['risk']!='read-only' for x in ca)
    print(f'QUALITY CATALOG VALID: {len(ids)} scenarios ({sum(x["kind"]!="EXTERNAL" for x in ca)} immediately executable).')
except Exception as e:
    print(f'QUALITY CATALOG ERROR: {e}',file=sys.stderr)
    raise SystemExit(1)
