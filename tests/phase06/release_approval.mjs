#!/usr/bin/env node
/** Strict *authorization* gate. Existing release_gate.mjs is structural only. */
import fs from 'node:fs'
import path from 'node:path'
import { validate } from './release_gate.mjs'
import { verifyReleaseArtifacts } from './proof_verifier.mjs'

function safeInput(value, kind) {
  if (typeof value !== 'string' || !value || value.includes('..') || value.includes('\\') ||
      path.isAbsolute(value) || value.includes(':') || value.length > 240) throw new Error('Unsafe '+kind+' path')
  const root=path.resolve(process.cwd())
  const full=path.resolve(root,value)
  if(!full.startsWith(root+path.sep)) throw new Error('Path outside checkout')
  return full
}
function reportResult(out) {
  const dir=path.resolve(process.env.SAKHTYAR_PHASE06_REPORT_DIR || 'phase06-report')
  fs.mkdirSync(dir,{recursive:true})
  fs.writeFileSync(path.join(dir,'release-approval-summary.json'),JSON.stringify(out,null,2))
}
const args=process.argv.slice(2)
let status={accepted:false,structuralAccepted:false,artifactAccepted:false,verifiedArtifacts:0,
  reasons:['No independently verified business evidence'],mode:'STRICT_NO_DB_OPERATIONS'}
try {
  if(args.length!==3)throw new Error('Usage: node release_approval.mjs <report> <evidence-root> <attestation>')
  const report=JSON.parse(fs.readFileSync(safeInput(args[0],'report'),'utf8'))
  const manifest=JSON.parse(fs.readFileSync(safeInput(args[2],'attestation'),'utf8'))
  const structural=validate(report)
  const proof=verifyReleaseArtifacts(report,{
    root:safeInput(args[1],'evidence root'),attestation:manifest,
    secret:process.env.SAKHTYAR_PHASE06_EVIDENCE_HMAC_KEY,
    expectedSha:process.env.GITHUB_SHA
  })
  status={accepted:structural.accepted&&proof.verified,
    structuralAccepted:structural.accepted,artifactAccepted:proof.verified,
    verifiedArtifacts:proof.artifactCount,passed:structural.passed,total:24,
    reasons:[...structural.reasons.slice(0,24),...proof.reasons],mode:'STRICT_NO_DB_OPERATIONS'}
} catch(e) {status.reasons=[e.name === 'SyntaxError'?'Invalid JSON':e.message]}
try {reportResult(status)} catch(e) {process.stderr.write('Cannot save release summary: '+e.name+'\n')}
process.stdout.write('RELEASE_APPROVAL='+ (status.accepted?'ACCEPTED':'BLOCKED')+' VERIFIED_ARTIFACTS='+status.verifiedArtifacts+'\n')
for(const reason of status.reasons.slice(0,12))process.stdout.write('- '+reason+'\n')
if(!status.accepted)process.exitCode=2
