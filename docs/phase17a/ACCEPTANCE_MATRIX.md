# فاز ۱۷-A — ماتریس پذیرش ۲۹ اقدام

مبنای HEAD: `8936cbdbef08e2ee9f211b3ce29fe3f83f2f702d`؛ وضعیت‌ها **ادعای PASS اجرای تست نیستند**.

| شماره اقدام | گروه | وضعیت | شاهد سورس/تست | محدودیت یا گام تأیید |
|---:|---|---|---|---|
| 1 | A03 | READY_TO_VERIFY | `RegulationService / RegulationDtos` | Maven/HTTP contract needs execution |
| 2 | A03 | READY_TO_VERIFY | `RegulationService / RegulationDtos` | Maven/HTTP contract needs execution |
| 3 | A04 | READY_TO_VERIFY | `RegulationService + Phase17RegulationAuthorizationTest` | Case-scoped checks ready; Scenario is global with no caseId |
| 4 | A04 | READY_TO_VERIFY | `RegulationService + Phase17RegulationAuthorizationTest` | Case-scoped checks ready; Scenario is global with no caseId |
| 5 | A04 | BLOCKED | `RegulationService + Phase17RegulationAuthorizationTest` | Scenario lacks caseId/assignment; do not invent ownership; add model/authorization gate in B |
| 6 | A04 | READY_TO_VERIFY | `RegulationService + Phase17RegulationAuthorizationTest` | Case-scoped checks ready; Scenario is global with no caseId |
| 7 | A05 | READY_TO_VERIFY | `RegulationEvidenceService.bind + KnowledgeGovernanceService` | Security tests and API integration pending |
| 8 | A05 | READY_TO_VERIFY | `RegulationEvidenceService.bind + KnowledgeGovernanceService` | Security tests and API integration pending |
| 9 | A03 | READY_TO_VERIFY | `RegulationService / RegulationDtos` | Maven/HTTP contract needs execution |
| 11 | A06 | READY_TO_VERIFY | `CandidateBridgeService.candidates + GovernanceService existing` | No legal approval or publication performed |
| 12 | A06 | READY_TO_VERIFY | `CandidateBridgeService.candidates + GovernanceService existing` | No legal approval or publication performed |
| 13 | A06 | READY_TO_VERIFY | `CandidateBridgeService.candidates + GovernanceService existing` | No legal approval or publication performed |
| 17 | A07 | BLOCKED | `RegulationEvidenceService.evidence + JurisdictionService.forCase` | Null-city approval prevented by old V45 CHECK; clone migration needed |
| 18 | A07 | BLOCKED | `RegulationEvidenceService.evidence + JurisdictionService.forCase` | Null-city approval prevented by old V45 CHECK; clone migration needed |
| 20 | A08 | READY_TO_VERIFY | `RegulationEvidenceService.evidence + Policy.fingerprint` | >1000-row DB regression pending |
| 21 | A06 | READY_TO_VERIFY | `CandidateBridgeService.candidates + GovernanceService existing` | No legal approval or publication performed |
| 22 | A08 | READY_TO_VERIFY | `RegulationEvidenceService.evidence + Policy.fingerprint` | >1000-row DB regression pending |
| 37 | A04 | BLOCKED | `RegulationService + Phase17RegulationAuthorizationTest` | DB RLS/TEST-vs-REAL requires PostgreSQL clone, app TESTER role differs from DB role |
| 41 | A01 | READY_TO_VERIFY | `READ_ONLY_AUDIT.sql / CandidateBridgeService.candidates` | SQL inventory must run on clone |
| 42 | A01 | READY_TO_VERIFY | `READ_ONLY_AUDIT.sql / CandidateBridgeService.candidates` | SQL inventory must run on clone |
| 43 | A02 | BLOCKED | `CandidateBridgeService.candidates` | Exact PDF/page verification and origin PDF access unavailable |
| 44 | A09 | READY_TO_VERIFY | `CandidateBridgeService + CandidatePolicy` | Read-only bridge; no automatic creation of Canonical Rule |
| 45 | A09 | READY_TO_VERIFY | `CandidateBridgeService + CandidatePolicy` | Read-only candidate previews; persistent review decisions deferred |
| 46 | A09 | READY_TO_VERIFY | `CandidateBridgeService + CandidatePolicy` | Read-only candidate previews; persistent review decisions deferred |
| 50 | A10 | BLOCKED | `CandidateBridgeService.relationshipProposal` | Only tentative relationship proposal; no authorized graph publication in A |
| 51 | A02 | BLOCKED | `CandidateBridgeService.candidates` | Exact PDF/page verification and origin PDF access unavailable |
| 52 | A02 | BLOCKED | `CandidateBridgeService.candidates` | Exact PDF/page verification and origin PDF access unavailable |
| 53 | A07 | BLOCKED | `RegulationEvidenceService.evidence + JurisdictionService.forCase` | Missing legal effective-date evidence; no date inferred or written |
| 54 | A07 | BLOCKED | `RegulationEvidenceService.evidence + JurisdictionService.forCase` | Old V45 city constraint prevents global review; clone migration and legal sign-off required |

**تعداد اقدامات:** 29؛ شماره‌ها بدون تکرار: 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 17, 18, 20, 21, 22, 37, 41, 42, 43, 44, 45, 46, 50, 51, 52, 53, 54.

## Gateهای مستقل
- Security unit/HTTP tests: READY_TO_VERIFY; not executed in target Maven.
- Pure-Java proposal parser smoke checks: executed on this isolated environment; see verification log.
- Source approval & legal publication: NOT_PERFORMED, cannot be auto-completed.
- PostgreSQL Clone / RLS / multi-Instance concurrency: BLOCKED until isolated DB.
- UI Browser E2E: NOT_VERIFIED (UI for phase C not changed).
- Database baseline statistics above: snapshot supplied by user, NOT independently measured in this run.
