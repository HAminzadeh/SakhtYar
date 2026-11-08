#!/usr/bin/env node
// Offline lockfile/security-version contract; npm audit remains authoritative for actual advisories.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';

const root = path.resolve(process.argv[2] ?? 'frontend');
const pkg = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
const lock = JSON.parse(fs.readFileSync(path.join(root, 'package-lock.json'), 'utf8'));
const packages = lock.packages ?? {};
function minVersion(v, floor) {
  const a = String(v).replace(/^v/, '').split('.').map(Number);
  const b = floor.split('.').map(Number);
  if (a.length !== 3 || a.some(x => !Number.isInteger(x))) return false;
  for (let i = 0; i < 3; i++) { if (a[i] !== b[i]) return a[i] > b[i]; }
  return true;
}
function installed(key, version) {
  const resolved = packages['node_modules/' + key]?.version;
  assert.equal(resolved, version, `Lockfile ${key} must resolve to ${version} (actual ${resolved})`);
}
assert.equal(pkg.name, 'sakhtyar-frontend');
assert.equal(pkg.dependencies['@neshan-maps-platform/maplibre-sdk'], '6.16.0');
assert.equal(pkg.dependencies.zod, '4.6.7');
assert.equal(pkg.devDependencies.vite, '8.3.6');
assert.equal(pkg.overrides?.['source-map-js'], '1.2.2');
assert.deepEqual(packages[''].dependencies, pkg.dependencies, 'Lockfile root dependencies do not match package.json');
assert.deepEqual(packages[''].devDependencies, pkg.devDependencies, 'Lockfile root devDependencies do not match package.json');
installed('@neshan-maps-platform/maplibre-sdk', '6.16.0');
installed('zod', '4.6.7');
installed('vite', '8.3.6');
installed('source-map-js', '1.2.2');
let numMaplibre = 0;
for (const [name, meta] of Object.entries(packages)) {
  if (/(^|\/)node_modules\/maplibre-gl$/.test(name)) {
    numMaplibre++;
    assert.ok(minVersion(meta.version, '6.4.1'), `Vulnerable maplibre-gl still in lockfile: ${name}@${meta.version}`);
  }
}
const propertyMap = fs.readFileSync(path.join(root, 'src/features/case/PropertyMap.tsx'), 'utf8');
assert.ok(propertyMap.includes("from '@neshan-maps-platform/maplibre-sdk'"), 'PropertyMap must use SDK import');
assert.ok(propertyMap.includes("'@neshan-maps-platform/maplibre-sdk/style.css'"), 'Neshan style import is missing');
assert.ok(/new maplibregl\.Map\(/.test(propertyMap) && propertyMap.includes('catch (error)'), 'Map initialization error boundary is missing');
assert.ok(!/\bstyleimagemissing\b|\bsetWorkerUrl\s*\(|\bmap\.transform\b|\bzoomLevelsToOverscale\b/.test(propertyMap), 'Potential MapLibre 5-only API detected');
console.log('PASS: lockfile uses Neshan SDK 6.16.0, Vite 8.3.6, Zod 4.6.7, source-map-js 1.2.2; maplibre-gl instances='+numMaplibre+' (all patched).');
console.log('PASS: PropertyMap imports and GPU initialization error boundary present. Full browser/WebGL2 compatibility requires visual testing.');
