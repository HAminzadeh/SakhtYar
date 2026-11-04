feat(test-isolation): harden shared PostgreSQL REAL/TEST boundaries and add V66 migration

## Scope

- Preserve previously implemented V58–V65 migrations, the 24-scenario catalog, and limited E2E-14 CORE fixture.
- Introduce V66 additive fixture-ledger TEST tagging and `SHARED_DB_TEST_GATED` isolation mode without resetting Flyway history or touching REAL domain rows.
- Strengthen V61 outbound database triggers to reject TEST queue writes even when a TEST application session inadvertently attempts to use the `REAL` default.
- Transition 23 non-CORE fixture declarations from `CLONE_REQUIRED` to `SHARED_DB_TEST_GATED`, leaving every unverified factory's writes disabled.
- Add read-only `dataScope` mappings for Case and Property entities; prevent TEST data from appearing in general-purpose REAL case, property, owner, and document business paths.
- Reject generic Case/Property/Owner operations executed under the application TESTER role until isolated test executors are provided.
- Prevent TESTER and non-REAL session access to the NotificationService outbox; explicitly label operational queue rows REAL and filter REAL-only read/update paths.
- Deny TESTER network egress in the Crawler PageFetcher, AI routing gateway, Ollama provider, and Neshan geocoding adapter.
- Preserve the existing MinIO adapter for REAL only; deny access for TEST cases and TESTER until a verified separate namespace adapter is wired.
- Add shared-database catalog/RLS read-only diagnostics, regression tests for security boundaries, and an offline one-click PowerShell installer with branch/SHA guards, anchor checks, backups and rollback-on-install-errors.

## Tests and verification

- Package manifest SHA256 and embedded ZIP integrity: independently verified.
- Phase02 originally committed catalog anchors verified against GitHub blob SHAs.
- PowerShell script and source artifacts created; Windows execution, Maven tests and PostgreSQL integration tests are **NOT EXECUTED HERE**.
- Installer attempts Maven safety-and-quality tests, full backend package, frontend build, and git diff checks on the target machine.

## Important limitations

This change is NOT a complete application-wide RLS rollout, does NOT enable the 24 business fixture writers, does NOT install a TEST MinIO/Redis adapter, and does NOT apply V66 to a running database. All unimplemented or unverified business workflows remain BLOCKED. Ensure a verified PostgreSQL backup before Flyway startup on the shared database.
