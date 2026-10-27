#!/usr/bin/env node
/**
 * SakhtYar business E2E integration runner. Makes REAL HTTP calls to the running backend.
 * 24 scenario IDs are submitted, but unsupported business implementations remain BLOCKED.
 * Default exit code is NON-ZERO whenever any requested scenario is not PASSED.
 * Never prints credentials; requires explicit opt-in for any TEST-domain writes.
 * Node.js 20+, no npm dependencies.
 */
import fs from 'node:fs'
const BASE = (process.env.SAKHTYAR_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
const cookie = process.env.SAKHTYAR_COOKIE || ''
const bearer = process.env.SAKHTYAR_TOKEN || ''
const csrf = process.env.SAKHTYAR_CSRF_TOKEN || ''
const workspace = process.env.SAKHTYAR_TEST_WORKSPACE_ID || ''
const allowWrite = process.env.SAKHTYAR_ENABLE_TEST_WRITE === '1'
const all = Array.from({ length: 24 }, (_, i) => `E2E-${String(i + 1).padStart(2, '0')}`)
const selection = process.env.SAKHTYAR_E2E_IDS ? process.env.SAKHTYAR_E2E_IDS.split(',').map(s => s.trim()) : all
if (process.argv.includes('--help')) {
  process.stdout.write(`SakhtYar E2E integration runner\n
Required: SAKHTYAR_COOKIE (authenticated admin cookie) or SAKHTYAR_TOKEN; SAKHTYAR_TEST_WORKSPACE_ID;
          SAKHTYAR_ENABLE_TEST_WRITE=1 to permit guarded TEST fixture writes.
Optional: SAKHTYAR_BASE_URL, SAKHTYAR_CSRF_TOKEN, SAKHTYAR_E2E_IDS=E2E-14,...,
          SAKHTYAR_E2E_REPORT=filename, SAKHTYAR_ACCEPT_BLOCKED=1 (inventory mode only).
Default is STRICT: exit 2 if any scenario is BLOCKED/FAILED.\n`)
  process.exit(0)
}
if (!cookie && !bearer) throw Error('Set SAKHTYAR_COOKIE or SAKHTYAR_TOKEN; no credentials are bundled.')
if (!workspace || !/^[0-9a-fA-F-]{36}$/.test(workspace)) throw Error('Set SAKHTYAR_TEST_WORKSPACE_ID to an existing TEST workspace UUID')
if (!selection.length || selection.some(id => !all.includes(id)) || new Set(selection).size !== selection.length) throw Error('Invalid scenario selection')
if (!allowWrite) throw Error('TEST writes require explicit SAKHTYAR_ENABLE_TEST_WRITE=1 after RLS and backup verification')
const base = '/api/v1/admin/simulations/e2e'
async function request(path, opts = {}) {
  const headers = { Accept: 'application/json', ...(opts.body ? { 'Content-Type': 'application/json' } : {}),
    ...(cookie ? { Cookie: cookie } : {}), ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}),
    ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}) }
  const res = await fetch(`${BASE}${path}`, { ...opts, headers, signal: AbortSignal.timeout(15000) })
  const body = await res.text()
  if (!res.ok) throw Error(`HTTP ${res.status} ${path}: ${body.slice(0, 240)}`)
  return JSON.parse(body)
}
const coverage = await request(`${base}/coverage`)
if (coverage.totalBusinessE2eCount !== 24) throw Error('Backend scenario catalog is inconsistent')
const key = `cli_${crypto.randomUUID().replace(/-/g, '')}`
const created = await request(`${base}/runs`, {
  method: 'POST', body: JSON.stringify({ workspaceId: workspace, requestKey: key, scenarioIds: selection }),
})
process.stdout.write(`E2E run ${created.id} started for ${selection.length} scenarios\n`)
let result
for (let attempt = 0; attempt < 100; attempt++) {
  result = await request(`${base}/runs/${created.id}`)
  process.stdout.write(`\rBusiness E2E: ${result.progressPercent}% (${result.completedScenarios}/${result.scenario_count})`)
  if (['COMPLETED', 'INTERRUPTED'].includes(result.state)) break
  await new Promise(resolve => setTimeout(resolve, 500))
}
process.stdout.write('\n')
if (!result || !['COMPLETED', 'INTERRUPTED'].includes(result.state)) throw Error('E2E run did not finish; inspect backend logs')
const path = process.env.SAKHTYAR_E2E_REPORT || `sakhtyar-e2e-${created.id}.json`
fs.writeFileSync(path, JSON.stringify(result, null, 2))
const counts = Object.fromEntries(['PASSED','FAILED','BLOCKED'].map(state => [state, result.results.filter(row => row.state === state).length]))
for (const row of result.results) process.stdout.write(`${row.scenario_id}: ${row.state} — ${row.summary}\n`)
process.stdout.write(`Report: ${path}\nPASSED=${counts.PASSED}; FAILED=${counts.FAILED}; BLOCKED=${counts.BLOCKED}\n`)
if (counts.FAILED || (counts.BLOCKED && process.env.SAKHTYAR_ACCEPT_BLOCKED !== '1')) process.exitCode = 2
