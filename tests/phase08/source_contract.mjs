import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const read = rel => readFileSync(path.join(root, rel), 'utf8');
const quality='backend/modules/quality/src/main/java/com/sakhtyar/quality/';
const admission=read(quality+'Phase08SafetyAdmission.java');
const entry=read(quality+'SimulationBusinessE2EController.java');
const controller=read(quality+'Phase08SafetyReadinessController.java');
const fixture=read(quality+'Phase08SyntheticFixturePlan.java');
const plans=read(quality+'Phase08SecurityProofCatalog.java');
const frontend=read('frontend/src/pages/SimulationCenterPage.tsx');
assert.match(admission, /PHYSICAL_RESTRICTED_TEST_LOGIN_NOT_VERIFIED/);
assert.match(admission, /CROSS_WORKSPACE_REAL_INVISIBILITY_NOT_PROVEN/);
assert.match(admission, /DEVELOPMENT_SEED_MUST_BE_DISABLED/);
assert.match(admission, /RUN_SCOPED_CLEANUP_NOT_VERIFIED/);
assert.match(admission, /EMBEDDED_SOURCE_COMMIT_UNVERIFIED/);
assert.match(admission, /expected\.equals\(session\)/);
assert.match(admission, /"CORE_READ_PROBE_OBSERVED"/);
assert.match(admission, /Boolean\.TRUE\.equals/);
assert.match(controller, /@GetMapping\("\/safety-readiness"\)/);
assert.match(controller, /authorization\.requireActiveOwner\(workspaceId,actor\)/);
assert.match(frontend, /phase08-safety-readiness-/);
assert.match(frontend, /!phase08Safety\.data\?\.authorizedBusinessWrites/);
assert.match(fixture, /300,60,40,false/);
const ids=[...plans.matchAll(/new Requirement\("(E2E-\d{2})"/g)].map(x=>x[1]);
assert.deepEqual(ids,['E2E-01','E2E-02','E2E-03','E2E-04','E2E-05','E2E-06','E2E-14']);
const firstWrite=entry.indexOf('insert into simulation_e2e_run');
const gate=entry.indexOf('Phase08SafetyAdmission.Verdict safety=');
const owner=entry.indexOf('workspaceAuthorization.requireActiveOwner(req.workspaceId(), a);');
assert(owner>0 && gate>owner && firstWrite>gate, 'Phase08 admission must precede every Business E2E run write');
assert.match(entry.slice(gate,firstWrite), /if\(!safety\.authorized\(\)\)/);
assert.match(entry.slice(gate,firstWrite), /throw new ResponseStatusException\(HttpStatus\.CONFLICT/);
assert(!/(CREATE ROLE|ALTER ROLE|CREATE POLICY|TRUNCATE|DELETE FROM|DROP TABLE)/i.test(admission+controller+fixture),
  'New admission code must not include privileged SQL or deletion');
for(const test of ['Phase08SafetyAdmissionTest','Phase08SyntheticFixturePlanTest',
                   'Phase08SafetyReadinessControllerTest','Phase08BusinessAdmissionBeforeWriteTest']) {
  assert(existsSync(path.join(root,'backend/modules/quality/src/test/java/com/sakhtyar/quality',test+'.java')));
}
console.log('[PASS] Phase08 source contract: seven targets, pre-write gate, owner-bound read-only API, no privileged DDL');
