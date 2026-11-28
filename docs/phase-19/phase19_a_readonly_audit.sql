-- Read-only catalog evidence. Run on disposable clone; no DML or grants.
\set ON_ERROR_STOP on
BEGIN READ ONLY;
SELECT current_database(),current_user,session_user;
SELECT version,description,success FROM public.flyway_schema_history WHERE version IN ('69','70','71','72') ORDER BY installed_rank;
SELECT c.relname,c.relrowsecurity,c.relforcerowsecurity,pg_get_userbyid(c.relowner) AS owner
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relname IN ('cost_assembly','cost_assembly_component','cost_assembly_revision','cost_estimate','cost_estimate_line') ORDER BY c.relname;
SELECT tablename,policyname,permissive,cmd,qual,with_check FROM pg_policies WHERE schemaname='public' AND tablename IN ('cost_assembly','cost_assembly_component','cost_assembly_revision','cost_estimate','cost_estimate_line') ORDER BY tablename,policyname;
SELECT c.relname,t.tgname,t.tgenabled,pg_get_triggerdef(t.oid) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid WHERE NOT t.tgisinternal AND t.tgname LIKE 'trg_phase19a_%' ORDER BY c.relname,t.tgname;
SELECT table_name,column_name,data_type,is_nullable FROM information_schema.columns WHERE table_schema='public' AND ((table_name='cost_assembly' AND column_name='revision') OR (table_name='cost_estimate' AND column_name IN ('definition_revision','assembly_definition')) OR table_name='cost_assembly_revision') ORDER BY table_name,ordinal_position;
SELECT current_user,has_table_privilege(current_user,'public.cost_assembly_revision','SELECT') AS can_read,has_table_privilege(current_user,'public.cost_assembly_revision','INSERT') AS can_archive,has_table_privilege(current_user,'public.cost_assembly_revision','UPDATE') AS can_update,has_table_privilege(current_user,'public.cost_assembly_revision','DELETE') AS can_delete,has_table_privilege(current_user,'public.cost_assembly_revision','TRUNCATE') AS can_truncate;
ROLLBACK;
