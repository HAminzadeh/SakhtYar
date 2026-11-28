-- Read-only catalog audit. Run on the disposable migrated clone first.
\set ON_ERROR_STOP on
BEGIN READ ONLY;
SELECT version, description, success FROM public.flyway_schema_history WHERE version IN ('69','70','71','72','73') ORDER BY installed_rank;
SELECT c.relname,c.relrowsecurity,c.relforcerowsecurity,pg_get_userbyid(c.relowner) owner
 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public'
 AND c.relname IN ('cost_assembly','cost_assembly_component','cost_assembly_revision','cost_estimate','cost_estimate_line') ORDER BY c.relname;
SELECT c.relname,t.tgname,t.tgenabled,pg_get_triggerdef(t.oid) definition
 FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='public' AND t.tgname LIKE 'trg_phase19%' ORDER BY c.relname,t.tgname;
SELECT indexname,indexdef FROM pg_indexes WHERE schemaname='public' AND indexname LIKE '%phase19b%' ORDER BY indexname;
SELECT column_name,data_type,is_nullable FROM information_schema.columns
 WHERE table_schema='public' AND table_name='cost_estimate' AND column_name IN ('definition_revision','assembly_definition','request_key','recalculated_from_id');
SELECT current_user,session_user,rolsuper,rolbypassrls,rolcreaterole,rolcreatedb FROM pg_roles WHERE rolname=current_user;
SELECT has_table_privilege(current_user,'public.cost_estimate','SELECT') estimate_read,
 has_table_privilege(current_user,'public.cost_estimate','INSERT') estimate_insert,
 has_table_privilege(current_user,'public.cost_estimate','UPDATE') estimate_update,
 has_table_privilege(current_user,'public.cost_estimate','DELETE') estimate_delete;
ROLLBACK;
