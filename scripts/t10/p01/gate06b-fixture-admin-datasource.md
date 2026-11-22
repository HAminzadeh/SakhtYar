# T10 P01 / Gate06-B: separate fixture provisioning connection

## Scope
- `SimulationCoreFixtureService` uses an explicitly selected administrative DataSource.
- Spring Boot's primary application DataSource is NOT overridden; Flyway/JPA remain unchanged.
- When all three `app.t10.fixture-admin.*` settings are supplied, only the fixture provisioner uses the dedicated connection.
- Without them the old connection is retained for compatibility. This is **not** secure runtime separation yet.
- Partial settings and `require-dedicated=true` without credentials fail closed.

## Future opt-in configuration (never commit actual credentials)

    APP_T10_FIXTURE_ADMIN_JDBC_URL=jdbc:postgresql://localhost:5432/sakhtyar
    APP_T10_FIXTURE_ADMIN_USERNAME=<privileged-provisioning-role>
    APP_T10_FIXTURE_ADMIN_PASSWORD=<secret>

The provider reads these environment variables directly through Spring `@Value` placeholders. To enforce the dedicated connection, set `APP_T10_FIXTURE_ADMIN_REQUIRE_DEDICATED=true` only after verifying credentials and provisioning behavior. Keep credentials in local secrets only. The fixture service currently requires `SUPERUSER` to create policies/roles. Do not activate limited application role until its wider database grants have been versioned/tested.

## Gate06-C prerequisites
- Prove actual runtime login is non-superuser/non-BYPASSRLS.
- Verify dedicated fixture provisioning still works and never shares runtime credentials.
- Introduce the least privilege role via a NEW Flyway migration (never edit V63).
- Cross-workspace positive/negative PostgreSQL tests under physically restricted logins.