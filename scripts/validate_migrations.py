#!/usr/bin/env python3
"""Fast offline migration checks. Flyway execution still requires a real Postgres instance."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
folder=root/'backend/app/src/main/resources/db/migration'
entries={}
for f in folder.glob('V*__*.sql'):
    m=re.fullmatch(r'V(\d+)__[A-Za-z0-9_]+\.sql',f.name)
    if not m: raise SystemExit(f'Bad migration filename: {f}')
    i=int(m.group(1));entries.setdefault(i,[]).append(f.name)
    if not f.read_text(encoding='utf-8').strip():raise SystemExit(f'Empty migration: {f}')
if not entries or sorted(entries)!=list(range(1,max(entries)+1)):
    raise SystemExit('Missing migration number or empty set')
if any(len(names)!=1 for names in entries.values()):raise SystemExit('Duplicate migration version')
if max(entries)<45:raise SystemExit('Phase 5.15 migration missing')
print(f'PASS: {len(entries)} sequential migrations including V45. This is NOT a database execution test.')
