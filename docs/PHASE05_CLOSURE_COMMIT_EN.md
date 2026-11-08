feat(phase05): harden deterministic construction finance foundations and case-scoped analysis ACL

## Financial calculations and independent Golden
- Make participant rounding conservation independent of caller-provided input order by allocating in stable share/key order while preserving original API response ordering.
- Reject null financial inputs, bounded numeric magnitude/precision and excessively large participant collections before arithmetic.
- Preserve HALF_UP currency rounding and explicitly distinguish zero-cost ROI as undefined.
- Add an independent Python Decimal golden oracle covering 60/40 shares, revenue/cost/profit, ROI, margin, conservation, sensitivity shock, material waste, and FAR.
- Add synthetic version-pinned golden fixture with IRR units, frozen calculation timestamp, price/rule/algorithm versions and workspace/run identifiers.

## Material, assembly and feasibility boundaries
- Reject negative candidate material prices and invalid historical prices before anomaly classification.
- Add positive and negative JUnit contracts for material anomalies, assembly quantity/waste and FAR/zero-land behavior.

## Server-side authorization and IDOR protection
- Enforce CaseAccessService.requireWrite on analysis snapshot and feasibility creation before repository access.
- Enforce CaseAccessService.requireRead on detail, case list and lineage reads.
- Filter scenario-wide results with CaseAccessService.canRead before enriching or returning records.
- Add denial-first Mockito tests for cross-case access and prevention of unauthorized repository writes.

## Synthetic snapshots and isolated fixture contracts
- Add immutable, deterministic numeric Phase05 snapshots with input/output/envelope SHA-256 hashes, explicit currency units, version pins and frozen time.
- Reject ambiguous units, oversized numeric payloads and sensitive-text field names.
- Introduce deterministic workspace/run-scoped fixture identifiers for E2E-15 through E2E-20 using the existing Phase02 ID scheme.
- Keep live fixture seeding and cleanup fail-closed until restricted physical PostgreSQL isolation has been independently verified.

## Delivery and verification
- Add source-pinned, guarded PowerShell 5.1 installer with automatic ZIP discovery/extraction, manifest verification, Git Blob checks, idempotent application, backups, rollback and JSON reports.
- Include offline independent Oracle and selected JUnit test command; no fake integration PASS claims.
- Add before/after Gap Matrix, detailed Handoff and safety documentation.
- Preserve the shared REAL/TEST database: no clone, migration, role/permission changes, SQL execution or destructive cleanup.
- Do not implement or claim completion of Business E2E-15..20, and do not automatically Git commit/push.

Baseline v2: 831a54ec612166f825cc8fc87159d67d3b2b9247
Validation: see Phase05-Closure-Test-Evidence.json and local installer report.
