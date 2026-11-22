feat(e2e): add 13 safe offline scenario-specific adapters and explicit acceptance test plans

- Add unique Spring-discoverable adapters for E2E-07..13 and E2E-15..20.
- Implement deterministic, side-effect-free contracts for knowledge ZIP/import
  prerequisites, checkpoints, OCR provenance, human review, governance,
  retrieval scoping, crawler URL rejection, builder ACL, material median/waste,
  construction snapshots, feasibility FAR, decimal finance allocation and
  sensitivity replay.
- Register adapters via the existing ScenarioExecutorRegistry component scan,
  preserving the original Phase08/Phase09 pre-write isolation gates.
- Make every offline adapter advertise OFFLINE_CONTRACT_ONLY and return BLOCKED
  after successful offline checks; never claim HTTP, DB, browser or business PASS.
- Add negative boundary assertions and JUnit registry coverage for all 13 cases.
- Add independent Java 21 smoke test and isolated GitHub Actions CI workflow.
- Document the 13-scenario before/after gap matrix, security limits and Phase3 handoff.
- Keep production data, schema, Docker, external providers and user-owned Git changes untouched.

Verification: independent Java 21 pure contract smoke PASS (13/13);
full Maven/Frontend/HTTP/DB/Browser tests NOT verified on the target machine.
Outstanding: 13 real authenticated isolated business workflows, restricted
TEST role + RLS proof, scoped cleanup, immutable signed artifacts and acceptance.

- Align package against GitHub v2 HEAD 666fbb523a87520a40141c2468da1f8684e4ff66; preserve previously pushed Phase10 sources.
- Add per-scenario fixture, negative tests, dependencies and seven mandatory proof types; missing proof must never upgrade an offline pass.
