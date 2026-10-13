# Phase 5.17 - Security boundaries

1. Public BFF endpoints may return only explicitly public data.
2. `/api/web/v1/cases/{caseId}/summary` requires a verified end-user identity and per-case authorization.
   A global CMS-to-Backend bearer token MUST NOT substitute for that authorization.
3. The WordPress PHP bridge only calls public market/capabilities endpoints.
4. WordPress bridge health endpoint is only accessible to `manage_options`.
5. Do not log Authorization headers, cookies, PII or full document contents.
6. Keep PostgreSQL, Redis, MinIO, Ollama and monitoring backends off the public Internet.
7. Rate-limit read-heavy endpoints and configure timeouts at the API gateway.
8. Comparable-level observations require CASE_READ; only aggregate market data is public.
9. The BFF's market observations are sourced from persisted records, NOT synthetic demo data.
10. A price listing is not a verified closing price. Display provenance, age and sample count.
11. Existing CaseService list/get currently use repository-wide reads without per-case owner/membership checks.
    This is a P0 multi-tenant security blocker: add server-side per-case authorization
    consistently across all case, document, owner, builder, contract and BFF endpoints.
12. Before production: permission threat model, staged E2E tests, OIDC rollout,
    webhook signing, backup+restore drill, dependency and secret scan.