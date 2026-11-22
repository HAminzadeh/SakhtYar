#!/usr/bin/env node
/** Source-only fail-closed contracts. Not a live security, DB, or HTTP test. */
import assert from 'node:assert/strict'
import fs from 'node:fs'
const read = p => fs.readFileSync(p,'utf8')
const root='backend/modules/'
const sec=read(root+'identity/src/main/java/com/sakhtyar/identity/config/SecurityConfig.java')
const origin=read(root+'identity/src/main/java/com/sakhtyar/identity/config/Phase03AllowedOrigins.java')
const auth=read(root+'identity/src/main/java/com/sakhtyar/auth/api/AuthController.java')
const jwt=read(root+'identity/src/main/java/com/sakhtyar/identity/security/JwtService.java')
const session=read(root+'identity/src/main/java/com/sakhtyar/identity/application/AuthSessionService.java')
const document=read(root+'document/src/main/java/com/sakhtyar/document/application/DocumentService.java')
const safety=read(root+'document/src/main/java/com/sakhtyar/document/application/Phase03DocumentSafety.java')
assert.match(sec,/Phase03AllowedOrigins\.parse\(allowedOrigins\)/)
assert.match(origin,/"http"\.equalsIgnoreCase\(scheme\)/)
assert.doesNotMatch(origin,/\.setAllowedOriginPatterns/)
const clientIp=auth.slice(auth.indexOf('static String clientIp('))
assert.doesNotMatch(clientIp,/X-Forwarded-For/)
assert.match(clientIp,/return request\.getRemoteAddr\(\)/)
assert.match(jwt,/accessExpirationMinutes < 1/)
assert.match(session,/refreshExpirationDays < 1/)
assert.match(document,/Phase03DocumentSafety\.validateUpload\(file\)/)
assert.match(document,/Phase03DocumentSafety\.requireOwnedObjectKey/)
assert.match(safety,/file\.getInputStream\(\)/)
assert.match(safety,/key\.startsWith\(prefix\)/)
for(const name of ['V57__construction_case_access.sql','V66__simulation_fixture_ledger_test_scope.sql'])
  assert.ok(fs.existsSync('backend/app/src/main/resources/db/migration/'+name))
console.log('PASS: Phase03 source-only origin, IP, token TTL, document key/signature contracts')
