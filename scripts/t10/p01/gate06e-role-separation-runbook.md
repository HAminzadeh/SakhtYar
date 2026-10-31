# Gate06-E | PostgreSQL role separation â€” source-only preparation

## Verified v2 integration points

- Runtime: SAKHTYAR_RUNTIME_DB_USER and SAKHTYAR_RUNTIME_DB_PASSWORD, legacy fallback.
- Flyway: SAKHTYAR_FLYWAY_USER and SAKHTYAR_FLYWAY_PASSWORD, legacy fallback.
- Fixture: APP_T10_FIXTURE_ADMIN_JDBC_URL, APP_T10_FIXTURE_ADMIN_USERNAME,
  APP_T10_FIXTURE_ADMIN_PASSWORD, APP_T10_FIXTURE_ADMIN_REQUIRE_DEDICATED.
- Runtime guard: APP_T10_REQUIRE_RESTRICTED_RUNTIME; checks rolsuper and rolbypassrls.

## Planned phases

E1: run gate06e-role-inventory.sql (read-only) with an authorized PostgreSQL client; record sanitized evidence.
E2: REVIEW the design-only gate06e-role-provisioning.sql.template; no live role changes.
E3: derive least-privilege object/sequence/function GRANT manifest from application SQL paths and table owners.
E4: configure isolated DB CLONE only; prove runtime vs Flyway vs fixture principal with actual sessions.
E5: execute negative cases listed in gate06e-negative-tests.md in disposable clone.
E6: record evidence and rollback; retain V64 blocked until negative tests and compatibility pass.

## Hard safety gates

Do not modify Docker volumes or recreate PostgreSQL. Do not alter role sakhtyar.
Do not edit Flyway V63, create V64, create policies, or enable strict runtime in existing environment.
No SQL/DB operations are executed by installer. No secrets committed to Git.
Startup probe requires proven Spring lifecycle wiring; unit tests alone are insufficient.
