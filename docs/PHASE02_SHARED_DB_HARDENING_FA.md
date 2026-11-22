# SakhtYar Phase 02 — Shared PostgreSQL REAL/TEST hardening

Baseline: branch `v2`, commit `6a82eb116d524bc6d8705023f6e2235557240f73` (2026-11-04).

## Implemented source changes

- V66 incrementally extends V65 with a TEST-only fixture-ledger marker, shared-db gated mode, immutable provenance, and V61 outbound queue fail-closed enhancement for sessions explicitly marked TEST.
- Replaces CLONE_REQUIRED declaration on 23 fixture contracts with SHARED_DB_TEST_GATED. Writes remain **disabled** until a specific domain is proven safe.
- Restricts ordinary CaseService reads/writes by TESTER, preventing a TESTER from performing REAL case API operations through a privileged application role.
- Adds read-only `dataScope` mapping to CaseEntity and prevents the ordinary DocumentService from accessing the shared REAL MinIO adapter for TEST cases and TESTER identities; does NOT claim end-to-end TEST storage implementation.
- Protects NotificationService using TESTER authorities and session scope, explicitly stores REAL rows, and only retrieves/updates REAL outbox entries.
- Prevents TESTER crawler HTTP requests at the actual `PageFetcher` network boundary.
- Adds read-only scope/provenance/RLS inventory query and a catalog contract regression test.

## Important unresolved gates — do not represent them as PASS

1. Runtime DataSource is not guaranteed non-superuser without the existing opt-in `APP_T10_REQUIRE_RESTRICTED_RUNTIME` and a configured restricted PostgreSQL login; avoid enabling without provisioning it.
2. RLS+FORCE RLS is proven on four CORE tables only. Other domain tables require individual policy/grants/transaction-scope propagation, cross-scope FK checks and negative independent-login tests.
3. Twenty-four catalogs are not twenty-four working fixture factories. The factory methods still BLOCKED, and `E2E-14` is a limited fixture, not a complete business E2E.
4. No application-wide safe TEST storage adapter, Redis namespace enforcement, email/SMS provider mocks, or AI egress interceptors are integrated. Existing TESTER paths are blocked rather than silently using REAL adapters.
5. `V65` and `V66` must be applied by Flyway in version order, never with direct `psql` bypass of Flyway history. The source installer **never runs migrations** or restarts Backend.
6. Live DB RLS, new SQL migration and source Maven tests must run in the user's environment. The installer can run Maven/npm but does not claim integration outcomes without observed logs.

## Safety of the database

`V66` is additive to the V65 metadata ledger and does not delete or mutate REAL domain rows. The updated V61 trigger function affects outbox/crawler queue behavior only when the session or row is TEST. Back up PostgreSQL before an application restart with Flyway enabled. As an existing migration, V65 is left byte-for-byte unchanged.

Use `tests/phase02/04_shared_db_gap_audit_readonly.sql` with a read-only DB connection to inventory remaining domains. No database writes are performed by that script.

## PowerShell installer

`Install-SakhtYar-Phase02-SharedDb-Hardening.ps1` is self-contained. No Python, no manual unzip. From Downloads run:

`powershell -NoProfile -ExecutionPolicy Bypass -File .\Install-SakhtYar-Phase02-SharedDb-Hardening.ps1`

Default: Audit, DryRun, backup, apply sources, hash Verify, Quality Maven tests, full backend package, frontend build. Modes: `Audit`, `DryRun`, `ApplySource`, `Verify`; `-SkipBuild` can be supplied for source-only tests. ApplySource explicitly does NOT connect to PostgreSQL.

In a dirty worktree, the installer aborts on any path-specific modifications, never uses `git reset`, `git clean`, or `stash`, and never overwrites nonmatching existing files.
