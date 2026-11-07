#!/usr/bin/env node
import assert from 'node:assert/strict'
import {validate} from './release_gate.mjs'
const sample={state:'COMPLETED',source_commit:'unknown',businessScenarioPassedCount:0,results:Array.from({length:24},(_,i)=>({scenario_id:`E2E-${String(i+1).padStart(2,'0')}`,state:'BLOCKED'}))}
const blocked=validate(sample)
assert.equal(blocked.accepted,false)
assert.equal(blocked.passed,0)
assert.ok(blocked.reasons.includes('Missing resolved source SHA'))
const falsePositive=structuredClone(sample)
falsePositive.source_commit='a'.repeat(40)
falsePositive.businessScenarioPassedCount=24
falsePositive.results.forEach(x=>x.state='PASSED')
assert.equal(validate(falsePositive).accepted,false,'Do not accept missing HTTP/DB/browser proof')
const spoofed=structuredClone(falsePositive)
spoofed.results.forEach(x=>x.evidence={assertions:{businessWorkflowExecuted:true,positiveHttpAssertion:true,negativeHttpAssertion:true,dbAssertion:true,browserAssertion:true}})
assert.equal(validate(spoofed).accepted,false,'Boolean assertions alone are not evidence')
const duplicated=structuredClone(spoofed)
duplicated.results[1].scenario_id='E2E-01'
assert.equal(validate(duplicated).accepted,false)
console.log('PASS: Phase06 strict release gate rejects blocked, unknown SHA, missing artifacts, duplicate IDs')
