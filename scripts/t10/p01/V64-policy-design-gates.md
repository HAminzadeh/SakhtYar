# T10-P01 | V64 access control design gates

Evidence is captured by `Start-SakhtYar-T10-P01-Audit.ps1` against the **existing** PostgreSQL container.
This is a design/audit gate, not a Flyway migration and not a claim of production readiness.

## Required before writing V64

1. Confirm the runtime DataSource role is non-superuser and has no BYPASSRLS; distinguish Flyway migrator from API runtime.
2. Confirm whether history writes are delegated via a dedicated restricted role or current service role, and map runtime grants.
3. Derive workspace authorization server-side from a verified principal; do not authorize using only client-settable `current_setting`.
4. For child history tables, enforce parent run's workspace and avoid leaking cross-workspace rows.
5. Define safe INSERT, SELECT, UPDATE and end-of-run transitions; prevent deleting or overwriting completed evidence.
6. Implement database tests using a non-superuser role: permitted own-workspace CRUD and denied cross-workspace CRUD.
7. Inspect existing `pg_policies` and `public` grants; do not use a blanket grant to PUBLIC or broad tenant policy.
8. Compare actual installed Flyway version inventory before naming a V64 migration.

## Constraints

- V63 is immutable (already applied).
- No new Docker stack, permanent database, superuser runtime test, or business-table write.
- No runnable V64 policy SQL is emitted until the authorization design is verified.
- Database catalog reports may contain internal role names and policy definitions; review before commit.