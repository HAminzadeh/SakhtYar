# API & schema compatibility — Phase 17-B

| Endpoint / table | Impact | Compatibility |
|---|---|---|
| `POST /api/v1/regulations/admin/rules/{id}/bind-evidence` | Existing explicit manager action preserved | Existing request/response unchanged |
| `POST /api/v1/regulations/admin/rules/{id}/bind-evidence/preview` | New read-only manager-gated validation; no write | Additive |
| `GET /api/v1/knowledge/admin/governance/releases/{id}/publication-preview` | New manager-only overview, no publish | Additive |
| `POST /api/v1/knowledge/admin/governance/releases/{id}/publish` | Now fail-closed on unvalidated release components, source provenance and self-publication | Harder safety gate |
| `POST /api/v1/regulations/evaluate` | Same DTO; conservative `REVIEW_REQUIRED`, exact arithmetic, more JSON snapshot fields | Same JSON schema; stricter semantics |
| `GET /api/v1/regulations/properties/{id}/evaluations` | Same DTO / preserved historical rows | Unchanged |
| `urban_rule.verified_knowledge_rule_id` | Existing V45 nullable FK now mapped into entity | No schema change |
| `urban_evaluation.input_snapshot` | Existing JSONB now stores ruleSnapshots, conflictRuleIds, coverageDecision | No schema change |
| `urban_evaluation_result.details` | Existing JSONB includes reproducibility metadata | No schema change |

No Flyway migration is included or executed. Database-clone verification is still mandatory for final acceptance.
