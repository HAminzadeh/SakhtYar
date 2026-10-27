-- SakhtYar T08 – OPERATOR TEMPLATE ONLY. DO NOT RUN BLINDLY.
-- Execute as a DBA against a backed-up STAGING clone first.
-- Replace the database name, owner name and password placeholders carefully.
-- Never save real database passwords in Git or terminal history.
-- Prefer a secure role/password provisioning tool and protected credential store.
--
-- Example (run separately, after adapting placeholders):
-- CREATE ROLE sakhtyar_runtime WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '<RANDOM_SECRET_FROM_VAULT>';
-- GRANT CONNECT ON DATABASE <your_database> TO sakhtyar_runtime;
-- GRANT USAGE ON SCHEMA public TO sakhtyar_runtime;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO sakhtyar_runtime;
-- GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO sakhtyar_runtime;
-- ALTER DEFAULT PRIVILEGES FOR ROLE <actual_migration_object_owner> IN SCHEMA public
--   GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sakhtyar_runtime;
-- ALTER DEFAULT PRIVILEGES FOR ROLE <actual_migration_object_owner> IN SCHEMA public
--   GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO sakhtyar_runtime;
--
-- Use separate migration credentials for Flyway. Verify triggers, functions,
-- permissions and RLS behaviour with the real application role before switching prod.
-- The commands below are SAFE AND READ-ONLY.
SELECT current_user AS runtime_database_user, rolsuper, rolbypassrls
FROM pg_roles WHERE rolname=current_user;

SELECT n.nspname AS schema_name, c.relname AS table_name,
       c.relrowsecurity AS rls_enabled, c.relforcerowsecurity AS force_rls
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relname IN
 ('construction_case','property','property_owner','case_document')
ORDER BY c.relname;

SELECT version,success,description FROM flyway_schema_history
WHERE version IN ('58','59','60','61','62') ORDER BY installed_rank;
