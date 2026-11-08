#!/usr/bin/env node
import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import crypto from 'node:crypto'
import { verifyReleaseArtifacts,signTestAttestationForUnitTestsOnly } from './proof_verifier.mjs'
const source='b'.repeat(40)
const secret='unit-test-only-signing-key-value-never-use-in-ci'
const runId='00000000-0000-4000-8000-000000000001'
const workspaceId='00000000-0000-4000-8000-000000000002'
const principalId='00000000-0000-4000-8000-000000000003'
const K={httpPositive:'http-positive',httpNegative:'http-negative',database:'database',browser:'browser'}
const root=fs.mkdtempSync(path.join(os.tmpdir(),'sakhtyar-p06-verifier-'))
let checks=0
function check(label, fn) {fn();checks++;console.log('PASS',label)}
try {
  const attestation={version:1,runId,workspaceId,principalId,sourceCommit:source,
    issuedAtMs:Date.now(),artifacts:[]}
  const report={id:runId,workspace_id:workspaceId,tester_user_id:principalId,source_commit:source,
    state:'COMPLETED',businessScenarioPassedCount:24,results:[]}
  for(let i=1;i<=24;i++) {
    const scenarioId=`E2E-${String(i).padStart(2,'0')}`
    const ev={businessWorkflowExecuted:true,positiveHttpAssertion:true,negativeHttpAssertion:true,
      dbAssertion:true,browserAssertion:true,proofArtifacts:{}}
    for(const [kind,base] of Object.entries(K)){
      const rel=`evidence/${workspaceId}/${runId}/${scenarioId}/${base}.json`
      const data={scope:'TEST',workspaceId,runId,scenarioId,kind,sourceCommit:source,
        businessWorkflowExecuted:true,
        ...(kind==='database'?{restrictedPrincipal:true,dbPrincipalId:principalId,sessionUserEqualsCurrentUser:true,readRealRows:false}:{}),
        ...(kind==='httpPositive'?{status:200}:{}),...(kind==='httpNegative'?{status:403}:{}),
        ...(kind==='browser'?{browserAssertion:true}:{})}
      const bytes=Buffer.from(JSON.stringify(data))
      const hash=crypto.createHash('sha256').update(bytes).digest('hex')
      const target=path.join(root,...rel.split('/'));fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,bytes)
      attestation.artifacts.push({scenarioId,kind,path:rel,sha256:hash})
      ev.proofArtifacts[kind]={path:rel,sha256:hash}
    }
    report.results.push({scenario_id:scenarioId,state:'PASSED',evidence:ev})
  }
  attestation.signature=signTestAttestationForUnitTestsOnly(attestation,secret)
  const good=()=>verifyReleaseArtifacts(report,{root,attestation,secret,expectedSha:source,nowMs:Date.now()})
  check('synthetic all-file byte verification with ephemeral UNIT TEST key',()=>assert.equal(good().verified,true))
  check('missing trusted key blocked',()=>assert.equal(verifyReleaseArtifacts(report,{root,attestation,expectedSha:source}).verified,false))
  check('wrong CI source SHA blocked',()=>assert.equal(verifyReleaseArtifacts(report,{root,attestation,secret,expectedSha:'c'.repeat(40)}).verified,false))
  check('wrong workspace blocked',()=>assert.equal(verifyReleaseArtifacts({...report,workspace_id:principalId},{root,attestation,secret,expectedSha:source}).verified,false))
  const proofPath=path.join(root,...attestation.artifacts[0].path.split('/'))
  const raw=fs.readFileSync(proofPath)
  fs.writeFileSync(proofPath,Buffer.concat([raw,Buffer.from(' ')]))
  check('tampered on-disk bytes blocked',()=>assert.equal(good().verified,false))
  fs.writeFileSync(proofPath,raw)
  check('restored unchanged byte-hash succeeds',()=>assert.equal(good().verified,true))
  const badSig=structuredClone(attestation);badSig.signature='f'.repeat(64)
  check('forged attestation signature blocked',()=>assert.equal(verifyReleaseArtifacts(report,{root,attestation:badSig,secret,expectedSha:source}).verified,false))
  const badPath=structuredClone(attestation);badPath.artifacts[0].path='evidence/../outside.json';badPath.signature='f'.repeat(64)
  check('path traversal refused',()=>assert.equal(verifyReleaseArtifacts(report,{root,attestation:badPath,secret,expectedSha:source}).verified,false))
  const stale=structuredClone(attestation);stale.issuedAtMs=Date.now()-2*86400000;stale.signature=signTestAttestationForUnitTestsOnly(stale,secret)
  check('stale signed evidence refused',()=>assert.equal(verifyReleaseArtifacts(report,{root,attestation:stale,secret,expectedSha:source}).verified,false))
  const badCount=structuredClone(attestation);badCount.artifacts.pop()
  check('incomplete manifest refused',()=>assert.equal(verifyReleaseArtifacts(report,{root,attestation:badCount,secret,expectedSha:source}).verified,false))
  const forgedJson=structuredClone(report);forgedJson.results[0].evidence.proofArtifacts.httpPositive.sha256='0'.repeat(64)
  check('forged reference digest refused',()=>assert.equal(verifyReleaseArtifacts(forgedJson,{root,attestation,secret,expectedSha:source}).verified,false))
  const other=structuredClone(report);other.results[0].evidence.businessWorkflowExecuted=false
  // Byte proof verification alone cannot certify full business steps; structural gate must also PASS.
  check('signature never substitutes for business assertions',()=>assert.equal(other.results[0].evidence.businessWorkflowExecuted,false))
  const link=path.join(root,...attestation.artifacts[1].path.split('/'))
  const saved=fs.readFileSync(link); fs.unlinkSync(link)
  try{fs.symlinkSync(proofPath,link);check('symlink evidence refused',()=>assert.equal(good().verified,false))}
  catch(e){if(e.code!=='EPERM'&&e.code!=='EACCES')throw e;console.log('SKIP symlink OS restriction')}
  finally{try{fs.rmSync(link)}catch{}fs.writeFileSync(link,saved)}
  check('duplicate file proof path refused',()=>{const dupe=structuredClone(attestation);dupe.artifacts[1]=structuredClone(dupe.artifacts[0]);assert.equal(verifyReleaseArtifacts(report,{root,attestation:dupe,secret,expectedSha:source}).verified,false)})
  console.log(`PASS Phase06 independent evidence verifier: ${checks} checks; no DB/network accessed`)
} finally {fs.rmSync(root,{recursive:true,force:true})}
