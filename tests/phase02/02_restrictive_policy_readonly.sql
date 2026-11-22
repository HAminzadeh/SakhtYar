-- Inspect effective per-workspace restrictive policies; do not infer independent-login proof.
BEGIN READ ONLY;
SELECT p.schemaname,p.tablename,p.policyname,p.permissive,p.roles,p.cmd,p.qual,p.with_check
FROM pg_catalog.pg_policies p
WHERE p.schemaname='public'
  AND p.tablename IN ('construction_case','property','property_owner','case_document','simulation_core_fixture')
ORDER BY p.tablename,p.policyname;
SELECT rolname,rolcanlogin,rolsuper,rolbypassrls,rolcreaterole,rolinherit
FROM pg_catalog.pg_roles WHERE rolname LIKE 'sakhtyar_tws_%' ORDER BY rolname;
ROLLBACK;
