#!/usr/bin/env node
/** Explicit read-only HTTP smoke. No POST/PUT/PATCH/DELETE; no credentials printed. */
import assert from 'node:assert/strict'
const base=(process.env.SAKHTYAR_BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/,'')
const token=process.env.SAKHTYAR_TOKEN || ''
const cookie=process.env.SAKHTYAR_COOKIE || ''
if(!token && !cookie){
  console.error('BLOCKED: supply a permitted admin token or cookie as an environment variable. Nothing sent.')
  process.exit(2)
}
const headers={Accept:'application/json',...(token?{Authorization:`Bearer ${token}`}:{Cookie:cookie})}
async function get(path){
  const response=await fetch(base+path,{method:'GET',headers,redirect:'error',signal:AbortSignal.timeout(10000)})
  let body=null
  if(response.ok){body=await response.json()}
  return {status:response.status,body}
}
try {
  const mode=await get('/api/v1/admin/simulations/runtime-mode')
  assert.equal(mode.status,200,'Owner-authorized runtime-mode should return 200')
  assert.ok(['development','production','legacy-local'].includes(mode.body?.profile),'Unexpected runtime profile')
  assert.equal(mode.body.businessWritesAuthorizedByProfile,false,'Profile must not authorize business writes')
  const coverage=await get('/api/v1/admin/simulations/e2e/coverage')
  if(mode.body.production){
    assert.equal(coverage.status,404,'Production must not expose business E2E coverage')
  }else if(mode.body.development){
    assert.equal(coverage.status,200,'Development E2E catalog should be readable')
    assert.equal(coverage.body.totalBusinessE2eCount,24)
    assert.equal(coverage.body.completeBusinessE2eCount,0,
      'Do not increase this count without independent audited runtime acceptance and updated checks')
  }else{
    console.log('Legacy profile observed: no write safety is inferred; coverage status '+coverage.status)
  }
  console.log(JSON.stringify({kind:'READ_ONLY_HTTP_SMOKE',state:'PASSED',profile:mode.body.profile,
    coverageHttpStatus:coverage.status,businessWritesAuthorizedByProfile:false,
    scope:'READ_ONLY_NO_BUSINESS_EXECUTION'}))
}catch(error){
  console.error('BLOCKED/FAILED: '+error.message)
  process.exitCode=1
}
