#!/usr/bin/env python3
"""Offline source contract checks. Not business E2E, not DB proof."""
from pathlib import Path
import json,re,sys
ROOT=Path(__file__).resolve().parents[2]
def check(root):
    def read(p):return (root/p).read_text(encoding='utf-8-sig')
    mig=root/'backend/app/src/main/resources/db/migration'
    migrations=[]
    for p in mig.iterdir():
        m=re.fullmatch(r'V([1-9][0-9]*)__[A-Za-z0-9_]+\.sql',p.name)
        if not m or not p.is_file():raise AssertionError('Unexpected migration entry: '+p.name)
        migrations.append(int(m[1]))
    assert sorted(migrations)==list(range(1,max(migrations)+1)) and max(migrations)>=71,'Migration inventory invalid'
    sql=read('backend/app/src/main/resources/db/migration/V71__phase18_cost_snapshot_boundaries.sql')
    policy=json.loads(read('scripts/database/migration_runtime_policy.json'))
    sec=read('backend/modules/identity/src/main/java/com/sakhtyar/identity/config/SecurityConfig.java')
    app=read('frontend/src/App.tsx');page=read('frontend/src/pages/MaterialsPage.tsx')
    checks={
      'v69_v70_v71_runtime_requirements':all(str(n) in policy and policy[str(n)]['tables'] for n in (69,70,71)),
      'runtime_no_snapshot_mutation':all(policy['71']['tables'][t]['privileges']==['SELECT','INSERT'] and set(policy['71']['tables'][t]['forbidden'])=={'UPDATE','DELETE','TRUNCATE'} for t in ('cost_estimate','cost_estimate_line','scenario_cost_snapshot','scenario_cost_snapshot_line')),
      'no_role_or_table_destruction':not re.search(r'\b(?:GRANT|CREATE\s+ROLE|DROP\s+TABLE|TRUNCATE\s+TABLE|DELETE\s+FROM)\b',re.sub(r'--[^\n]*','',sql),re.I),
      'forced_scope':'FORCE ROW LEVEL SECURITY' in sql and 'AS RESTRICTIVE' in sql and 'sakhtyar_material_scope_ok' in sql,
      'snapshot_seal':'DEFERRABLE INITIALLY DEFERRED' in sql and 'creation_txid=txid_current()' in sql and 'actual<>expected' in sql,
      'currency_width':'ALTER COLUMN currency_code TYPE varchar(8)' in sql,
      'explicit_scope_http':all(x in sec for x in ('"/api/v1/assemblies/**"','"/api/v1/scenarios/**"','"/api/v1/materials/*/price-sources"')),
      'navigation_and_route':'path="/materials"' in app and 'permission={["CASE_READ", "USER_MANAGE"]}' in app,
      'client_reuse':"from '../api/client'" in page and 'fetch(' not in page,
      'no_fake_market_data':'/aggregates/history' in page and '/price-sources' in page and "hasPermission('USER_MANAGE')" in page,
      'separate_history_and_current':'/aggregates/rebuild' in page and '/anomalies' in page,
    }
    for k,v in checks.items():print(('PASS' if v else 'FAIL'),k)
    if not all(checks.values()):raise AssertionError('Offline source contract failed')
    print('PASS_OFFLINE_SOURCE_CONTRACTS_ONLY; runtime, DB and E2E NOT proven')
if __name__=='__main__':
    try:check(ROOT)
    except Exception as e:print('FAIL',e,file=sys.stderr);sys.exit(1)
