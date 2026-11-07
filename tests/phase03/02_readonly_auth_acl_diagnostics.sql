-- Phase03 additional SHARED PRIMARY DIAGNOSTICS ONLY. No user rows or tokens are selected.
-- Must be executed only when an operator explicitly requests read-only inspection.
BEGIN TRANSACTION READ ONLY;
SELECT current_database() database_name, session_user, current_user,
       current_setting('row_security') row_security;
SELECT n.nspname schema_name,c.relname table_name,c.relrowsecurity rls_enabled,
       c.relforcerowsecurity force_rls
FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relname IN
 ('app_user','auth_session','construction_case','construction_case_access','case_document')
ORDER BY c.relname;
SELECT relname, relhastriggers FROM pg_catalog.pg_class
WHERE oid IN (to_regclass('public.construction_case_access'),to_regclass('public.case_document'));
SELECT version,description,success FROM public.flyway_schema_history
WHERE version IN ('57','58','60','61','64','65','66') ORDER BY version;
ROLLBACK;
