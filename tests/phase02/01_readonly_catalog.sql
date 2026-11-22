-- READ-ONLY schema inventory. Run in the approved database. NEVER treat RLS presence as full safety proof.
BEGIN READ ONLY;
WITH referenced_tables AS (
    SELECT unnest(ARRAY[
      'app_user','auth_session','construction_case','construction_case_access',
      'knowledge_dataset_package','urban_rule','flyway_schema_history','knowledge_document',
      'knowledge_pipeline_run','knowledge_pipeline_checkpoint','knowledge_legal_segment',
      'knowledge_ocr_page_review','knowledge_ai_import_item','knowledge_ai_import_review_event',
      'knowledge_rule_review','knowledge_rule_review_event','knowledge_term','knowledge_relation',
      'crawl_source','crawl_job','crawled_document','property','property_owner','case_document',
      'builder_profile','project_builder','material_price_observation','cost_estimate',
      'construction_scenario','scenario_cost_snapshot','urban_evaluation','feasibility_assessment',
      'financial_analysis','financial_participation_allocation','sensitivity_analysis',
      'analysis_snapshot','intelligence_workflow_execution','monitoring_event','notification_outbox',
      'platform_config_version','platform_config_audit'
    ]) AS table_name
), metadata AS (
    SELECT rt.table_name, c.oid, c.relrowsecurity, c.relforcerowsecurity, c.relowner
    FROM referenced_tables rt
    LEFT JOIN pg_catalog.pg_class c ON c.oid=to_regclass('public.'||rt.table_name)
)
SELECT m.table_name, m.oid IS NOT NULL AS exists_in_public,
       COALESCE(m.relrowsecurity,false) AS rls_enabled,
       COALESCE(m.relforcerowsecurity,false) AS force_rls,
       CASE WHEN m.oid IS NULL THEN NULL ELSE pg_get_userbyid(m.relowner) END AS table_owner,
       COALESCE((SELECT count(*) FROM pg_catalog.pg_constraint x
         WHERE x.conrelid=m.oid AND x.contype='p'),0) AS pk_count,
       COALESCE((SELECT count(*) FROM pg_catalog.pg_constraint x
         WHERE x.conrelid=m.oid AND x.contype='f'),0) AS fk_count,
       EXISTS(SELECT 1 FROM information_schema.columns c
         WHERE c.table_schema='public' AND c.table_name=m.table_name AND c.column_name='data_scope') AS has_data_scope,
       EXISTS(SELECT 1 FROM information_schema.columns c
         WHERE c.table_schema='public' AND c.table_name=m.table_name AND c.column_name='test_workspace_id') AS has_test_workspace_id
FROM metadata m ORDER BY m.table_name;
ROLLBACK;
