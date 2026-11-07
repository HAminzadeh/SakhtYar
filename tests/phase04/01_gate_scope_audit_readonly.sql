-- SakhtYar Phase04: read-only catalog diagnostics; do not run as a DDL principal.
SELECT current_database() AS database_name, current_user AS db_principal,
       current_setting('app.sakhtyar_scope', true) AS declared_scope,
       pg_has_role(current_user,'pg_read_all_data','member') AS read_all_data_member;
SELECT version, description, success, installed_on
FROM public.flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;
SELECT n.nspname AS schema_name, c.relname AS table_name,
       c.relrowsecurity AS rls_enabled, c.relforcerowsecurity AS rls_forced
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='public' AND c.relkind='r' AND
      c.relname IN ('knowledge_dataset_package','knowledge_ai_import_item','knowledge_ocr_page_review',
                    'urban_rule','crawl_source','crawl_job','crawled_document','knowledge_term','knowledge_relation')
ORDER BY c.relname;
SELECT schemaname, tablename, policyname, cmd, roles, qual, with_check
FROM pg_policies WHERE schemaname='public' AND tablename IN
 ('knowledge_dataset_package','knowledge_ai_import_item','knowledge_ocr_page_review',
  'urban_rule','crawl_source','crawl_job','crawled_document')
ORDER BY tablename, policyname;
