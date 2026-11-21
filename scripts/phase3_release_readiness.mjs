#!/usr/bin/env node
/** Offline source inventory only: no business claims, credentials, HTTP or DB access. */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
const root=process.cwd()
const source=p=>fs.readFileSync(path.join(root,p),'utf8')
const plans=source('backend/modules/quality/src/main/java/com/sakhtyar/quality/SimulationScenarioPlans.java')
const declared=[...plans.matchAll(/new Plan\("(E2E-\d{2})"/g)].map(m=>m[1])
const expected=Array.from({length:24},(_,i)=>`E2E-${String(i+1).padStart(2,'0')}`)
if (declared.length!==24 || new Set(declared).size!==24 || expected.some(id=>!declared.includes(id))) {
  console.error('BLOCKED: 24-scenario catalog missing, duplicated or incomplete');process.exit(2)
}
const exists=p=>fs.existsSync(path.join(root,p))
const main='backend/modules/quality/src/main/java/com/sakhtyar/quality/'
const phase2Ids=new Set([7,8,9,10,11,12,13,15,16,17,18,19,20])
const rows=expected.map(id=>{
  const number=Number(id.slice(4))
  const type=number===14?'LIMITED_CORE_FIXTURE':phase2Ids.has(number)?'OFFLINE_CONTRACT_ONLY':number>=21?'OFFLINE_CONTRACT_ONLY':'UNIMPLEMENTED_BUSINESS_WORKFLOW'
  const file=number===14?main+'E2E14FixtureExecutor.java':phase2Ids.has(number)?main+`phase2/Phase2${id.replace('-','')}Executor.java`:number>=21?main+`phase3/Phase3${id.replace('-','')}Executor.java`:null
  const registeredSource=Boolean(file&&exists(file))
  return {scenarioId:id,sourceExecutorPresent:registeredSource,executionType:registeredSource?type:'UNIMPLEMENTED_BUSINESS_WORKFLOW',
    diagnostics:'NOT_RUN',business:number===5?'BLOCKED':'NOT_VERIFIED',businessAssertionsPassed:0,
    positiveHttp:'NOT_RUN',negativeHttp:'NOT_RUN',restrictedDbRole:'NOT_VERIFIED',workspaceScope:'NOT_VERIFIED',
    browser:'NOT_RUN',evidenceArtifacts:0,sourceCommit:'UNVERIFIED',
    blocker:number===5?'RESTORE_PROOF_BLOCKED_BY_NO_CLONE_POLICY':'FULL_BUSINESS_EVIDENCE_NOT_VERIFIED'}
})
const report={schemaVersion:2,reportKind:'OFFLINE_SOURCE_INVENTORY_NOT_RUNTIME_ACCEPTANCE',
  reviewedBase:'cc77cd56264ca28e77ba7dc35d733e1aac2ab841',sourceCommit:'UNVERIFIED',
  generatedAt:new Date().toISOString(),counts:{total:24,businessPassed:0,businessBlocked:1,businessNotVerified:23,
  phase3OfflineAdaptersPresent:rows.filter(r=>Number(r.scenarioId.slice(4))>=21&&r.sourceExecutorPresent).length,
  totalArtifactsVerified:0,artifactsRequiredForCompleteAcceptance:96},
  releaseGate:'BLOCKED',reason:'No independently verified signed TEST artifacts, authenticated HTTP, browser, physical restricted DB role or restore proof',
  scenarios:rows}
const dest=path.resolve(process.argv[2]||path.join(root,'.final-e2e-reports','phase3-offline-readiness.json'))
fs.mkdirSync(path.dirname(dest),{recursive:true})
fs.writeFileSync(dest,JSON.stringify(report,null,2)+'\n',{flag:'w',mode:0o600})
console.log(`OFFLINE INVENTORY: ${rows.length} plans; 0 verified Business E2E; release BLOCKED; ${dest}`)
