-- READ ONLY, no business row content. Use the real restricted runtime role on a disposable clone.
BEGIN READ ONLY;
SELECT current_user AS effective_role,session_user AS login_role,rolsuper,rolbypassrls,rolcreaterole,rolcreatedb
 FROM pg_roles WHERE rolname=current_user;
SELECT c.relname,c.relrowsecurity AS rls,c.relforcerowsecurity AS forced,
       count(t.oid) FILTER(WHERE t.tgname LIKE 'trg_phase18b_%' AND t.tgenabled IN ('O','A')) AS active_v70_guards
 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace LEFT JOIN pg_trigger t ON t.tgrelid=c.oid AND NOT t.tgisinternal
 WHERE n.nspname='public' AND c.relname IN ('material_price_observation','material_price_aggregate','material_price_anomaly')
 GROUP BY c.relname,c.relrowsecurity,c.relforcerowsecurity ORDER BY c.relname;
SELECT tablename,indexname,indexdef FROM pg_indexes WHERE schemaname='public' AND indexname LIKE '%phase18b%' ORDER BY tablename,indexname;
SELECT algorithm_version,current_reference,count(*) AS count FROM public.material_price_aggregate GROUP BY algorithm_version,current_reference ORDER BY algorithm_version,current_reference;
SELECT count(*) FILTER(WHERE quote_key IS NULL) AS legacy_quotes,count(*) FILTER(WHERE quote_key IS NOT NULL) AS governed_quotes FROM public.material_price_observation;
ROLLBACK;
