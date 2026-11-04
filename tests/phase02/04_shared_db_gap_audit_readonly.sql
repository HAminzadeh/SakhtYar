-- Run with the authorized DBA session. Read-only catalog/provenance audit.
-- Do NOT mistake a tagged column for verified RLS or an independent LOGIN.
BEGIN READ ONLY;
WITH required(tab) AS (
 VALUES ('construction_case'),('property'),('property_owner'),('case_document'),
 ('construction_case_access'),('builder_profile'),('project_builder'),('contract_record'),
 ('urban_rule'),('urban_evaluation'),('feasibility_assessment'),('financial_analysis'),
 ('financial_participation_allocation'),('sensitivity_analysis'),('analysis_snapshot'),
 ('knowledge_source'),('knowledge_document'),('knowledge_legal_segment'),
 ('knowledge_pipeline_run'),('knowledge_ai_import_item'),('knowledge_rule_review'),
 ('knowledge_term'),('knowledge_relation'),('crawl_source'),('crawl_job'),
 ('crawled_document'),('notification_outbox'),('monitoring_event'),
 ('material_price_observation'),('cost_estimate'),('construction_scenario'),
 ('scenario_cost_snapshot'),('intelligence_workflow_execution')
)
SELECT r.tab AS table_name, c.oid IS NOT NULL AS exists_in_public,
       COALESCE(c.relrowsecurity,false) AS rls_enabled,
       COALESCE(c.relforcerowsecurity,false) AS force_rls,
       EXISTS(SELECT 1 FROM pg_attribute a WHERE a.attrelid=c.oid
           AND a.attname='data_scope' AND NOT a.attisdropped) AS has_scope_tag,
       EXISTS(SELECT 1 FROM pg_attribute a WHERE a.attrelid=c.oid
           AND a.attname='test_workspace_id' AND NOT a.attisdropped) AS has_workspace_tag,
       (SELECT count(*) FROM pg_policies p WHERE p.schemaname='public' AND p.tablename=r.tab
           AND p.permissive='RESTRICTIVE') AS restrictive_policy_count
FROM required r LEFT JOIN pg_class c ON c.oid=to_regclass('public.'||r.tab)
ORDER BY 1;
SELECT current_database() AS database_name,current_user AS login_role,
       r.rolsuper,r.rolbypassrls,r.rolcreaterole
FROM pg_roles r WHERE r.rolname=current_user;
SELECT version,description,success FROM flyway_schema_history
WHERE version IN ('58','60','61','62','63','64','65','66') ORDER BY installed_rank;
ROLLBACK;
