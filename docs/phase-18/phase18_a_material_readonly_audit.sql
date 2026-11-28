-- READ ONLY. Run as the actual application database principal; no business data is returned.
BEGIN READ ONLY;
SELECT current_user AS effective_role, session_user AS login_role,
       rolsuper, rolbypassrls, rolcreaterole, rolcreatedb
FROM pg_roles WHERE rolname=current_user;
SELECT c.relname, c.relrowsecurity AS rls, c.relforcerowsecurity AS forced,
       pg_get_userbyid(c.relowner) AS owner,
       EXISTS(SELECT 1 FROM pg_policies p WHERE p.schemaname='public' AND p.tablename=c.relname AND p.policyname='phase18_material_allow') AS allow_policy,
       EXISTS(SELECT 1 FROM pg_policies p WHERE p.schemaname='public' AND p.tablename=c.relname AND p.policyname='phase18_material_limit' AND p.permissive='RESTRICTIVE') AS restrictive_policy,
       EXISTS(SELECT 1 FROM pg_trigger t WHERE t.tgrelid=c.oid AND t.tgname='trg_phase18_material_guard' AND t.tgenabled IN ('O','A')) AS guard_trigger
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relname IN ('material_item','material_variant','material_price_observation','material_price_aggregate','material_price_anomaly') ORDER BY c.relname;
SELECT schemaname,tablename,policyname,permissive,roles,qual,with_check FROM pg_policies
WHERE schemaname='public' AND tablename LIKE 'material_%' ORDER BY tablename,policyname;
SELECT table_name,column_name,data_type,character_maximum_length,numeric_precision,numeric_scale
FROM information_schema.columns WHERE table_schema='public' AND table_name IN ('material_price_observation','material_price_aggregate')
AND column_name IN ('currency_code','currency_id','amount','quantity_basis','confidence') ORDER BY table_name,column_name;
SELECT schemaname,tablename,indexname,indexdef FROM pg_indexes WHERE schemaname='public' AND tablename='material_item';
SELECT public.sakhtyar_material_scope_ok('REAL',NULL) AS real_allowed_in_current_context,
       public.sakhtyar_material_scope_ok('TEST','00000000-0000-0000-0000-000000000001'::uuid) AS arbitrary_test_allowed;
ROLLBACK;
