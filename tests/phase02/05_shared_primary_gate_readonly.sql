-- Phase 02 shared-primary PostgreSQL evidence. READ ONLY, no role creation, grants,
-- RLS changes, migrations, fixture writes or cleanup. Does not itself prove isolation.
-- Run ONLY when a DBA explicitly authorizes metadata inspection; redact role names in logs.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;

-- Physical principal and dangerous capabilities. These facts alone are insufficient for PASS.
SELECT current_database() AS database_name,
       current_user AS current_role, session_user AS physical_login,
       current_setting('row_security') AS row_security_mode,
       r.rolcanlogin, r.rolsuper, r.rolbypassrls, r.rolcreaterole,
       r.rolcreatedb, r.rolinherit, r.rolreplication,
       (SELECT count(*) FROM pg_catalog.pg_auth_members m WHERE m.member=r.oid) AS membership_count
FROM pg_catalog.pg_roles r WHERE r.rolname=current_user;

-- Candidate data classification only; tables without an explicit tag require additional
-- independent evidence, not optimistic inference. No sensitive business rows selected.
WITH classified(name,threat_class) AS (
 VALUES
 ('app_user','GLOBAL_AUTH'),('auth_session','GLOBAL_AUTH'),
 ('construction_case','TEST_TAGGED'),('property','TEST_TAGGED'),
 ('property_owner','TEST_TAGGED'),('case_document','TEST_TAGGED'),
 ('construction_case_access','UNVERIFIED'),('builder_profile','UNVERIFIED'),
 ('project_builder','UNVERIFIED'),('knowledge_dataset_package','UNVERIFIED'),
 ('knowledge_document','UNVERIFIED'),('knowledge_pipeline_run','UNVERIFIED'),
 ('knowledge_pipeline_checkpoint','UNVERIFIED'),('knowledge_legal_segment','UNVERIFIED'),
 ('knowledge_ocr_page_review','UNVERIFIED'),('knowledge_ai_import_item','UNVERIFIED'),
 ('knowledge_ai_import_review_event','UNVERIFIED'),('knowledge_rule_review','UNVERIFIED'),
 ('knowledge_rule_review_event','UNVERIFIED'),('knowledge_term','SHARED_REFERENCE_CANDIDATE'),
 ('knowledge_relation','SHARED_REFERENCE_CANDIDATE'),('urban_rule','SHARED_REFERENCE_CANDIDATE'),
 ('urban_evaluation','UNVERIFIED'),('feasibility_assessment','UNVERIFIED'),
 ('crawl_source','UNVERIFIED'),('crawl_job','OUTBOX'),('crawled_document','UNVERIFIED'),
 ('material_price_observation','UNVERIFIED'),('cost_estimate','UNVERIFIED'),
 ('construction_scenario','UNVERIFIED'),('scenario_cost_snapshot','UNVERIFIED'),
 ('financial_analysis','UNVERIFIED'),('financial_participation_allocation','UNVERIFIED'),
 ('sensitivity_analysis','UNVERIFIED'),('analysis_snapshot','UNVERIFIED'),
 ('intelligence_workflow_execution','UNVERIFIED'),('monitoring_event','UNVERIFIED'),
 ('notification_outbox','OUTBOX'),('platform_config_version','REAL_ONLY_CANDIDATE'),
 ('platform_config_audit','REAL_ONLY_CANDIDATE'),('simulation_fixture_contract_ledger','TEST_TAGGED')
)
SELECT x.name,x.threat_class,c.oid IS NOT NULL AS exists_in_public,
       COALESCE(c.relrowsecurity,false) AS rls_on,
       COALESCE(c.relforcerowsecurity,false) AS force_rls,
       EXISTS(SELECT 1 FROM pg_catalog.pg_attribute a WHERE a.attrelid=c.oid AND
              a.attname='data_scope' AND NOT a.attisdropped) AS has_data_scope,
       EXISTS(SELECT 1 FROM pg_catalog.pg_attribute a WHERE a.attrelid=c.oid AND
              a.attname='test_workspace_id' AND NOT a.attisdropped) AS has_workspace_id,
       (SELECT count(*) FROM pg_catalog.pg_policies p WHERE p.schemaname='public' AND p.tablename=x.name)
           AS policy_count,
       (SELECT count(*) FROM pg_catalog.pg_trigger t WHERE t.tgrelid=c.oid AND NOT t.tgisinternal)
           AS user_trigger_count,
       (SELECT count(*) FROM pg_catalog.pg_constraint fk WHERE fk.conrelid=c.oid AND fk.contype='f')
           AS foreign_key_count
FROM classified x LEFT JOIN pg_catalog.pg_class c ON c.oid=to_regclass('public.'||x.name)
ORDER BY threat_class,name;

-- Existing physical TEST accounts: catalog only, never alter credentials or grants.
SELECT rolname,rolcanlogin,rolsuper,rolbypassrls,rolcreaterole,rolcreatedb,
       rolinherit,rolreplication,
       (SELECT count(*) FROM pg_catalog.pg_auth_members m WHERE m.member=r.oid) AS membership_count
FROM pg_catalog.pg_roles r WHERE rolname LIKE 'sakhtyar_tws_%' ORDER BY rolname;

-- Existing Flyway history verification (no migrations executed).
SELECT version,description,success FROM public.flyway_schema_history
WHERE version IN ('58','59','60','61','62','63','64','65','66') ORDER BY installed_rank;

-- Metadata integrity and outward-effect trigger inventory.
SELECT c.relname,t.tgname,t.tgenabled,pg_get_triggerdef(t.oid) AS definition
FROM pg_catalog.pg_trigger t JOIN pg_catalog.pg_class c ON c.oid=t.tgrelid
WHERE c.relnamespace='public'::regnamespace AND c.relname IN
('construction_case','property','property_owner','case_document',
 'notification_outbox','crawl_job','simulation_fixture_contract_ledger')
AND NOT t.tgisinternal ORDER BY c.relname,t.tgname;

ROLLBACK;
