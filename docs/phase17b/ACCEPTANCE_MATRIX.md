# Phase 17-B — Acceptance Matrix (31 numbered actions)

Baseline: `v2@f8578f0fa2aff8aea0a2710018919f6aec9e8bfe` (2026-11-26). Source patches built for this exact commit.

Status legend: `READY_TO_VERIFY` = source/tests prepared, Maven and/or clone execution pending; `BLOCKED` = materially incomplete or requires independent human/DB-clone authorization.

| Group | Action ID | Capability | Evidence / file | Implemented or missing | Required verification | Status |
|---|---:|---|---|---|---|---|
| B01 | 10 | Rule type contracts | `UrbanRuleType / UrbanRuleEvaluator` | Six types retained; conditional keys now fail-closed | Phase17BExactArithmeticTest | **READY_TO_VERIFY** |
| B01 | 47 | Non-numeric rules | `RegulationService / EvidenceService` | Non-numeric obligations cannot be treated as PASS; separate legal rule execution remains unimplemented | Legal text fixture / B12 | **BLOCKED** |
| B01 | 48 | Extend rule taxonomy | `UrbanRuleType` | No new legal type without verified source examples; six current types retained | Schema fixtures needed | **BLOCKED** |
| B01 | 49 | AND/OR and exceptions | `UrbanRuleEvaluator` | Unknown condition/exception metadata returns REVIEW; full verified DSL deferred | conditionsAreNotSilentlyIgnored | **BLOCKED** |
| B02 | 14 | Invalidation after edits | `UrbanRuleEntity.update` | Material edit clears verified binding and deactivates | Phase17BBindingInvalidationTest | **READY_TO_VERIFY** |
| B02 | 15 | Immutable rule versions | `UrbanEvaluationEntity / Snapshot` | Snapshot includes rule parameters; persisted rule version ledger needs clone migration approval | History regression on clone | **BLOCKED** |
| B02 | 16 | As-of validity | `RegulationService / EvidenceService` | Existing local-date range filters preserved; as-of historical evaluation not exposed | Temporal fixture tests | **BLOCKED** |
| B02 | 55 | Supersession and revocation | `KnowledgeGovernanceService / UrbanRuleEntity` | Material updates invalidate binding; legal supersession model awaits reviewed source dates | Versioned fixture | **BLOCKED** |
| B03 | 19 | Conflict policy | `UrbanRuleConflictPolicy` | Overlapping differing thresholds are flagged, not silently selected by priority | Phase17BConflictPolicyTest | **READY_TO_VERIFY** |
| B03 | 56 | Legal hierarchy | `UrbanRuleConflictPolicy` | Conservative conflict detection; legal-precedence hierarchy intentionally not inferred | Conflict fixture | **BLOCKED** |
| B04 | 23 | Evaluator coverage | `UrbanRuleEvaluator` | All six current numeric types retained; new boundary tests | Phase17BExactArithmeticTest | **READY_TO_VERIFY** |
| B04 | 24 | Exact FAR and coverage | `UrbanRuleEvaluator` | Cross multiplication for decisions; division only for display | farBeyondLimit / coverageBeyondLimit | **READY_TO_VERIFY** |
| B04 | 25 | Invalid input | `UrbanRuleEvaluator / RegulationService.integer` | Reject negative/invalid numeric inputs and fractional integer floor counts | Negative and fractional tests | **READY_TO_VERIFY** |
| B04 | 26 | Missing-data fail closed | `UrbanRuleEvaluator` | Missing and conditional inputs yield REVIEW | Existing and new unit tests | **READY_TO_VERIFY** |
| B04 | 27 | No-rule status | `RegulationService.evaluate` | REVIEW_REQUIRED for empty matches and uncertified coverage | Regulation integration test | **READY_TO_VERIFY** |
| B05 | 28 | Snapshot of effective rules | `RegulationService.evaluate` | Rule parameters, scope, binding, dates and inputs stored in immutable evaluation snapshot | History tests on clone | **READY_TO_VERIFY** |
| B05 | 29 | Historical evaluation | `UrbanEvaluationEntity / Result.details` | Historical rows not overwritten by current rule edits; JSON snapshot retained | Clone history reproduction | **READY_TO_VERIFY** |
| B05 | 64 | Snapshot evidence provenance | `RegulationService / EvidenceService` | Rule and knowledge IDs retained; source SHA/page/release details need full canonical linkage | Golden provenance snapshot | **BLOCKED** |
| B06 | 30 | Audit coverage | `RegulationService / GovernanceService` | Existing create/update/evaluate audit retained; binding/review publication events not complete | Audit tests | **BLOCKED** |
| B06 | 35 | SQL schema/index migration | `V15 / V45 (unchanged)` | No migration without tested clone; version ledger and concurrency index still needed | PostgreSQL clone | **BLOCKED** |
| B07 | 57 | Applicability | `RegulationService / EvidenceService` | Property type/jurisdiction/date and reviewed binding gates preserved | SQL and access tests | **READY_TO_VERIFY** |
| B07 | 58 | Units and operators | `RegulationEvidenceService` | Checks reviewed type/operator/unit/number against rule at selection | Evidence negative tests | **READY_TO_VERIFY** |
| B08 | 59 | Candidate binding preview | `RegulationEvidenceService.previewBind` | Read-only manager-gated preview added; old bind still explicit | HTTP contract and mock tests | **READY_TO_VERIFY** |
| B08 | 60 | Batch bind workflow | `RegulationEvidenceController` | Single-rule explicit binding; bounded batch preview/apply/diff still unavailable | DB transaction clone | **BLOCKED** |
| B09 | 61 | Separation of reviewer/publisher | `KnowledgeGovernanceService.publish` | Rejects publishing by an approver of same release | Security tests | **READY_TO_VERIFY** |
| B09 | 62 | Release compatibility gate | `KnowledgeGovernanceService / KnowledgeReleaseService` | Nonempty COMPATIBLE components required; no self-approval at publish | Release compatibility test | **READY_TO_VERIFY** |
| B10 | 63 | Stale binding invalidation | `UrbanRuleEntity / EvidenceService` | Material updates clear FK; selection rechecks approved numeric semantics | Phase17BBindingInvalidationTest | **READY_TO_VERIFY** |
| B11 | 65 | Secure rule selection | `RegulationService / EvidenceService` | Existing case ACL, SQL-reviewed evidence and scope checks preserved | RLS/EXPLAIN | **READY_TO_VERIFY** |
| B11 | 66 | Safe as-of SQL selection | `RegulationEvidenceService` | Existing SQL filters prior limit; full indexed as-of snapshot query needs clone | Postgres clone EXPLAIN | **BLOCKED** |
| B12 | 68 | Municipality input conflicts | `RegulationEvidenceService.municipalityInputs` | Differing limits no longer use first-wins; conflict keys removed | Golden conflict fixture | **READY_TO_VERIFY** |
| B12 | 69 | Textual obligation handoff | `RegulationEvidenceService.municipalityInputs` | ReviewRequired signal present; rich legal clauses endpoint deferred | Legal text integration | **BLOCKED** |

## Non-negotiable gates

- No real-law verification, AI approval uplift, automatic release publication or automatic UrbanRule activation.
- A component marked PENDING_REVIEW must not be changed to COMPATIBLE by the publishing operation.
- Passing a few numeric rules is insufficient to declare total legal compliance.
- Additive migration is not supplied because there is no verified PostgreSQL clone available; immutable rule version ledger / historical as-of remain BLOCKED.
- Existing global `ConstructionScenario` is not case-owned; no fabricated case ownership check.
- Source PDF bytes and page source SHA cannot be independently verified from GitHub.
- No Maven/JUnit/Postgres/Browser PASS asserted without logs from user.
