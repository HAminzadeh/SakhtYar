# فاز ۱۷-C — ماتریس مستقل ۱۵ اقدام

| ID | Stage | Work | Evidence | Status | Test / blocker |
|---:|---|---|---|---|---|
| 31 | C01 | Feasibility freshness gate, rule evidence recheck | `FeasibilityRegulationGate / FeasibilityService` | READY_TO_VERIFY | Maven + API + DB clone |
| 32 | C01 | Geo / parcel scope changes | `propertyUpdatedAt/Rule binding checks` | BLOCKED | Jurisdiction-version integration and clone |
| 70 | C01 | No unconditional legal compliance | `Review-required / legalCoverageCertified fail-closed` | BLOCKED | Certified coverage model + legal confirmation |
| 33 | C02 | Rule details and review UX | `CaseAnalysisPanels UrbanEvaluation + Result tables` | READY_TO_VERIFY | npm build + browser |
| 34 | C02 | Persian dates, RTL and responsive table | `CaseAnalysisPanels Persian dates + scroll` | READY_TO_VERIFY | npm build + mobile browser |
| 71 | C02 | Verified source document provenance | `Evidence list displays verified IDs not file URLs` | BLOCKED | Authorized PDF endpoint + page / hash chain |
| 73 | C02 | Show applicable rule results | `CaseAnalysisPanels Evaluation details` | READY_TO_VERIFY | browser E2E fixture |
| 67 | C03 | Explain legal exclusions | `Bridge provenance/warning labels` | BLOCKED | Exclusion taxonomy end-to-end with published releases |
| 72 | C03 | Admin operational workbench | `KnowledgeGovernanceWorkbench embedded Bridge preview` | READY_TO_VERIFY | npm build + role-based browser |
| 36 | C04 | Automated regression tests | `Phase17CFeasibilityRegulationGateTest` | READY_TO_VERIFY | Maven -pl analysis -am test |
| 74 | C04 | PostgreSQL clone / RLS / E2E | `docs/phase17c/TEST_RUNBOOK.md` | BLOCKED | Clone with synthetic TEST data and browser tests |
| 38 | C05 | Guarded ZIP and Windows installer | `manifest + PowerShell + installer_engine.py` | READY_TO_VERIFY | Run all six modes on Windows |
| 39 | C05 | Documentation and handoff | `docs/phase17c/*.md` | READY_TO_VERIFY | Review API, rollback, operator guide |
| 40 | C05 | Acceptance accounting | `ACCEPTANCE_75.md` | READY_TO_VERIFY | Verify counts & real test logs |
| 75 | C06 | Shadow production / staged rollout | `Operational runbook; no shadow decision run` | BLOCKED | Flag, shadow telemetry, legal owner go-live approval |
