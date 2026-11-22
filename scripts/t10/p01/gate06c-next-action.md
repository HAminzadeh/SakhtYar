# T10-P01 Gate06C — Live role-evidence gate

This is a read-only PostgreSQL catalog/session audit plus versioned evidence output.

## Required before a restricted runtime rollout

1. Define runtime role/grants in an immutable new Flyway migration, **not** an ad-hoc psql role creation.
2. Keep privileged fixture provisioning on a dedicated admin connection. Set APP_T10_FIXTURE_ADMIN_REQUIRE_DEDICATED=true only after configuring that connection.
3. Prove the running Spring Boot connection identity with safe diagnostics (current_user, rolsuper, rolbypassrls) on its own DataSource, not the docker exec psql administrator session.
4. Test all critical modules with restricted runtime credentials in a controlled environment before switching production/local defaults.
5. Run negative cross-workspace RLS tests using a genuinely restricted login, including child table access and session cleanup.
6. Keep V63 immutable; do not add policies that trust only client-editable PostgreSQL GUCs.
7. Do not set APP_T10_REQUIRE_RESTRICTED_RUNTIME=true before the restricted role has all required permissions.

This report does **not** prove runtime separation or authorize V64 execution.