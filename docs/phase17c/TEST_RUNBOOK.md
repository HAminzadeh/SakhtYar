# Test & Release Runbook (isolation-first)

## Source Verify

1. Run the included installer SelfTest/Audit/DryRun, then ApplySource and Verify.
2. Verify must run Maven `-pl modules/analysis -am test` with dependency modules and bundled IntelliJ Maven fallback; `frontend/npm run build` (`tsc -b && vite build`); `git diff --check`.
3. Compare actual count from Maven output; do not claim old phase test counts for this release.

## PostgreSQL clone (manual, BLOCKED until environment)

- Create isolated TEST-scoped database clone with distinct credentials, host and name. Do not use running production/REAL database for inserts, publishing or rule approval.
- Run the read-only inventory SQL separately; it does not grant legal verification.
- Prepare synthetic LAW document with exact PDF SHA/page and rule version; test unpublished, published, revoked, expired, alternate location, cross-user access, TESTER denial, and a simulated approved binding.
- Exercise stale cost/scenario/property/scope snapshots and a revoked evidence binding. Confirm HTTP 409 before assessment insertion.
- Confirm SELECT permissions and RLS when configured; inspect indexes with EXPLAIN ANALYZE only against clone.
- Obtain explicit authorization before any Flyway migration in a non-test database.

## Browser E2E (manual, BLOCKED until browser + clone)

- Login as user with a REAL case and read permissions; confirm Evidence and results source labels show no filesystem path.
- Admin with USER_MANAGE sees read-only Bridge Preview, types RULE/SEGMENT/ALL, pages 1 and later; read-only role is denied by Backend.
- Invalid / expired / zero-rule case never shows a building permit or legal-compliance success.
- Confirm mobile RTL, long Persian text truncation, inaccessible source and 403/404/409 responses.
- Confirm UI cannot force success by modifying the network request: Backend rejects stale snapshots.

## Rollout

- Shadow/production activation is deliberately NOT implemented and marked BLOCKED. Keep all decision-making disabled/conditional pending legal coverage certification and reviewed operational checks.
- Monitor aggregated counts, conflicts and latency only after privacy/security review; do not log raw source text or PII.
