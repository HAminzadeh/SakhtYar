-- P02: negative-security readiness GATE, READ ONLY, in existing shared database.
-- FALSE here means BLOCKED. TRUE is structural evidence ONLY, not full E2E proof.
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '15s';
WITH base AS (
  SELECT c.oid, c.relname, c.relrowsecurity, c.relforcerowsecurity
  FROM pg_catalog.pg_class c
  JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
  WHERE n.nspname = 'public' AND c.relkind IN ('r','p')
), core AS (
  SELECT count(*) = 4 AS complete
  FROM base b
  WHERE b.relname IN ('construction_case','property','property_owner','case_document')
    AND b.relrowsecurity AND b.relforcerowsecurity
    AND EXISTS(SELECT 1 FROM pg_catalog.pg_policies p WHERE p.schemaname='public'
      AND p.tablename=b.relname AND p.policyname='sakhtyar_core_scope_policy')
    AND EXISTS(SELECT 1 FROM pg_catalog.pg_trigger t WHERE t.tgrelid=b.oid
      AND t.tgname='trg_sakhtyar_core_scope' AND t.tgenabled<>'D')
), external AS (
  SELECT count(*) = 2 AS complete
  FROM base b
  WHERE b.relname IN ('notification_outbox','crawl_job')
    AND EXISTS(SELECT 1 FROM pg_catalog.pg_trigger t WHERE t.tgrelid=b.oid
      AND t.tgname='trg_sakhtyar_test_egress' AND t.tgenabled<>'D')
), principal AS (
  SELECT count(*) = 1 AS restricted
  FROM pg_catalog.pg_roles r
  WHERE r.rolname = current_user AND r.rolcanlogin AND NOT r.rolsuper
    AND NOT r.rolbypassrls AND NOT r.rolcreaterole AND NOT r.rolcreatedb
    AND current_user = session_user AND current_setting('row_security') = 'on'
)
SELECT 'OBSERVATION_ONLY_NOT_WRITE_AUTHORIZATION' AS report_kind,
       (SELECT complete FROM core) AS four_core_rls_structural,
       (SELECT complete FROM external) AS two_outbound_queue_guards,
       (SELECT restricted FROM principal) AS runtime_principal_restricted,
       FALSE AS full_application_test_real_isolation_proven,
       FALSE AS safe_to_execute_business_e2e_writes,
       FALSE AS authenticated_cross_workspace_negative_proof_obtained;
ROLLBACK;
