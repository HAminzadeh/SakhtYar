-- T06 Read-only operator diagnostics. Use with a restricted application DB connection.
SELECT current_user AS runtime_db_role, r.rolsuper, r.rolbypassrls
FROM pg_roles r WHERE r.rolname=current_user;

SELECT n.nspname, c.relname AS core_table, c.relrowsecurity AS rls_enabled,
       c.relforcerowsecurity AS rls_forced
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public'
  AND c.relname IN ('construction_case','property','property_owner','case_document')
ORDER BY c.relname;

SELECT version,description,success FROM flyway_schema_history
WHERE version IN ('58','59','60','61','62') ORDER BY installed_rank;

SELECT state,count(*) AS runs FROM simulation_e2e_run GROUP BY state;
SELECT scenario_id,state,count(*) AS results
FROM simulation_e2e_result GROUP BY scenario_id,state ORDER BY scenario_id,state;
