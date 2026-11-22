#!/usr/bin/env node
/** Source-only contract check. Never runs a business scenario or touches a database. */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'

const here = path.dirname(fileURLToPath(import.meta.url))
const catalogPath = process.argv[2]
  ? path.resolve(process.argv[2])
  : path.join(here, 'scenario-plans.json')
const plans = JSON.parse(fs.readFileSync(catalogPath, 'utf8'))
assert.ok(Array.isArray(plans), 'Scenario catalog must be an array')
assert.equal(plans.length, 24, 'Exactly 24 scenario plans are required')
const byId = new Map(plans.map(plan => [plan.id, plan]))
assert.equal(byId.size, 24, 'Scenario IDs must be unique')
const ids = Array.from({ length: 24 }, (_, i) => `E2E-${String(i + 1).padStart(2, '0')}`)
for (const id of ids) assert.ok(byId.has(id), `Missing ${id}`)
const statuses = new Set(['NOT_IMPLEMENTED_FULL_E2E', 'LIMITED_CORE_FIXTURE', 'FULL_BUSINESS_E2E'])
for (const plan of plans) {
  assert.ok(typeof plan.title === 'string' && plan.title.trim(), `${plan.id}: title missing`)
  assert.ok(typeof plan.description === 'string' && plan.description.trim(), `${plan.id}: description missing`)
  assert.ok(typeof plan.businessSteps === 'string' && plan.businessSteps.trim(), `${plan.id}: businessSteps missing`)
  assert.ok(statuses.has(plan.businessStatus), `${plan.id}: unknown businessStatus`)
  assert.ok(Array.isArray(plan.dependencies), `${plan.id}: dependencies missing`)
  assert.ok(Array.isArray(plan.preflightSteps) && plan.preflightSteps.length === 4, `${plan.id}: four preflight steps required`)
  assert.ok(Array.isArray(plan.requiredTables) && plan.requiredTables.length > 0, `${plan.id}: tables missing`)
  assert.ok(Array.isArray(plan.requiredRoutes) && plan.requiredRoutes.length > 0, `${plan.id}: routes missing`)
}
const seen = new Set()
const active = new Set()
function visit(id) {
  assert.ok(byId.has(id), `Unknown scenario dependency ${id}`)
  assert.ok(!active.has(id), `Cyclic dependency detected at ${id}`)
  if (seen.has(id)) return
  active.add(id)
  for (const dep of byId.get(id).dependencies) {
    assert.notEqual(dep, id, `Self dependency ${id}`)
    visit(dep)
  }
  active.delete(id)
  seen.add(id)
}
for (const id of ids) visit(id)
const counts = Object.fromEntries([...statuses].map(s => [s, plans.filter(p => p.businessStatus === s).length]))
assert.equal(Object.values(counts).reduce((a, b) => a + b, 0), 24)
console.log(JSON.stringify({kind:'SOURCE_CATALOG_CONTRACT',status:'PASSED',total:24,preflightChecks:96,
  coverage:counts,fullBusinessAcceptedByThisCheck:0,note:'Catalog metadata never proves a runtime E2E PASS'}, null, 2))
