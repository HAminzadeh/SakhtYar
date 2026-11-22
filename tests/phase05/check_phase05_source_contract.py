#!/usr/bin/env python3
"""Static SOURCE_ONLY check. It DOES NOT prove HTTP, RLS, or runtime isolation."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[2]
checks={
 'backend/modules/analysis/src/main/java/com/sakhtyar/analysis/application/AnalysisSnapshotService.java':[
     'caseAccess.requireWrite(r.caseId())','caseAccess.requireRead(e.getCaseId())',
     'caseAccess.requireRead(caseId)','caseAccess.canRead(e.getCaseId())',
     'caseAccess.requireRead(snapshot.getCaseId())'],
 'backend/modules/analysis/src/main/java/com/sakhtyar/analysis/feasibility/application/FeasibilityService.java':[
     'caseAccess.requireWrite(r.caseId())','caseAccess.requireRead(caseId)',
     'caseAccess.canRead(e.getCaseId())','caseAccess.requireRead(e.getCaseId())'],
 'backend/modules/finance/src/main/java/com/sakhtyar/finance/application/FinancialCalculator.java':[
     'sorted(Comparator.comparing(Participant::valueSharePercent)','1000 financial participants',
     'value.precision()>38'],
 'backend/modules/material/src/main/java/com/sakhtyar/material/application/PriceAnomalyDetector.java':[
     'Candidate material price must be non-negative','Historical material prices must be non-null'],
 'backend/modules/analysis/src/main/java/com/sakhtyar/analysis/application/Phase05NumericSnapshot.java':[
     'SCHEMA_VERSION','envelopeHash','caseId','workspaceId','runId'],
 'backend/modules/quality/src/main/java/com/sakhtyar/quality/Phase05SyntheticFixtureManifest.java':[
     'E2E-(1[5-9]|20)','NO_LIVE_CLEANUP','BLOCKED','requireNoWrites'],
}
for rel,tokens in checks.items():
    f=ROOT/rel
    data=f.read_text(encoding='utf-8')
    missing=[s for s in tokens if s not in data]
    if missing: raise SystemExit(f'FAIL SOURCE_ONLY {rel}: missing={missing}')
golden=json.loads((ROOT/'tests/phase05/golden/synthetic_business_finance_oracle_v2.json').read_text(encoding='utf-8'))
assert golden['schemaVersion']==2 and golden['versionPin']['createdAt']=='2026-01-01T00:00:00Z'
assert golden['fixtures']['dbWritesAllowed'] is False and golden['fixtures']['liveCleanupAllowed'] is False
print(f'PASS Phase05 SOURCE_ONLY contracts: {len(checks)} Java files, version-pinned synthetic golden; runtime gates NOT_RUN')
