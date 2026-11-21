-- SAKHTYAR / PHASE 01 / READ-ONLY SHARED-DATABASE INSPECTION
-- Use with psql -X -v ON_ERROR_STOP=1 -f ... ; absolutely no writes.
-- This report is NOT proof of a separate non-BYPASSRLS authenticated TEST principal.
BEGIN READ ONLY;
SELECT current_database() AS database_name,
       current_user AS current_principal,
       session_user AS authenticated_principal,
       current_setting('row_security') AS row_security;
SELECT rolname, rolsuper, rolbypassrls, rolcreaterole, rolcreatedb, rolinherit
FROM pg_roles WHERE rolname IN (current_user, session_user);
SELECT rel.relname AS core_table,
       rel.relrowsecurity AS rls_enabled,
       rel.relforcerowsecurity AS rls_forced,
       EXISTS(SELECT 1 FROM pg_policies p WHERE p.schemaname='public'
          AND p.tablename=rel.relname AND p.policyname='sakhtyar_core_scope_policy') AS scope_policy,
       EXISTS(SELECT 1 FROM pg_trigger t WHERE t.tgrelid=rel.oid
          AND t.tgname='trg_sakhtyar_core_scope' AND NOT t.tgisinternal) AS scope_trigger
FROM pg_class rel JOIN pg_namespace ns ON ns.oid=rel.relnamespace
WHERE ns.nspname='public' AND rel.relname IN
    ('construction_case','property','property_owner','case_document')
ORDER BY rel.relname;
SELECT c.table_name,
       EXISTS(SELECT 1 FROM information_schema.columns col
         WHERE col.table_schema='public' AND col.table_name=c.table_name
           AND col.column_name='test_workspace_id') AS workspace_marker,
       EXISTS(SELECT 1 FROM pg_class t JOIN pg_namespace ns ON ns.oid=t.relnamespace
         WHERE ns.nspname='public' AND t.relname=c.table_name
           AND t.relrowsecurity AND t.relforcerowsecurity) AS forced_rls
FROM information_schema.columns c
WHERE c.table_schema='public' AND c.column_name='data_scope'
ORDER BY c.table_name;
SELECT rel.relname AS outbound_table,
       EXISTS(SELECT 1 FROM pg_trigger t WHERE t.tgrelid=rel.oid
          AND t.tgname='trg_sakhtyar_test_egress' AND NOT t.tgisinternal) AS egress_trigger
FROM pg_class rel JOIN pg_namespace ns ON ns.oid=rel.relnamespace
WHERE ns.nspname='public' AND rel.relname IN ('notification_outbox','crawl_job')
ORDER BY rel.relname;
ROLLBACK;
