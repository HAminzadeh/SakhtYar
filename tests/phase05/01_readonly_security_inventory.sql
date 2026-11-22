-- Phase 05 audit only: SELECT statements, no role switching or data mutation.
select current_user as database_principal, session_user as session_principal,
       (select rolbypassrls from pg_roles where rolname=current_user) as bypass_rls,
       (select rolsuper from pg_roles where rolname=current_user) as superuser;
select t.tablename, c.relrowsecurity as rls_enabled, c.relforcerowsecurity as rls_forced
from pg_tables t join pg_class c on c.oid=to_regclass(format('%I.%I',t.schemaname,t.tablename))
where schemaname='public' and tablename in
('construction_case','property','property_owner','project_builder','builder_profile',
 'material_price_observation','cost_estimate','construction_scenario',
 'urban_evaluation','feasibility_assessment','financial_analysis',
 'financial_participation_allocation','sensitivity_analysis','sensitivity_point')
order by tablename;
select version,description,success from flyway_schema_history order by installed_rank desc limit 10;
