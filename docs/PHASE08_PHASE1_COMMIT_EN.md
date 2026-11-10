feat(quality): enforce fail-closed Phase08 business E2E pre-write admission and evidence readiness

- Introduce Phase08SafetyAdmission with a conservative, side-effect-free proof contract.
  Require physical workspace-restricted PostgreSQL LOGIN identity, row_security,
  complete Phase02 isolation baseline, explicit global outbound protection,
  run-scoped cleanup, evidence storage, disabled development seed and verified provenance.
- Reject business E2E start before persisting any run, creating a TESTER identity,
  inserting result steps or invoking a business workflow when these proofs are absent.
  The existing independent Phase07 read-only diagnostics remain available.
- Expose owner-verified, administrator-only GET /e2e/safety-readiness with explicit
  blocker codes and seven source-defined Phase1 scenario acceptance requirements.
- Add deterministic, workspace/run-scoped synthetic fixture plan for a 300 m² property
  and 60/40 owners; it is metadata only and never authorizes SQL writes.
- Update the Persian Simulation Center business tab with a Phase08 safety readiness
  report, refresh/export controls and disabled unsafe execution buttons.
- Add four unit-test classes covering fail-closed admission, role mismatch, seed
  rejection, deterministic fixture IDs, controller authorization and no-write startup.
- Add offline source contract and GitHub CI workflow without database access.
- Document 7-scenario before/after gap matrix, evidence requirements and Phase2 handoff.

Safety boundaries: no database clone/restore, migrations, role changes, RLS DDL,
seed, Docker, external network calls, or automatic Git operations are performed
by the patch installer. The full business workflow count remains 0/24 until
independent runtime proof exists; E2E-05 practical restore remains blocked.

Verified locally: Java 21 pure-logic compilation/smoke tests, offline source
contract, installer audit/apply/verify/idempotency/conflict preservation.
Pending on Windows: real Maven selected JUnit, full backend package, Vite build,
and runtime TEST isolation proof. Do not claim these passed without launcher logs.
