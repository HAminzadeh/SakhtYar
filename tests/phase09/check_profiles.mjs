import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
const root=path.resolve(import.meta.dirname,'../..')
const read=p=>fs.readFileSync(path.join(root,p),'utf8')
const app='backend/app/src/main/'
for(const profile of ['development','production']) {
  const settings=read(app+`resources/application-${profile}.yml`)
  assert.match(settings,/dev-seed:\s*\n\s*enabled:\s*false/)
  assert.match(settings,/flyway:\s*\n\s*enabled:\s*false/)
  assert.match(settings,/ddl-auto:\s*validate/)
  assert.match(read(`.run/SakhtYar Backend - ${profile[0].toUpperCase()+profile.slice(1)}.run.xml`),
    new RegExp(`--spring.profiles.active=${profile}`))
}
const seed=read(app+'java/com/sakhtyar/bootstrap/DevSeedRunner.java')
assert.match(seed,/@Profile\("!development & !production"\)/)
const startup=read(app+'java/com/sakhtyar/bootstrap/Phase09ProfileSafetyInitializer.java')
assert.match(startup,/spring.flyway.enabled/)
assert.match(startup,/app.dev-seed.enabled/)
const boot=read(app+'java/com/sakhtyar/SakhtYarApplication.java')
assert.match(boot,/new Phase09ProfileSafetyInitializer\(\)/)
assert.ok(boot.indexOf('new Phase09ProfileSafetyInitializer()') < boot.indexOf('new LocalInfrastructureInitializer()'))
const quality='backend/modules/quality/src/main/java/com/sakhtyar/quality/'
for(const name of ['SimulationController','SimulationBusinessE2EController',
 'Phase07DiagnosticCenterController','Phase08SafetyReadinessController','Phase02FixtureCatalogController']) {
 const text=read(quality+name+'.java')
 assert.match(text,/@(?:Profile|org\.springframework\.context\.annotation\.Profile)\("!production"\)/)
}
const business=read(quality+'SimulationBusinessE2EController.java')
assert.match(business,/phase09RuntimePolicy.requireDevelopment\(\)/)
if (business.includes('insert into simulation_e2e_run'))
  assert.ok(business.indexOf('phase09RuntimePolicy.requireDevelopment()') < business.indexOf('insert into simulation_e2e_run'))
const physical=read(quality+'Phase09RestrictedLoginInspector.java')
assert.match(physical,/session_user/)
assert.match(physical,/rolbypassrls/)
const executable=physical.replace(/\/\*[\s\S]*?\*\//g,'').replace(/\/\/.*$/gm,'')
assert.doesNotMatch(executable,/\b(?:CREATE ROLE|ALTER ROLE|DROP ROLE|SET ROLE|CREATE DATABASE)\b/i)
const safety=read(quality+'Phase08SafetyReadinessController.java')
assert.match(safety,/phase09RestrictedProbe.inspect\(workspaceId\)/)
assert.match(safety,/appJdbcIdentityNotAcceptedAsTestProof/)
const catalog=read(quality+'Phase09SafetyBlockerCatalog.java')
assert.equal([...catalog.matchAll(/new Requirement\("[A-Z_]+"/g)].length,11)
const preflight=read(quality+'Phase09PreflightSessionController.java')
assert.match(preflight,/@Profile\("development & !production"\)/)
assert.doesNotMatch(preflight,/(?:\.update\(|\.executeUpdate\(|\bdelete from\b)/i)
const frontend=read('frontend/src/pages/SimulationCenterPage.tsx')
assert.match(frontend,/phase09-preflight-/)
assert.match(frontend,/blockerDetails/)
assert.match(frontend,/sim-runtime-mode/)
console.log('[PASS] Phase09 source contract: 2 profiles, startup-guard, 5 production exclusions, 11 blockers, isolated probe, UI preflight')
