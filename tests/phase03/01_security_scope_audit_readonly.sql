-- Phase03: READ-ONLY diagnostics on the shared primary database.
-- No role grants, fixture creation, writes, or migrations.
BEGIN READ ONLY;
SELECT current_database() AS database_name, current_user AS login_role,
       session_user AS session_role,
       current_setting('server_version') AS pg_version;
SELECT rolname, rolcanlogin, rolsuper, rolbypassrls, rolcreaterole
FROM pg_catalog.pg_roles WHERE rolname=current_user OR rolname LIKE 'sakhtyar_tws_%'
ORDER BY rolname;
WITH t AS (
 SELECT unnest(ARRAY['construction_case','property','property_owner','case_document',
    'construction_case_access','app_user','auth_session','knowledge_dataset_package',
    'simulation_fixture_contract_ledger']) AS table_name
)
SELECT t.table_name, to_regclass('public.'||t.table_name) IS NOT NULL AS exists,
  coalesce(c.relrowsecurity,false) AS rls_enabled,
  coalesce(c.relforcerowsecurity,false) AS force_rls,
  EXISTS(SELECT 1 FROM information_schema.columns col WHERE col.table_schema='public' AND
     col.table_name=t.table_name AND col.column_name='data_scope') AS has_data_scope,
  EXISTS(SELECT 1 FROM information_schema.columns col WHERE col.table_schema='public' AND
     col.table_name=t.table_name AND col.column_name='test_workspace_id') AS has_test_workspace_id
FROM t LEFT JOIN pg_catalog.pg_class c ON c.oid=to_regclass('public.'||t.table_name)
ORDER BY t.table_name;
SELECT version,description,success,installed_on FROM flyway_schema_history
WHERE version IN ('64','65','66') ORDER BY installed_rank;
ROLLBACK;
