import { readFileSync, writeFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const thisDir = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(thisDir, '../../..')
const src = resolve(repoRoot, 'engineering/ai-harness/catalog.json')
const dest = resolve(repoRoot, 'frontend/src/data/engineeringCatalog.ts')
const d = JSON.parse(readFileSync(src, 'utf8'))
if (d.schema !== 'sakhtyar.ai-engineering.catalog.v1' || d.agents?.length !== 71 || d.skills?.length !== 311) {
  throw new Error('Catalog schema or counts differ from the pinned v0.1.0 reference; review changes before updating generator.')
}
if (new Set(d.agents.map(x=>x.slug)).size !== d.agents.length || new Set(d.skills.map(x=>x.slug)).size !== d.skills.length) {
  throw new Error('Duplicate catalog slug detected')
}
const content = {
  version: d.version,
  sourceCommit: d.upstreamCommit,
  agents: d.agents,
  skills: d.skills,
  workflows: d.workflows,
}
const head = `// Generated from engineering/ai-harness/catalog.json. Public, non-sensitive documentation only.\nexport type HubEntry = {\n  id: string; slug: string; type: 'agent' | 'skill'; title: string; description: string;\n  category: string; categoryLabel: string; readiness: 'adapted' | 'catalog';\n  steps: string[]; output: string; warning: string; example: string; source: string;\n};\nexport type HubWorkflow = { id: string; title: string; tag: string; summary: string; agents: string[]; skills: string[]; steps: string[]; example: string };\nexport type HubCatalog = { version: string; sourceCommit: string; agents: HubEntry[]; skills: HubEntry[]; workflows: HubWorkflow[] };\nexport const hubCatalog: HubCatalog = `
const generated = head + JSON.stringify(content) + ';\n'
if (process.argv.includes('--check')) {
  if (readFileSync(dest, 'utf8') !== generated) {
    process.stderr.write('Catalog TS is out of sync. Run: node engineering/ai-harness/scripts/sync-frontend-catalog.mjs\n')
    process.exit(1)
  }
  process.stdout.write('Catalog TS matches JSON.\n')
} else {
  writeFileSync(dest, generated, 'utf8')
  process.stdout.write('Catalog TypeScript refreshed.\n')
}
