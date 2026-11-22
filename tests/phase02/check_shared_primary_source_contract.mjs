#!/usr/bin/env node
/** Offline contracts, not DB isolation evidence or a business E2E execution. */
import fs from 'node:fs';
import assert from 'node:assert/strict';
import path from 'node:path';

const plans=JSON.parse(fs.readFileSync('tests/e2e/scenario-plans.json','utf8'));
assert.equal(plans.length,24);
const ids=new Set(plans.map(p=>p.id));
assert.equal(ids.size,24);
assert.equal(plans.filter(p=>p.businessStatus==='NOT_IMPLEMENTED_FULL_E2E').length,23);
assert.equal(plans.filter(p=>p.businessStatus==='LIMITED_CORE_FIXTURE').length,1);
const byId=new Map(plans.map(p=>[p.id,p]));
const done=new Set(),active=new Set();
function visit(id) {
  assert.ok(byId.has(id),`Unknown dependency ${id}`);
  assert.ok(!active.has(id),`Cycle ${id}`);
  if(done.has(id))return;
  active.add(id);
  for(const dep of byId.get(id).dependencies)visit(dep);
  active.delete(id);done.add(id);
}
for(const p of plans)visit(p.id);
assert.equal(done.size,24);
const q='backend/modules/quality/src/main/java/com/sakhtyar/quality/';
const core=fs.readFileSync(q+'SimulationCoreFixtureService.java','utf8');
assert.match(core,/APP_T10_FIXTURE_ENABLE_ROLE_BOOTSTRAP:false/);
assert.match(core,/bootstrapAllowed/);
assert.match(core,/provider\.isDedicated\(\)/);
const factories=fs.readFileSync(q+'Phase02FixtureFactories.java','utf8');
assert.match(factories,/DeniedUntilIsolatedFactory/);
const sql=fs.readFileSync('tests/phase02/05_shared_primary_gate_readonly.sql','utf8');
const statements=sql.split('\n').filter(x=>!x.trim().startsWith('--')).join('\n');
assert.match(statements,/BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY/);
assert.match(statements,/ROLLBACK\s*;/);
assert.doesNotMatch(statements,/\b(?:CREATE|ALTER|DROP|TRUNCATE|INSERT|UPDATE|DELETE|GRANT|REVOKE|CALL)\s+/i);
const canary=fs.readFileSync(q+'Phase02ScopedFakeStorage.java','utf8');
assert.match(canary,/strictly in-memory|strictly in-memory/i);
assert.ok(!/DriverManager|Socket|HttpClient|MinioClient|RedisTemplate/.test(canary));
console.log('PASS: 24-plan DAG, 23+1 coverage, bootstrap opt-in, blocked factories, read-only primary audit, socket-free canary');
