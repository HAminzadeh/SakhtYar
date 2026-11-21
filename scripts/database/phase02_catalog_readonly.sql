-- SakhtYar / Area 02 / catalog inspection on the EXISTING shared PostgreSQL.
-- Read-only: no provisioning, no DML, no ALTER/CREATE/DROP, no REAL business reads.
-- psql -X -v ON_ERROR_STOP=1 -f scripts/database/phase02_catalog_readonly.sql
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '15s';

-- 1. Physical principal properties. This does not grant permission or prove workspace isolation.
SELECT current_database() AS database_name,
       current_user AS runtime_current_user,
       session_user AS authenticated_session_user,
       current_setting('row_security') AS row_security_setting,
       r.rolsuper, r.rolbypassrls, r.rolcreaterole, r.rolcreatedb,
       r.rolinherit, r.rolcanlogin
FROM pg_catalog.pg_roles r WHERE r.rolname = current_user;

-- 2. All public base tables: scope markers, row security, and source-of-truth triggers.
WITH base AS (
 SELECT c.oid, c.relname AS table_name, c.relrowsecurity, c.relforcerowsecurity,
        EXISTS (SELECT 1 FROM pg_catalog.pg_attribute a
                WHERE a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
                  AND a.attname='data_scope') AS has_scope,
        EXISTS (SELECT 1 FROM pg_catalog.pg_attribute a
                WHERE a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
                  AND a.attname='test_workspace_id') AS has_workspace
 FROM pg_catalog.pg_class c
 JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='public' AND c.relkind IN ('r','p')
)
SELECT b.table_name,
       CASE WHEN b.table_name IN ('construction_case','property','property_owner','case_document')
              THEN 'CASE_DOMAIN_CORE'
            WHEN b.table_name LIKE 'simulation_%' OR b.table_name LIKE 't10_%'
              THEN 'TEST_WORKSPACE_METADATA'
            WHEN b.table_name LIKE 'global_%' OR b.table_name IN ('app_user','auth_session')
              THEN 'GLOBAL_OR_IDENTITY'
            WHEN b.has_scope THEN 'SCOPE_TAGGED_REVIEW_REQUIRED'
            ELSE 'UNCLASSIFIED_REVIEW_REQUIRED' END AS preliminary_class,
       b.has_scope, b.has_workspace, b.relrowsecurity AS rls_enabled,
       b.relforcerowsecurity AS rls_forced,
       EXISTS(SELECT 1 FROM pg_catalog.pg_policies p
              WHERE p.schemaname='public' AND p.tablename=b.table_name
                AND p.policyname='sakhtyar_core_scope_policy') AS core_scope_policy,
       EXISTS(SELECT 1 FROM pg_catalog.pg_trigger t WHERE t.tgrelid=b.oid
              AND t.tgname='trg_sakhtyar_core_scope' AND NOT t.tgisinternal
              AND t.tgenabled<>'D') AS core_trigger,
       EXISTS(SELECT 1 FROM pg_catalog.pg_trigger t WHERE t.tgrelid=b.oid
              AND t.tgname='trg_sakhtyar_test_egress' AND NOT t.tgisinternal
              AND t.tgenabled<>'D') AS external_queue_trigger
FROM base b ORDER BY preliminary_class, b.table_name;

-- 3. Policy bindings (no business row contents).
SELECT schemaname, tablename, policyname, permissive, roles, cmd, qual, with_check
FROM pg_catalog.pg_policies WHERE schemaname='public' ORDER BY tablename, policyname;

-- 4. Foreign keys and referenced tables: discover cross-scope edges without joining business rows.
SELECT c.conrelid::regclass::text AS source_table,
       c.confrelid::regclass::text AS target_table,
       c.conname AS constraint_name, c.convalidated AS validated
FROM pg_catalog.pg_constraint c
WHERE c.contype='f' AND c.connamespace='public'::regnamespace
ORDER BY source_table, constraint_name;

-- 5. Index definitions for the four CORE tables and two outbound queues.
SELECT schemaname, tablename, indexname, indexdef
FROM pg_catalog.pg_indexes
WHERE schemaname='public' AND tablename IN
 ('construction_case','property','property_owner','case_document',
  'notification_outbox','crawl_job','simulation_fixture_contract_ledger')
ORDER BY tablename,indexname;

-- 6. Existing grants: PUBLIC and runtime principal; report privileges, not secret values.
SELECT c.relname AS table_name,
       has_table_privilege(current_user, c.oid, 'SELECT') AS can_select,
       has_table_privilege(current_user, c.oid, 'INSERT') AS can_insert,
       has_table_privilege(current_user, c.oid, 'UPDATE') AS can_update,
       has_table_privilege(current_user, c.oid, 'DELETE') AS can_delete,
       EXISTS(SELECT 1 FROM pg_catalog.aclexplode(COALESCE(c.relacl, pg_catalog.acldefault('r', c.relowner))) a
              WHERE a.grantee=0 AND a.privilege_type='SELECT') AS public_select,
       EXISTS(SELECT 1 FROM pg_catalog.aclexplode(COALESCE(c.relacl, pg_catalog.acldefault('r', c.relowner))) a
              WHERE a.grantee=0 AND a.privilege_type='INSERT') AS public_insert
FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relkind IN ('r','p')
AND (c.relname IN ('construction_case','property','property_owner','case_document',
                   'simulation_core_fixture','simulation_fixture_contract_ledger',
                   'notification_outbox','crawl_job')
     OR c.relname LIKE 't10_%')
ORDER BY table_name;

-- 7. Flyway metadata existence only. Do NOT assume it is safe to migrate.
SELECT to_regclass('public.flyway_schema_history') IS NOT NULL AS flyway_history_exists,
       to_regclass('public.simulation_fixture_contract_ledger') IS NOT NULL AS v65_ledger_exists;
ROLLBACK;
