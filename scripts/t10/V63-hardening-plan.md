# V63 hardening review

The same PostgreSQL database is used. No Docker containers are created.

- V63 uses three history tables and FORCE RLS with no policies.
- This change adds a validated foreign key from `t10_e2e_run.workspace_id` to `simulation_workspace.id`.
- Child records remain linked using `ON DELETE RESTRICT`.
- The migration does NOT grant access, install permissive policies, run fixtures or create business executors.
- Before making history writes operational, implement a narrowly privileged application role and tested workspace policies. No implicit superuser privilege can be treated as isolation.
- Never edit an already applied Flyway migration; use a new version for subsequent changes.
- The migration should be tested by normal Flyway application startup, NOT direct psql execution.