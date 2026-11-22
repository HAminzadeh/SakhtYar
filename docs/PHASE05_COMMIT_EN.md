feat(finance,builder): enforce case-scoped authorization and conserve financial allocations

- Preserve exact currency minor-unit totals across multi-party allocations by assigning deterministic rounding residual to the final participant.
- Validate non-negative material price observations and reject invalid sensitivity baselines before arithmetic.
- Introduce case-scoped access guards for builder matching and project-builder linkage.
- Enforce case read/write permissions for direct financial analysis and sensitivity APIs, including filtering cross-case list results.
- Add explicit casefile dependencies where needed.
- Add numeric golden regression tests, denied-IDOR unit tests and synthetic reference fixtures.
- Add a read-only PostgreSQL audit and Phase 05 gap matrix/handoff.
- Provide checksum-pinned, backup-first, conflict-safe PowerShell installer with Audit/DryRun/ApplySource/Verify modes.

Validation: source reviewed at GitHub v2 HEAD 578024f09d0ece8879770fc47df777a70c83e848; Golden numeric oracle independently calculated with Python Decimal. Windows Maven/build/runtime/DB/browser checks remain pending. No Flyway migration or REAL DB operations. E2E-15..E2E-20 remain BLOCKED until actual isolated end-to-end evidence.
