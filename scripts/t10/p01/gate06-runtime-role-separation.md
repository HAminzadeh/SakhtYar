# T10-P01 / Gate06 — runtime role separation foundation

## What this gate implements
- Configurable `SAKHTYAR_RUNTIME_DB_USER` / `SAKHTYAR_RUNTIME_DB_PASSWORD` for the default Spring datasource.
- Configurable `SAKHTYAR_FLYWAY_USER` / `SAKHTYAR_FLYWAY_PASSWORD` for Spring Flyway's separate connection.
- Preserves old POSTGRES_USER / POSTGRES_PASSWORD defaults when overrides are unset.
- Adds a startup guard enabled only when `APP_T10_REQUIRE_RESTRICTED_RUNTIME=true`.
- The guard rejects a default datasource role with SUPERUSER or BYPASSRLS.
- No database, Flyway migration, grants, roles, or Docker changes.

## Important limitations and rollout gates
1. The current `SimulationCoreFixtureService` provisions workspace roles through the default DataSource and explicitly requires superuser. Enabling a restricted default runtime user will break core fixture provisioning until a separate admin-only provisioning datasource is implemented.
2. Do not enable the restricted runtime guard on the existing superuser-based local backend; startup will intentionally fail.
3. Configuring two user names does not create roles, grant permissions, or prove RLS.
4. Implement a dedicated admin datasource for fixture provisioning and least-privilege runtime grants in a later reviewed Flyway migration.
5. Test positive and negative access to history with nonprivileged roles before enabling V64 policies.
6. Do not alter already-applied V63; never store DB passwords in Git.

## Deployment sequence (later, not performed by this script)
- Version controlled roles/grants (Flyway) + runtime role permissions audit.
- Fixture service admin datasource routing + separate history writer datasource.
- Verify restricted role can start app, authenticate, and exercise all endpoints.
- Set `APP_T10_REQUIRE_RESTRICTED_RUNTIME=true` only after required grants are proven.
