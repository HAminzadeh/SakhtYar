import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'sakhtyar-p3-'))
try {
  const report=path.join(dir,'report.json')
  const job=spawnSync(process.execPath,['scripts/phase3_release_readiness.mjs',report],{encoding:'utf8'})
  assert.equal(job.status,0,job.stderr)
  const d=JSON.parse(fs.readFileSync(report,'utf8'))
  assert.equal(d.scenarios.length,24)
  assert.equal(new Set(d.scenarios.map(x=>x.scenarioId)).size,24)
  assert.equal(d.counts.businessPassed,0)
  assert.equal(d.releaseGate,'BLOCKED')
  assert.equal(d.scenarios.find(x=>x.scenarioId==='E2E-05').business,'BLOCKED')
  assert.equal(d.scenarios.find(x=>x.scenarioId==='E2E-21').executionType,'OFFLINE_CONTRACT_ONLY')
  const hasFixture=fs.existsSync('backend/modules/quality/src/main/java/com/sakhtyar/quality/E2E14FixtureExecutor.java')
  assert.equal(d.scenarios.find(x=>x.scenarioId==='E2E-14').executionType,hasFixture?'LIMITED_CORE_FIXTURE':'UNIMPLEMENTED_BUSINESS_WORKFLOW')
  assert.equal(d.sourceCommit,'UNVERIFIED')
  console.log('PASS phase3 readonly report: 24 distinct rows; 0 business PASS; E2E-05 explicitly blocked')
} finally { fs.rmSync(dir,{recursive:true,force:true}) }
