feat(quality): add 24 independently runnable E2E diagnostics and Simulation Center visibility

## Independent Scenario Diagnostics
- Implement one explicit, distinct Phase07 diagnostic executor for each E2E-01 through E2E-24.
- Run all scenarios independently, even when upstream business dependencies remain unverified.
- Reuse source-controlled scenario plans and Phase02 workspace/run fixture contracts.
- Execute safe in-memory TEST namespace, canary storage and outbound-denial checks per scenario.
- Report exact missing real-world business proof for each scenario without claiming a business pass.

## Read-Only Infrastructure Assessment
- Reuse SimulationScenarioChecks for PostgreSQL connectivity, required schema objects, MVC routes,
  existing safety gates and domain scope audits.
- Keep complete step evidence, per-scenario blockers, prerequisites and executable diagnostic details.
- Distinguish metadata checks, mock validations and restricted-role business E2E evidence.
- Return businessScenarioPassedCount=0 until a separate authenticated business test supplies verified artifacts.

## UI and Reporting
- Add an independently runnable 24-scenario diagnostic tab to Simulation Center.
- Provide run-all, per-scenario actions, progress metrics, detailed inspection and JSON export.
- Scope diagnostics to the caller-owned ACTIVE TEST workspace and USER_MANAGE authorization.
- Keep volatile bounded one-hour diagnostic report history accessible only to the originating owner.
- Preserve existing limited E2E-14 executor and strict business release gates unchanged.

## Safety and Installation
- Do not clone any database or change shared REAL/TEST isolation.
- Perform no SQL writes, database migrations, seed, RLS or role changes, restores or DB cleanup.
- Do not call real outbound AI, email, crawler, MinIO or Redis providers during diagnostics.
- Use a guarded PowerShell 5.1 launcher with ZIP discovery, SHA-256 verification, local Git checks,
  timestamped backups, idempotent source application, offline JUnit and frontend build gates.
- Never reset, commit, stage or push Git changes automatically.

## Verification
- Local Java 21 diagnostic contract harness: requires PASS before release.
- Backend Maven selected JUnit, all-modules package and Frontend production build: run by user-side installer.
- Actual HTTP, browser, restricted-role RLS and full Business E2E: still BLOCKED pending isolated implementations.
