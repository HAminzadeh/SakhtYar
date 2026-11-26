# HANDOFF — Phase17C to Phase18

- Recheck HEAD of `v2` after commit. Phase17C installer baseline `629565366c67f21d75dcff4cf4f2971fa8445335`.
- Latest Phase17B Maven verify from user: 13 modules, 207 tests passed; Phase17C Maven/npm and E2E need new independent logs.
- Existing canonical LAW data historical: 2596 items, verified=0, published=0, active urban=0 on 2026-11-25; recheck with SQL READ ONLY.
- Backend newly rejects stale Feasibility snapshots when Property/Scenario timestamp or Rule binding/current evidence differs. Old UrbanEvaluation rows require new evaluation before future consumption.
- UI embeds read-only AI Bridge in Knowledge Governance Workbench and shows individual rule outcomes in Case Analysis.
- Outstanding: legally sound coverage certification, textual clauses/exception DSL, official PDF viewer with authorization, persistent version ledger, concurrency locks, Scenario ownership semantics, migration clone, RLS, E2E, shadow telemetry and production signoff.
- Never activate REAL rules or release without independent legal approval, audit and clone acceptance.
