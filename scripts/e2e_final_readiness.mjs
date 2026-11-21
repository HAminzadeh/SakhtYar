#!/usr/bin/env node
/** Single offline readiness report, no production mutation and no fabricated E2E PASSES. */
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const tasks = [
  ['Phase 1: profile and frontend source checks','tests/phase09/check_profiles.mjs'],
  ['Phase 2: safety admissions and restricted probe contracts','tests/e2e/check_safety_contracts.mjs'],
  ['Phase 3: 24 plans and dependency integrity','tests/e2e/check_24_plans.mjs'],
  ['Phase 4: catalog regression and honest coverage','tests/e2e/check_catalog_regression.mjs'],
]
const results = tasks.map(([name, file]) => {
  const run=spawnSync(process.execPath,[path.join(root,file)],{cwd:root,encoding:'utf8',timeout:30000})
  return {name,script:file,state:run.status===0?'PASSED':'FAILED',exitCode:run.status,
    message:(run.status===0?run.stdout:run.stderr||run.stdout||run.error?.message||'unknown').slice(-6000)}
})
const report={schemaVersion:1,generatedAt:new Date().toISOString(),hostname:os.hostname(),
  scope:'SOURCE_ONLY_NO_DATABASE_WRITES_NO_BROWSER_EXECUTION',
  checks:results,sourceChecksPassed:results.every(x=>x.state==='PASSED'),
  businessE2EAcceptance:'NOT_VERIFIED',completeBusinessScenariosAccepted:0,
  remainingGates:['restricted PostgreSQL login and independent positive control',
    'all-table RLS/FK provenance', 'global fake adapters for outbound effects',
    'workspace-and-run-scoped cleanup','durable signed evidence store',
    'complete 24 business executors with positive and negative HTTP, DB, browser assertions']}
const outFlag=process.argv.indexOf('--report')
if(outFlag!==-1){
  const raw=process.argv[outFlag+1]
  if(!raw){console.error('Missing value for --report');process.exit(2)}
  const dest=path.resolve(raw)
  fs.mkdirSync(path.dirname(dest),{recursive:true})
  fs.writeFileSync(dest,JSON.stringify(report,null,2)+'\n',{flag:'w'})
  console.log('Report: '+dest)
}
for(const x of results)console.log(`[${x.state}] ${x.name}`)
console.log(`Source checks: ${report.sourceChecksPassed?'PASSED':'FAILED'}; Business E2E: NOT VERIFIED`)
if(!report.sourceChecksPassed)process.exitCode=1
