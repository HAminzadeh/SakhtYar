-- Gate06-E / READ-ONLY SQL inventory. Never store raw secrets in output.
SELECT current_database() AS db, current_user AS principal, current_setting('server_version') AS version;
SELECT rolname, rolcanlogin, rolsuper, rolbypassrls, rolcreaterole, rolcreatedb, rolinherit
FROM pg_catalog.pg_roles
WHERE rolname IN ('sakhtyar','sakhtyar_runtime','sakhtyar_flyway','sakhtyar_fixture_admin') ORDER BY rolname;
SELECT role.rolname AS granted_role, member.rolname AS member_role, membership.admin_option
FROM pg_catalog.pg_auth_members membership
JOIN pg_catalog.pg_roles role ON role.oid=membership.roleid
JOIN pg_catalog.pg_roles member ON member.oid=membership.member
WHERE role.rolname LIKE 'sakhtyar%' OR member.rolname LIKE 'sakhtyar%' ORDER BY 1,2;
SELECT n.nspname AS schema_name, c.relname AS object_name, c.relkind,
 pg_get_userbyid(c.relowner) AS owner_name, c.relrowsecurity AS rls_enabled,
 c.relforcerowsecurity AS rls_forced
FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relkind IN ('r','p','S') ORDER BY c.relkind,c.relname;
SELECT grantee, table_name, privilege_type FROM information_schema.role_table_grants
WHERE table_schema='public' AND grantee IN ('sakhtyar_runtime','sakhtyar_flyway','sakhtyar_fixture_admin','PUBLIC')
ORDER BY grantee,table_name,privilege_type;
SELECT schemaname, tablename, policyname, roles, cmd, qual, with_check
FROM pg_catalog.pg_policies WHERE schemaname='public'
AND tablename IN ('t10_e2e_run','t10_e2e_scenario_run','t10_e2e_step') ORDER BY tablename,policyname;
SELECT version, description, success FROM public.flyway_schema_history
WHERE version IN ('63','64') ORDER BY version;
