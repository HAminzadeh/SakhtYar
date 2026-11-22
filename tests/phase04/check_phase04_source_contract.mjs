#!/usr/bin/env node
// SOURCE_ONLY. No DB, HTTP, filesystem mutations or real E2E execution.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
const root = process.cwd();
const read = rel => fs.readFileSync(path.join(root,rel),'utf8');
const paths = {
  importer:'backend/modules/knowledge/src/main/java/com/sakhtyar/knowledge/application/KnowledgeDatasetPackageService.java',
  crawler:'backend/modules/crawler/src/main/java/com/sakhtyar/crawler/application/IngestionPipeline.java',
  discovery:'backend/modules/crawler/src/main/java/com/sakhtyar/crawler/infrastructure/UrlDiscovery.java',
  canonicalizer:'backend/modules/crawler/src/main/java/com/sakhtyar/crawler/infrastructure/UrlCanonicalizer.java',
  fetcher:'backend/modules/crawler/src/main/java/com/sakhtyar/crawler/infrastructure/PageFetcher.java',
  security:'backend/modules/identity/src/main/java/com/sakhtyar/identity/config/SecurityConfig.java',
  plans:'tests/e2e/scenario-plans.json'
};
assert.match(read(paths.importer), /Phase04ArchiveGuard\.inspect\(/);
assert.match(read(paths.importer), /Phase04ArchiveGuard\.copyBounded\(/);
assert.match(read(paths.importer), /Duplicate manifest path/);
assert.match(read(paths.crawler), /Phase04RobotsPolicy\.fromResponse/);
assert.match(read(paths.crawler), /robots\.requireAllowed/);
assert.match(read(paths.crawler), /Crawler REAL ingestion requires USER_MANAGE/);
assert.match(read(paths.crawler), /CrawlTargetPolicy\.requireSameHost/);
assert.match(read(paths.crawler), /Crawler requires an authenticated real operator/);
assert.match(read(paths.discovery), /requires an explicit allowed host/);
assert.match(read(paths.canonicalizer), /CrawlTargetPolicy\.requireSyntax\(uri\)/);
assert.match(read(paths.fetcher), /Crawler response MIME type denied/);
assert.match(read(paths.security), /\.requestMatchers\("\/api\/v1\/crawler\/\*\*"\)\.hasAuthority\("USER_MANAGE"\)/);
const plans = JSON.parse(read(paths.plans));
assert.equal(plans.length,24);
assert.equal(plans.filter(x=>x.id==='E2E-14' && x.businessStatus==='LIMITED_CORE_FIXTURE').length,1);
assert.equal(plans.filter(x=>x.businessStatus==='NOT_IMPLEMENTED_FULL_E2E').length,23);
console.log('PASS Phase04 SOURCE_ONLY contracts (runtime Gate NOT_RUN)');
