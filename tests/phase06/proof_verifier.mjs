/**
 * Phase06 independent on-disk TEST artifact verifier.
 * Only a CI-owned key can authorize the release attestation. This is NOT a
 * database isolation proof or a business E2E executor by itself.
 */
import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'

const SHA = /^[a-f0-9]{40}$/i
const DIGEST = /^[a-f0-9]{64}$/i
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const TYPES = ['httpPositive', 'httpNegative', 'database', 'browser']
const KINDS = {httpPositive:'http-positive', httpNegative:'http-negative', database:'database', browser:'browser'}
const idFor = i => `E2E-${String(i).padStart(2,'0')}`
const fail = why => { throw new Error(why) }
const parseEvidence = input => {
  if (input?.type === 'jsonb') {
    if (typeof input.value !== 'string' || input.value.length > 500_000) fail('Invalid JSONB evidence')
    return JSON.parse(input.value)
  }
  return input
}
const validRel = rel => typeof rel === 'string' && rel.length < 260 &&
  /^evidence\/[a-zA-Z0-9_/-]+\.json$/.test(rel) &&
  !rel.split('/').some(x => !x || x === '.' || x === '..') &&
  !rel.includes('\\') && !rel.includes(':')

function readSafe(root, rel) {
  if (!validRel(rel)) fail('Unsafe evidence path')
  const absolute = path.resolve(root, ...rel.split('/'))
  const canonicalRoot = path.resolve(root)
  if (!absolute.startsWith(canonicalRoot + path.sep)) fail('Evidence escaped root')
  let current = canonicalRoot
  for (const piece of path.relative(canonicalRoot, absolute).split(path.sep)) {
    current = path.join(current, piece)
    const stat = fs.lstatSync(current)
    if (stat.isSymbolicLink()) fail('Symlink evidence component')
  }
  const stat = fs.statSync(absolute)
  if (!stat.isFile() || stat.size > 65536) fail('Evidence must be a small regular file')
  return fs.readFileSync(absolute)
}

function normalizedAttestation(manifest) {
  if (!manifest || manifest.version !== 1 || !UUID.test(manifest.runId || '') ||
      !UUID.test(manifest.workspaceId || '') || !UUID.test(manifest.principalId || '') ||
      !SHA.test(manifest.sourceCommit || '') || !Number.isInteger(manifest.issuedAtMs) ||
      !Array.isArray(manifest.artifacts) || manifest.artifacts.length !== 96) fail('Malformed trusted attestation')
  const files = manifest.artifacts.map(x => {
    if (!x || !/^E2E-(0[1-9]|1[0-9]|2[0-4])$/.test(x.scenarioId || '') ||
        !TYPES.includes(x.kind) || !validRel(x.path) || !DIGEST.test(x.sha256 || '')) fail('Malformed artifact entry')
    const expected = `evidence/${manifest.workspaceId}/${manifest.runId}/${x.scenarioId}/${KINDS[x.kind]}.json`
    if (x.path !== expected) fail('Evidence path does not match TEST workspace/run ownership')
    return {scenarioId:x.scenarioId,kind:x.kind,path:x.path,sha256:x.sha256.toLowerCase()}
  }).sort((a,b) => a.path.localeCompare(b.path, 'en'))
  if (new Set(files.map(x=>x.path)).size !== 96) fail('Duplicated evidence path')
  return {version:1,runId:manifest.runId,workspaceId:manifest.workspaceId,
    principalId:manifest.principalId,sourceCommit:manifest.sourceCommit.toLowerCase(),
    issuedAtMs:manifest.issuedAtMs,artifacts:files}
}

/** A signature is computed only by an explicitly configured, trusted CI evidence signer. */
export function signTestAttestationForUnitTestsOnly(manifest, key) {
  const normalized = normalizedAttestation(manifest)
  return crypto.createHmac('sha256', key).update(JSON.stringify(normalized)).digest('hex')
}

function safeProof(data, entry, attest, kind) {
  if (!data || Array.isArray(data) || typeof data !== 'object' ||
      data.scope !== 'TEST' || data.workspaceId !== attest.workspaceId ||
      data.runId !== attest.runId || data.scenarioId !== entry.scenarioId ||
      data.kind !== kind || data.sourceCommit !== attest.sourceCommit ||
      data.businessWorkflowExecuted !== true) fail('Evidence payload identity or TEST scope invalid')
  if (kind === 'database' && (data.restrictedPrincipal !== true || data.dbPrincipalId !== attest.principalId ||
      data.sessionUserEqualsCurrentUser !== true || data.readRealRows !== false))
    fail('Restricted TEST database principal not independently evidenced')
  if (kind === 'httpPositive' && !(data.status >= 200 && data.status < 300)) fail('Positive HTTP assertion missing')
  if (kind === 'httpNegative' && ![401,403,404].includes(data.status)) fail('Negative HTTP assertion missing')
  if (kind === 'browser' && data.browserAssertion !== true) fail('Browser assertion missing')
}

export function verifyReleaseArtifacts(report, {root, attestation, secret, expectedSha, nowMs=Date.now()} = {}) {
  const reasons=[]
  try {
    if (typeof secret !== 'string' || !/^[-A-Za-z0-9_]{40,256}$/.test(secret)) fail('Missing trusted CI signing secret')
    if (!SHA.test(expectedSha || '')) fail('Missing independently resolved CI source SHA')
    if (typeof root !== 'string' || !fs.existsSync(root) || fs.lstatSync(root).isSymbolicLink()) fail('Missing trusted artifact root')
    const canonical = normalizedAttestation(attestation)
    if (canonical.sourceCommit !== expectedSha.toLowerCase() ||
        report?.source_commit?.toLowerCase() !== canonical.sourceCommit ||
        report?.id !== canonical.runId || report?.workspace_id !== canonical.workspaceId ||
        report?.tester_user_id !== canonical.principalId) fail('CI source, run, workspace or principal mismatch')
    if (canonical.issuedAtMs > nowMs + 300000 || canonical.issuedAtMs < nowMs - 86400000) fail('Stale or future-dated attestation')
    const signed = signTestAttestationForUnitTestsOnly(canonical, secret)
    const provided = attestation?.signature
    if (typeof provided !== 'string' || !DIGEST.test(provided) ||
        !crypto.timingSafeEqual(Buffer.from(provided,'hex'),Buffer.from(signed,'hex'))) fail('CI attestation signature invalid')
    const resultMap = new Map(report?.results?.map(r=>[r.scenario_id,r]) || [])
    if (resultMap.size !== 24) fail('Incomplete business scenario rows')
    for (const file of canonical.artifacts) {
      const row = resultMap.get(file.scenarioId)
      const proof = parseEvidence(row?.evidence)?.proofArtifacts?.[file.kind]
      if (!proof || proof.path !== file.path || proof.sha256?.toLowerCase() !== file.sha256) fail('Manifest/report evidence reference mismatch')
      const bytes = readSafe(root, file.path)
      if (crypto.createHash('sha256').update(bytes).digest('hex') !== file.sha256) fail('On-disk SHA-256 mismatched')
      safeProof(JSON.parse(bytes.toString('utf8')),file,canonical,file.kind)
    }
  } catch (e) { reasons.push(e instanceof Error ? e.message : 'Unknown evidence verification failure') }
  return {verified:reasons.length===0,reasons,artifactCount:reasons.length===0?96:0,
    verificationMode:'CI_KEYED_ON_DISK_TEST_EVIDENCE'}
}
