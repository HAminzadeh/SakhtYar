feat(quality,bootstrap): introduce guarded development and production E2E profiles

- Add explicit development and production Spring profiles with public seed and automatic Flyway disabled.
- Reject conflicting startup configuration before Spring bean initialization and migration.
- Exclude the DevSeedRunner from both official profiles, preserving legacy local behavior.
- Exclude simulation and business E2E controllers from Production at the backend level.
- Expose authenticated profile introspection and add a development-only, read-only UI preflight session.
- Preserve Phase08 fail-closed business admission and require development before any possible business run write.
- Add optional dedicated physical PostgreSQL TEST-login inspection without main DataSource fallback or DDL.
- Add operator-friendly remediation/evidence catalog for all 11 Phase08 admission blockers.
- Extend Simulation Center with profile state, per-blocker guidance, read-only session lifecycle, and JSON export.
- Add JUnit and Node.js safety contracts, GitHub CI workflow, two IntelliJ run configs and guarded ZIP/PowerShell installer.
- Preserve source hashes and staged work, with backups and idempotent source patching.

VERIFICATION: Local source and standalone Java checks are included; Windows Maven/Frontend results must be recorded from the installer report. No Business E2E passes are implied. No DB clone, seed, migration, role/RLS change or cleanup is performed by installer.
Baseline v2: 6bb50f3026182cc8048e31b0dc82cdbbb1679222
