#!/usr/bin/env node
/** Source-only phase06 topology checks: never invokes DB or AI providers. */
import fs from 'node:fs'
import path from 'node:path'
import assert from 'node:assert/strict'
import {fileURLToPath} from 'node:url'
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..')
const get=rel=>fs.readFileSync(path.join(root,...rel.split('/')),'utf8')
const ai=get('backend/modules/agents/src/main/java/com/sakhtyar/agents/provider/Phase06FakeAiModelProvider.java')
assert.match(ai,/implements AiModelProvider/)
assert.doesNotMatch(ai,/@Service|@Component|JdbcTemplate|RestClient|HttpClient/)
assert.match(ai,/Tool not allowlisted/)
const guard=get('backend/modules/quality/src/main/java/com/sakhtyar/quality/Phase02OutboundGuard.java')
assert.match(guard,/127\.0\.0\.1/)
assert.match(guard,/explicitTestMode/)
const outbox=get('backend/modules/notification/src/main/java/com/sakhtyar/notification/application/Phase06FakeNotificationOutbox.java')
assert.doesNotMatch(outbox,/JdbcTemplate|RestClient|HttpClient|@Service/)
const config=get('backend/modules/config/src/main/java/com/sakhtyar/config/Phase06ConfigSandbox.java')
assert.doesNotMatch(config,/@Service|JdbcTemplate|PlatformConfigService/)
assert.match(config,/Separation of duties/)
const executor=get('backend/modules/intelligence/src/main/java/com/sakhtyar/intelligence/application/WorkflowExecutor.java')
assert.doesNotMatch(executor,/trim\(ex\.getMessage\(\),2000\)|Map\.of\(\),ex\.getMessage\(\)/)
const ollama=get('backend/modules/agents/src/main/java/com/sakhtyar/agents/provider/ollama/OllamaAiModelProvider.java')
assert.doesNotMatch(ollama,/truncate\(ex\.getResponseBodyAsString\(\), 800\)/)
assert.match(get('.github/workflows/phase06-quality-gate.yml'),/release_approval\.mjs/)
console.log('PASS Phase06 SOURCE_ONLY: fake adapters, Phase02 reuse, redacted failures, strict CI release path')
