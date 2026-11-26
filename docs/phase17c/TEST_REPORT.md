# Phase 17-C — Actual verification ledger

Date: 2026-11-26. Source baseline: `v2@629565366c67f21d75dcff4cf4f2971fa8445335`.

| Check | Result | Actual evidence |
|---|---|---|
| GitHub HEAD / manifest original Git blob references | PASS | Fresh branch read and six exact source file SHA refs |
| GitHub patch anchors | PASS | Every existing source anchor checked as occurring once (additional flag anchors audited from current source) |
| Java 21 standalone compile | PASS | `FeasibilityRegulationGate` compiled with minimal domain stubs |
| Java 21 standalone smoke | PASS | Seven checks, including REAL/TEST, stale scenario, cross-city cost, uncertified COMPLIANT and revoked binding |
| TSX grammar | PASS | Four changed TSX fragments parsed, 0 syntax errors |
| Installer integration on synthetic Git repo | PASS | SelfTest / Audit / DryRun / ApplySource / re-Audit / Rollback and tamper refusal |
| ZIP/Manifest integrity | PASS | SHA256 and archive CRC validation during packaging |
| Actual Maven reactor / JUnit on complete source | READY_TO_VERIFY | Run Phase17C installer Verify on Windows |
| Frontend `npm run build` (`tsc -b && vite`) | READY_TO_VERIFY | Run installer Verify, requires Node.js >=24 |
| PostgreSQL clone/RLS/EXPLAIN | BLOCKED | No safe, accessible clone in this environment |
| Browser UI/E2E with legal fixtures | BLOCKED | No browser plus isolated fixture/approval available |
| Shadow aggregation and Production activation | BLOCKED | No legal sign-off, telemetry or independent rollout gate acceptance |

The Maven log previously supplied for Phase17-B (207 tests, 13 reactor modules) is historical; it does NOT verify this Phase17-C change.
