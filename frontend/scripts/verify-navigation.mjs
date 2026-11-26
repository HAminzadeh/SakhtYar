// Phase Navigation R01: no network/DB; validates the UI information architecture.
// Usage from frontend/: node scripts/verify-navigation.mjs
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'

const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const read = relative => fs.readFileSync(path.join(frontend, relative), 'utf8')
const appShell = read('src/app/AppShell.tsx')
const casePage = read('src/pages/CaseDetailPage.tsx')
const routes = read('src/App.tsx')
const stylesheet = read('src/styles/navigation-workflow.css')

for (const [file, source] of [
  ['AppShell.tsx', appShell], ['CaseDetailPage.tsx', casePage],
]) {
  const parsed = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
  assert.equal(parsed.parseDiagnostics.length, 0, `${file} must parse as TSX`)
}

const declaredRoutes = new Set([...routes.matchAll(/path="([^"*]+)"/g)].map(match => match[1]))
const menuRoutes = [...appShell.matchAll(/path: '(\/[^']+)'/g)].map(match => match[1])
assert.ok(menuRoutes.length >= 12, 'Expected the full main menu route inventory')
for (const route of menuRoutes) {
  assert.ok(declaredRoutes.has(route), `Main menu target is not registered in App.tsx: ${route}`)
}
console.log('PASS: main-menu links map to existing routes')

const stages = [...casePage.matchAll(/sections:\s*\[([^\]]+)\]/g)]
  .map(match => [...match[1].matchAll(/'([^']+)'/g)].map(item => item[1]))
assert.equal(stages.length, 4, 'Expected four workflow stages')
const sections = stages.flat()
const actualTabs = [...casePage.matchAll(/\{ key: '([^']+)', label:/g)].map(match => match[1])
assert.equal(sections.length, 12, 'All twelve existing case sections must be retained')
assert.equal(new Set(sections).size, 12, 'No duplicated stage section')
assert.deepEqual(new Set(sections), new Set(actualTabs), 'All existing case panel keys must remain reachable')
console.log('PASS: 12 case panels are accessible through exactly four stages')

assert.ok(appShell.includes('hasPermission(link.permission)'), 'Main navigation must filter by permission')
assert.ok(appShell.includes('Drawer') && appShell.includes('sy-nav-mobile-trigger'), 'Accessible mobile menu required')
assert.ok(casePage.includes("next.set('section', nextTab)"), 'Case navigation must preserve selected section in URL')
assert.ok(casePage.includes('راهـنمای') || casePage.includes('راهنمای انجام کار'), 'Workflow must state that stages are not legal completion')
assert.ok(stylesheet.includes('.sy-case-stage:focus-visible'), 'Keyboard focus indicator required')
console.log('PASS: permissions, mobile navigation, deep-linking and keyboard focus contracts')

console.log('Navigation IA regression checks: ALL PASS')
