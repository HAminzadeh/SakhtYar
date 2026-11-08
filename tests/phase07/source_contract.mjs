#!/usr/bin/env node
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..')
const read=p=>fs.readFileSync(path.join(root,p),'utf8')
const base='backend/modules/quality/src/main/java/com/sakhtyar/quality/'
const controller=read(base+'Phase07DiagnosticCenterController.java')
const page=read('frontend/src/pages/SimulationCenterPage.tsx')
const spec=read('backend/modules/quality/src/test/java/com/sakhtyar/quality/Phase07DiagnosticExecutorsTest.java')
for(let i=1;i<=24;i++){
  const n=String(i).padStart(2,'0')
  const s=read(base+`Phase07E2E${n}Executor.java`)
  assert.match(s,new RegExp(`super\\("E2E-${n}"`))
  assert.match(s,/extends Phase07BaseDiagnosticExecutor/)
  assert.match(spec,new RegExp(`new Phase07E2E${n}Executor\\(`))
}
assert.match(controller,/requireActiveOwner/)
assert.match(controller,/"businessScenarioPassedCount",0/)
assert.match(controller,/"databaseCloneCreated",false/)
assert.match(page,/e2e\/diagnostics\/runs/)
assert.match(page,/تشخیص مستقل ۲۴ سناریو/)
console.log('PASS: 24 independent Phase07 diagnostic executor classes, guarded API, UI and honest business state')
