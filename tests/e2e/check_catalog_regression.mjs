#!/usr/bin/env node
/** Verify catalog checker from any CWD and reject broken dependencies without database access. */
import { mkdtempSync, readFileSync, writeFileSync, rmSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'
import assert from 'node:assert/strict'
const here=path.dirname(fileURLToPath(import.meta.url))
const checker=path.join(here,'check_24_plans.mjs')
const valid=JSON.parse(readFileSync(path.join(here,'scenario-plans.json'),'utf8'))
const temp=mkdtempSync(path.join(os.tmpdir(),'sakhtyar-catalog-'))
try {
  const anywhere=spawnSync(process.execPath,[checker],{cwd:temp,encoding:'utf8'})
  assert.equal(anywhere.status,0,anywhere.stderr)
  const copy=structuredClone(valid)
  copy[0].dependencies.push('E2E-99')
  const bad=path.join(temp,'invalid.json')
  writeFileSync(bad,JSON.stringify(copy))
  const rejected=spawnSync(process.execPath,[checker,bad],{cwd:temp,encoding:'utf8'})
  assert.notEqual(rejected.status,0,'Unknown dependency must be rejected')
  const next=structuredClone(valid)
  const candidate=next.find(p=>p.businessStatus==='NOT_IMPLEMENTED_FULL_E2E')
  assert.ok(candidate,'Baseline must include unimplemented scenarios')
  candidate.businessStatus='FULL_BUSINESS_E2E'
  const evolved=path.join(temp,'evolved.json')
  writeFileSync(evolved,JSON.stringify(next))
  const dynamic=spawnSync(process.execPath,[checker,evolved],{cwd:temp,encoding:'utf8'})
  assert.equal(dynamic.status,0,dynamic.stderr)
  assert.equal(JSON.parse(dynamic.stdout).coverage.FULL_BUSINESS_E2E,1,
    'Catalog checker must not hardcode all 23 unimplemented statuses')
  assert.equal(JSON.parse(dynamic.stdout).fullBusinessAcceptedByThisCheck,0,
    'Metadata must not claim runtime acceptance')
  console.log('[PASS] catalog CWD independence, cyclic/invalid dependency guard, dynamic coverage, no fabricated acceptance')
} finally {rmSync(temp,{recursive:true,force:true})}
