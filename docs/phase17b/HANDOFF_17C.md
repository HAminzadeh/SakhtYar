# HANDOFF — Phase 17-B to Phase 17-C

- Baseline source: `v2@f8578f0fa2aff8aea0a2710018919f6aec9e8bfe`. Recheck branch head after Phase 17-B push.
- New previews: admin binding `/api/v1/regulations/admin/rules/{id}/bind-evidence/preview`; publication `/api/v1/knowledge/admin/governance/releases/{id}/publication-preview`.
- The engine uses exact cross-multiplication for FAR and coverage; unresolved overlap/unknown conditions must not assert COMPLIANT.
- A modified rule clears its legacy evidence link. Existing `input_snapshot` and result `details` now carry more rule data; do not mutate historical rows.
- Release publication cannot set missing release components to COMPATIBLE itself; approval and review must be independent.
- Remaining BLOCKED: audited rule version ledger and migration clone tests; legal precedence and revision approval; full textual clauses; secure bounded batch binding workflow and independent preview token; backtest historic version-as-of; provenance hash/page source verification; Scenario global vs case-specific association; SQL/RLS/EXPLAIN and browser E2E.
- Historical actual DB snapshot from 2026-11-25: AI LAW=2596, canonical VERIFIED=0, PUBLISHED=0, urban rules=0. Re-measure read-only before any acceptance report.
- Deliverables include 31-row matrix and SQL audit; no real regulation approval or production migration.
- Phase C must preserve existing Feasibility and UI DTO compatibility, add coverage reports and shadow mode; distinguish numeric rule PASS from whole-project legal compliance.
