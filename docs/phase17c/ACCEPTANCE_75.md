# فاز ۱۷-C — ماتریس پذیرش همه ۷۵ اقدام

Baseline GitHub: `v2@629565366c67f21d75dcff4cf4f2971fa8445335`.

**هشدار:** وضعیت A/B در این جدول صرفاً از ماتریس‌های تاریخی مخزن نقل می‌شود؛ موفقیت Maven مرحله B (۱۳ ماژول/۲۰۷ تست در لاگ کاربر) به معنی پذیرش موارد حقوقی/DB نیست. هیچ‌یک از وضعیت‌های READY_TO_VERIFY به‌طور خودکار به VERIFIED ارتقا داده نشده است.

| ID | Phase | Current evidence / outcome | Status | Outstanding validation |
|---:|---|---|---|---|
| 1 | A03 | `RegulationService / RegulationDtos` | READY_TO_VERIFY (A historical) | Maven/HTTP contract needs execution |
| 2 | A03 | `RegulationService / RegulationDtos` | READY_TO_VERIFY (A historical) | Maven/HTTP contract needs execution |
| 3 | A04 | `RegulationService + Phase17RegulationAuthorizationTest` | READY_TO_VERIFY (A historical) | Case-scoped checks ready; Scenario is global with no caseId |
| 4 | A04 | `RegulationService + Phase17RegulationAuthorizationTest` | READY_TO_VERIFY (A historical) | Case-scoped checks ready; Scenario is global with no caseId |
| 5 | A04 | `RegulationService + Phase17RegulationAuthorizationTest` | BLOCKED (A historical) | Scenario lacks caseId/assignment; do not invent ownership; add model/authorization gate in B |
| 6 | A04 | `RegulationService + Phase17RegulationAuthorizationTest` | READY_TO_VERIFY (A historical) | Case-scoped checks ready; Scenario is global with no caseId |
| 7 | A05 | `RegulationEvidenceService.bind + KnowledgeGovernanceService` | READY_TO_VERIFY (A historical) | Security tests and API integration pending |
| 8 | A05 | `RegulationEvidenceService.bind + KnowledgeGovernanceService` | READY_TO_VERIFY (A historical) | Security tests and API integration pending |
| 9 | A03 | `RegulationService / RegulationDtos` | READY_TO_VERIFY (A historical) | Maven/HTTP contract needs execution |
| 10 | B01 | `UrbanRuleType / UrbanRuleEvaluator` | **READY_TO_VERIFY** (B historical) | Phase17BExactArithmeticTest |
| 11 | A06 | `CandidateBridgeService.candidates + GovernanceService existing` | READY_TO_VERIFY (A historical) | No legal approval or publication performed |
| 12 | A06 | `CandidateBridgeService.candidates + GovernanceService existing` | READY_TO_VERIFY (A historical) | No legal approval or publication performed |
| 13 | A06 | `CandidateBridgeService.candidates + GovernanceService existing` | READY_TO_VERIFY (A historical) | No legal approval or publication performed |
| 14 | B02 | `UrbanRuleEntity.update` | **READY_TO_VERIFY** (B historical) | Phase17BBindingInvalidationTest |
| 15 | B02 | `UrbanEvaluationEntity / Snapshot` | **BLOCKED** (B historical) | History regression on clone |
| 16 | B02 | `RegulationService / EvidenceService` | **BLOCKED** (B historical) | Temporal fixture tests |
| 17 | A07 | `RegulationEvidenceService.evidence + JurisdictionService.forCase` | BLOCKED (A historical) | Null-city approval prevented by old V45 CHECK; clone migration needed |
| 18 | A07 | `RegulationEvidenceService.evidence + JurisdictionService.forCase` | BLOCKED (A historical) | Null-city approval prevented by old V45 CHECK; clone migration needed |
| 19 | B03 | `UrbanRuleConflictPolicy` | **READY_TO_VERIFY** (B historical) | Phase17BConflictPolicyTest |
| 20 | A08 | `RegulationEvidenceService.evidence + Policy.fingerprint` | READY_TO_VERIFY (A historical) | >1000-row DB regression pending |
| 21 | A06 | `CandidateBridgeService.candidates + GovernanceService existing` | READY_TO_VERIFY (A historical) | No legal approval or publication performed |
| 22 | A08 | `RegulationEvidenceService.evidence + Policy.fingerprint` | READY_TO_VERIFY (A historical) | >1000-row DB regression pending |
| 23 | B04 | `UrbanRuleEvaluator` | **READY_TO_VERIFY** (B historical) | Phase17BExactArithmeticTest |
| 24 | B04 | `UrbanRuleEvaluator` | **READY_TO_VERIFY** (B historical) | farBeyondLimit / coverageBeyondLimit |
| 25 | B04 | `UrbanRuleEvaluator / RegulationService.integer` | **READY_TO_VERIFY** (B historical) | Negative and fractional tests |
| 26 | B04 | `UrbanRuleEvaluator` | **READY_TO_VERIFY** (B historical) | Existing and new unit tests |
| 27 | B04 | `RegulationService.evaluate` | **READY_TO_VERIFY** (B historical) | Regulation integration test |
| 28 | B05 | `RegulationService.evaluate` | **READY_TO_VERIFY** (B historical) | History tests on clone |
| 29 | B05 | `UrbanEvaluationEntity / Result.details` | **READY_TO_VERIFY** (B historical) | Clone history reproduction |
| 30 | B06 | `RegulationService / GovernanceService` | **BLOCKED** (B historical) | Audit tests |
| 31 | C01 | FeasibilityRegulationGate / FeasibilityService | READY_TO_VERIFY | Maven + API + DB clone |
| 32 | C01 | propertyUpdatedAt/Rule binding checks | BLOCKED | Jurisdiction-version integration and clone |
| 33 | C02 | CaseAnalysisPanels UrbanEvaluation + Result tables | READY_TO_VERIFY | npm build + browser |
| 34 | C02 | CaseAnalysisPanels Persian dates + scroll | READY_TO_VERIFY | npm build + mobile browser |
| 35 | B06 | `V15 / V45 (unchanged)` | **BLOCKED** (B historical) | PostgreSQL clone |
| 36 | C04 | Phase17CFeasibilityRegulationGateTest | READY_TO_VERIFY | Maven -pl analysis -am test |
| 37 | A04 | `RegulationService + Phase17RegulationAuthorizationTest` | BLOCKED (A historical) | DB RLS/TEST-vs-REAL requires PostgreSQL clone, app TESTER role differs from DB role |
| 38 | C05 | manifest + PowerShell + installer_engine.py | READY_TO_VERIFY | Run all six modes on Windows |
| 39 | C05 | docs/phase17c/*.md | READY_TO_VERIFY | Review API, rollback, operator guide |
| 40 | C05 | ACCEPTANCE_75.md | READY_TO_VERIFY | Verify counts & real test logs |
| 41 | A01 | `READ_ONLY_AUDIT.sql / CandidateBridgeService.candidates` | READY_TO_VERIFY (A historical) | SQL inventory must run on clone |
| 42 | A01 | `READ_ONLY_AUDIT.sql / CandidateBridgeService.candidates` | READY_TO_VERIFY (A historical) | SQL inventory must run on clone |
| 43 | A02 | `CandidateBridgeService.candidates` | BLOCKED (A historical) | Exact PDF/page verification and origin PDF access unavailable |
| 44 | A09 | `CandidateBridgeService + CandidatePolicy` | READY_TO_VERIFY (A historical) | Read-only bridge; no automatic creation of Canonical Rule |
| 45 | A09 | `CandidateBridgeService + CandidatePolicy` | READY_TO_VERIFY (A historical) | Read-only candidate previews; persistent review decisions deferred |
| 46 | A09 | `CandidateBridgeService + CandidatePolicy` | READY_TO_VERIFY (A historical) | Read-only candidate previews; persistent review decisions deferred |
| 47 | B01 | `RegulationService / EvidenceService` | **BLOCKED** (B historical) | Legal text fixture / B12 |
| 48 | B01 | `UrbanRuleType` | **BLOCKED** (B historical) | Schema fixtures needed |
| 49 | B01 | `UrbanRuleEvaluator` | **BLOCKED** (B historical) | conditionsAreNotSilentlyIgnored |
| 50 | A10 | `CandidateBridgeService.relationshipProposal` | BLOCKED (A historical) | Only tentative relationship proposal; no authorized graph publication in A |
| 51 | A02 | `CandidateBridgeService.candidates` | BLOCKED (A historical) | Exact PDF/page verification and origin PDF access unavailable |
| 52 | A02 | `CandidateBridgeService.candidates` | BLOCKED (A historical) | Exact PDF/page verification and origin PDF access unavailable |
| 53 | A07 | `RegulationEvidenceService.evidence + JurisdictionService.forCase` | BLOCKED (A historical) | Missing legal effective-date evidence; no date inferred or written |
| 54 | A07 | `RegulationEvidenceService.evidence + JurisdictionService.forCase` | BLOCKED (A historical) | Old V45 city constraint prevents global review; clone migration and legal sign-off required |
| 55 | B02 | `KnowledgeGovernanceService / UrbanRuleEntity` | **BLOCKED** (B historical) | Versioned fixture |
| 56 | B03 | `UrbanRuleConflictPolicy` | **BLOCKED** (B historical) | Conflict fixture |
| 57 | B07 | `RegulationService / EvidenceService` | **READY_TO_VERIFY** (B historical) | SQL and access tests |
| 58 | B07 | `RegulationEvidenceService` | **READY_TO_VERIFY** (B historical) | Evidence negative tests |
| 59 | B08 | `RegulationEvidenceService.previewBind` | **READY_TO_VERIFY** (B historical) | HTTP contract and mock tests |
| 60 | B08 | `RegulationEvidenceController` | **BLOCKED** (B historical) | DB transaction clone |
| 61 | B09 | `KnowledgeGovernanceService.publish` | **READY_TO_VERIFY** (B historical) | Security tests |
| 62 | B09 | `KnowledgeGovernanceService / KnowledgeReleaseService` | **READY_TO_VERIFY** (B historical) | Release compatibility test |
| 63 | B10 | `UrbanRuleEntity / EvidenceService` | **READY_TO_VERIFY** (B historical) | Phase17BBindingInvalidationTest |
| 64 | B05 | `RegulationService / EvidenceService` | **BLOCKED** (B historical) | Golden provenance snapshot |
| 65 | B11 | `RegulationService / EvidenceService` | **READY_TO_VERIFY** (B historical) | RLS/EXPLAIN |
| 66 | B11 | `RegulationEvidenceService` | **BLOCKED** (B historical) | Postgres clone EXPLAIN |
| 67 | C03 | Bridge provenance/warning labels | BLOCKED | Exclusion taxonomy end-to-end with published releases |
| 68 | B12 | `RegulationEvidenceService.municipalityInputs` | **READY_TO_VERIFY** (B historical) | Golden conflict fixture |
| 69 | B12 | `RegulationEvidenceService.municipalityInputs` | **BLOCKED** (B historical) | Legal text integration |
| 70 | C01 | Review-required / legalCoverageCertified fail-closed | BLOCKED | Certified coverage model + legal confirmation |
| 71 | C02 | Evidence list displays verified IDs not file URLs | BLOCKED | Authorized PDF endpoint + page / hash chain |
| 72 | C03 | KnowledgeGovernanceWorkbench embedded Bridge preview | READY_TO_VERIFY | npm build + role-based browser |
| 73 | C02 | CaseAnalysisPanels Evaluation details | READY_TO_VERIFY | browser E2E fixture |
| 74 | C04 | docs/phase17c/TEST_RUNBOOK.md | BLOCKED | Clone with synthetic TEST data and browser tests |
| 75 | C06 | Operational runbook; no shadow decision run | BLOCKED | Flag, shadow telemetry, legal owner go-live approval |

## Gate summary
- C: 9 READY_TO_VERIFY; 6 BLOCKED. These are not test PASS claims.
- No legal approval / release publication / activation of real rules.
- Real DB clone and RLS/browser E2E are BLOCKED until the environment is provided.
- Source installation and Maven/npm build require Windows verification.
- The historic ۲٬۵۹۶ AI LAW records are not equivalent to valid urban rules.
