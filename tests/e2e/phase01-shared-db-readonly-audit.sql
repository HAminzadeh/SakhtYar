-- SakhtYar Phase 01: diagnostic inspection against the existing shared PostgreSQL.
-- This SQL contains no writes, no ALTER/GRANT/ROLE operations and no fixture/cleanup.
-- It is NOT isolation proof. A separate restricted TEST login and audited negative
-- access checks are necessary before enabling any state-changing business E2E.
BEGIN TRANSACTION READ ONLY;
SELECT current_database() AS database_name,
       session_user AS actual_login,
       current_user AS effective_user,
       current_setting('transaction_read_only') AS tx_is_read_only;
SELECT rolname, rolcanlogin, rolsuper, rolbypassrls, rolcreaterole, rolinherit
FROM pg_catalog.pg_roles WHERE rolname = current_user;
SELECT n.nspname AS schema_name, c.relname AS table_name,
       c.relrowsecurity AS rls_enabled,
       c.relforcerowsecurity AS force_rls
FROM pg_catalog.pg_class c
JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind IN ('r','p')
  AND c.relname IN (
     'simulation_workspace', 'simulation_e2e_run', 'simulation_e2e_result',
     'simulation_e2e_step', 'construction_case', 'property',
     'property_owner', 'case_document', 'app_user', 'auth_session'
  )
ORDER BY c.relname;
SELECT schemaname, tablename, policyname, cmd, roles
FROM pg_catalog.pg_policies
WHERE schemaname = 'public' AND tablename IN (
   'construction_case','property','property_owner','case_document','simulation_workspace'
)
ORDER BY tablename, policyname;
SELECT version, description, success
FROM public.flyway_schema_history
WHERE version IN ('62','63','64','65','66')
ORDER BY installed_rank;
ROLLBACK;
