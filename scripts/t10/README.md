# T10 E2E implementation workbench

This folder is committed with the SakhtYar source. This is **not** a claim that all 24 workflows are implemented.

Phases: 1 baseline and inventory; 2 RLS scope inspection; 3 Java outbound safety policy foundation; 4 deterministic dependency-order planner; 5 readiness handoff documentation.

All database SQL shipped in this package is SELECT-only. No Docker stack is created, and no migrations run.

Production work still required: per-table Flyway migrations reviewed against a live inventory, transactional scope for JPA/JDBC, cross-workspace FK enforcement, worker/outbox/MinIO/Redis integration, business fixture factories and all missing 23 executors, persisted run history/UI, and integration tests. The two Java classes in this package are foundations only and have no effect on runtime until wired into existing application services.

Run SQL with an approved read-only account; never execute fixture or schema changes as a side effect of source installation.
