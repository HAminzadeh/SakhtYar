# SakhtYar — Phase 5.15 integration release

## Installed outcomes

1. GitHub Actions CI on `main`/`v2`: Java build/tests, TypeScript/Vite build, Compose syntax, migration naming, draft Golden Dataset validation.
2. `V45` creates audited human-review records. Existing records are **not automatically approved**.
3. Resumable pipeline creates `DRAFT` release, leaving execution `WAITING_FOR_REVIEW` instead of silently publishing.
4. Admin review API and UI require explicit subject, operator, unit, geographic scope, effective date, verified document hash/page/authority and reviewer identity.
5. Release publication requires all candidate rules to receive review decisions, and at least one approved rule.
6. Regulation evidence lists ONLY approved rules in a published release for the property city and district.
7. Urban rule binding checks type/operator/unit/numeric value and scope against approved knowledge. Older unbound urban rules do not produce a definitive `COMPLIANT` result.
8. Municipality Agent does not trust client-supplied municipal ratios; uses only bound verified urban rules.
9. Case UI exposes urban regulation, construction cost, feasibility, finance, and scenario comparison, consuming existing Backend APIs.
10. Tehran E2E checks an authenticated existing Tehran case, validates snapshot chain and refuses to guess missing data. A separate explicit `--execute` mode creates evaluation/feasibility/finance snapshots ONLY when exact user-supplied UUIDs and financial assumptions are passed.

## Execution and governance

On Windows, from the root: `powershell -ExecutionPolicy Bypass -File .\Apply-SakhtYar-Phase515-All.ps1`.
The installer **never pushes changes, starts production containers, deletes live data, or invents legal evidence**. It backs up overwritten files. It runs local tests if required dependencies exist, and reports skipped checks. Use `-Rollback` to restore the latest installer backup.

After restart, Flyway should run V45 against the **actual staging database**. Run in staging before production, back up the full DB separately, then manually verify `flyway_schema_history` and end-to-end API behavior. The CI migration-naming check is not a substitute for real Flyway execution.

Review flow: `/admin/knowledge` → pending rules → source PDF and page independent check → approve/reject each rule → release publish. If converting a knowledge rule to an urban rule, create/confirm urban rule via `/api/v1/regulations/rules`, then call `/api/v1/regulations/admin/rules/{urbanRuleId}/bind-evidence` with `{ "knowledgeRuleId": "..." }`. This link is checked for matching numeric limit/type/unit/jurisdiction, without importing arbitrary numeric text.

## Important remaining manual prerequisites

- Certified current regulatory source(s), parcel zoning, amendments, human-review decisions and source validity.
- Expert-verified Golden answers; the supplied 12 questions are NOT scored answers.
- Assembly/material prices, sale comparables, owner and builder UUIDs, scenario assumptions and explicit finance assumptions.
- Staging credentials, running PostGIS+pgvector PostgreSQL, Redis/MinIO, Flyway runtime check and real E2E execution.

`python scripts/e2e_tehran.py --case-id <EXISTING_CASE_UUID>` requires an actual authenticated session provided via environment variables; it performs read-only checks and reports `INSUFFICIENT_DATA` instead of inventing numbers.

### Optional explicit write-through E2E example

The following is intentionally a **template**; replace every `<...>` with real audited values and use a staging database, not production.

```bash
python scripts/e2e_tehran.py --execute --case-id <CASE_UUID> --scenario-id <SCENARIO_UUID> --cost-snapshot-id <COST_UUID> --sellable-area-m2 <EXPERT_AREA> --sale-price-per-m2 <VERIFIED_PRICE> --owner-id <OWNER_UUID> --builder-id <BUILDER_UUID> --owner-percent <EXPERT_AGREED_PERCENT> --currency IRR
```

Requires `SAKHTYAR_SESSION_COOKIE` or `SAKHTYAR_BEARER_TOKEN` in environment. The script makes no API modifications unless `--execute` is present. Financial figures are only explicit scenarios, not certified market values.
