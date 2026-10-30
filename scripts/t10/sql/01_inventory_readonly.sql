-- T10 read-only inventory; run only under explicitly authorized read-only transaction.
BEGIN TRANSACTION READ ONLY;
SELECT current_database() AS database_name, current_user AS role_name,
       (SELECT rolsuper FROM pg_roles WHERE rolname=current_user) AS is_superuser,
       (SELECT rolbypassrls FROM pg_roles WHERE rolname=current_user) AS bypass_rls;
SELECT n.nspname AS schema_name, c.relname AS table_name,
       c.relrowsecurity AS rls_enabled, c.relforcerowsecurity AS force_rls,
       EXISTS(SELECT 1 FROM information_schema.columns col
              WHERE col.table_schema=n.nspname AND col.table_name=c.relname
                AND col.column_name='data_scope') AS has_data_scope,
       EXISTS(SELECT 1 FROM information_schema.columns col
              WHERE col.table_schema=n.nspname AND col.table_name=c.relname
                AND col.column_name='test_workspace_id') AS has_test_workspace_id
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE c.relkind='r' AND n.nspname='public'
ORDER BY c.relname;
SELECT schemaname, tablename, policyname, permissive, roles, cmd, qual, with_check
FROM pg_policies WHERE schemaname='public' ORDER BY tablename, policyname;
SELECT installed_rank, version, description, success
FROM public.flyway_schema_history ORDER BY installed_rank;
COMMIT;
