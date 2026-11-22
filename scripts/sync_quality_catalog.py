"""Sync project-owned quality-tests/catalog.json into backend classpath resource."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
a=root/'quality-tests/catalog.json'
b=root/'backend/modules/quality/src/main/resources/quality/catalog.json'
items=json.loads(a.read_text(encoding='utf-8'))
assert isinstance(items,list) and len({x['id'] for x in items})==len(items)
b.write_text(json.dumps(items,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f'Synced {len(items)} scenarios')
