#!/usr/bin/env node
/** Conservative source-level invariants, NOT a replacement for runtime authorization or RLS proofs. */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const read = p => fs.readFileSync(path.join(root, p), 'utf8')
const q = 'backend/modules/quality/src/main/java/com/sakhtyar/quality/'
const controller = read(q+'SimulationBusinessE2EController.java')
const runStart = controller.indexOf('@PostMapping("/runs")')
const firstWrite = controller.indexOf('insert into simulation_e2e_run', runStart)
assert.ok(runStart >= 0 && firstWrite > runStart, 'Business E2E endpoint / first write missing')
for (const required of ['phase09RuntimePolicy.requireDevelopment()', 'workspaceAuthorization.requireActiveOwner(',
  'Phase08SafetyAdmission.assess(', 'if(!safety.authorized())']) {
  const pos=controller.indexOf(required,runStart)
  assert.ok(pos>runStart && pos<firstWrite, `Missing pre-write safety prerequisite: ${required}`)
}
assert.match(controller, /@(?:org\.springframework\.context\.annotation\.)?Profile\("!production"\)/)
const guard = read(q+'Phase08SafetyAdmission.java')
for(const flag of ['fullApplicationIsolationVerified','externalSideEffectsGloballyGuarded',
  'testDataCleanupScoped','verifiedEvidenceStoreReady','physicalLoginIsolationEvidence']) {
  assert.ok(guard.includes(flag),`Admission contract missing ${flag}`)
}
const probe = read(q+'Phase09RestrictedLoginInspector.java')
assert.match(probe, /connection\.setReadOnly\(true\)/)
assert.match(probe, /not rolbypassrls/)
assert.match(probe, /not rolsuper/)
assert.match(probe, /MISSING_INDEPENDENT_POSITIVE_CONTROL_AND_FULL_APPLICATION_SCOPE/)
assert.doesNotMatch(probe.replace(/\/\*[\s\S]*?\*\//g,'').replace(/\/\/.*$/gm,''),
  /\b(?:CREATE ROLE|ALTER ROLE|DROP ROLE|SET ROLE|TRUNCATE|DROP TABLE)\b/i)
const session = read(q+'Phase09PreflightSessionController.java')
assert.match(session, /@Profile\("development & !production"\)/)
assert.match(session, /READ_ONLY_PREFLIGHT_SESSION_NOT_BUSINESS_E2E/)
assert.doesNotMatch(session, /jdbc\.update\(|\.executeUpdate\(/)
const readiness = read(q+'Phase08SafetyReadinessController.java')
assert.match(readiness, /appJdbcIdentityNotAcceptedAsTestProof/)
const registry = read(q+'ScenarioExecutorRegistry.java')
assert.match(registry, /FULL_BUSINESS_E2E/)
assert.match(registry, /E2EArtifactIntegrity\.verified/)
console.log('[PASS] fail-closed pre-write policy, physical TEST probe, production exclusions, evidence gate (SOURCE ONLY)')
