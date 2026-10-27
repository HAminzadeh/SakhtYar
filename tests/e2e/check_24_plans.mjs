#!/usr/bin/env node
/** Offline source contract verification; 24 registered scenario plans and dependency graph. */
import fs from 'node:fs'
import { strict as assert } from 'node:assert'
const [,,path='scenario-plans.json'] = process.argv
const all=JSON.parse(fs.readFileSync(path,'utf8'))
assert.equal(all.length,24)
const byId=new Map(all.map(x=>[x.id,x]))
assert.equal(byId.size,24)
for(const x of all){
 assert.ok(x.title && x.description && x.businessSteps)
 assert.equal(x.preflightSteps.length,4)
 assert.ok(x.requiredTables.length>=1 && x.requiredRoutes.length>=1)
 const seen=new Set(); const chain=new Set()
 function visit(code){
  assert.ok(byId.has(code),`unknown dependency ${code}`)
  assert.ok(!chain.has(code),`cyclic dependency ${code}`)
  if(seen.has(code))return
  chain.add(code);for(const dep of byId.get(code).dependencies)visit(dep)
  chain.delete(code);seen.add(code)
 }
 visit(x.id)
}
assert.equal(all.filter(x=>x.businessStatus==='NOT_IMPLEMENTED_FULL_E2E').length,23)
assert.equal(all.filter(x=>x.businessStatus==='LIMITED_CORE_FIXTURE').length,1)
console.log('PASS: 24 plans, description, 96 prereq checks, dependencies, restricted business coverage')
