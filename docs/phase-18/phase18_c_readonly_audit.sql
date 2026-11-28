-- Only on a disposable clone, using the real restricted runtime login. No rows exported.
BEGIN READ ONLY;
SELECT current_user AS effective_role,session_user AS login_role,rolsuper,rolbypassrls,rolcreaterole,rolcreatedb FROM pg_roles WHERE rolname=current_user;
SELECT c.relname,c.relrowsecurity,c.relforcerowsecurity,
       EXISTS(SELECT 1 FROM pg_policy p WHERE p.polrelid=c.oid AND p.polname='phase18c_cost_limit' AND NOT p.polpermissive) AS restrictive_policy,
       EXISTS(SELECT 1 FROM pg_trigger t WHERE t.tgrelid=c.oid AND t.tgname='trg_phase18c_cost_scope' AND t.tgenabled IN ('O','A')) AS scope_guard,
       EXISTS(SELECT 1 FROM pg_trigger t WHERE t.tgrelid=c.oid AND t.tgname='trg_phase18c_cost_seal' AND t.tgdeferrable AND t.tginitdeferred) AS deferred_seal,
       has_table_privilege(current_user,c.oid,'TRUNCATE') AS truncate_allowed
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relname IN ('cost_assembly','cost_assembly_component','cost_estimate','cost_estimate_line','quality_package','quality_package_selection','construction_scenario','construction_scenario_item','scenario_cost_snapshot','scenario_cost_snapshot_line','feasibility_assessment','feasibility_reason','financial_analysis','financial_participation_allocation') ORDER BY c.relname;
SELECT table_name,column_name,character_maximum_length FROM information_schema.columns WHERE table_schema='public' AND table_name='cost_estimate' AND column_name='currency_code';
ROLLBACK;
