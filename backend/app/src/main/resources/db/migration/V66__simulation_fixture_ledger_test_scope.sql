-- SakhtYar Phase02: shared primary PostgreSQL, exclusively TEST-tagged fixture ledger.
-- ADDITIVE migration to existing V65; no business/REAL row is inserted, updated, or deleted.
-- Existing V65 script and Flyway history MUST remain immutable.

DO $preflight$
DECLARE previous text;
BEGIN
  IF to_regclass('public.simulation_fixture_contract_ledger') IS NULL THEN
    RAISE EXCEPTION 'V65 fixture ledger must be applied before V66';
  END IF;
  SELECT pg_get_constraintdef(c.oid) INTO previous
    FROM pg_constraint c
    WHERE c.conrelid='public.simulation_fixture_contract_ledger'::regclass
      AND c.conname='simulation_fixture_contract_ledger_isolation_mode_check';
  IF previous IS NULL OR position('CLONE_REQUIRED' IN previous)=0
       OR position('LIMITED_CORE_RESTRICTED_LOGIN' IN previous)=0 THEN
    RAISE EXCEPTION 'Unexpected V65 isolation mode constraint; refusing to modify it';
  END IF;
END $preflight$;

ALTER TABLE public.simulation_fixture_contract_ledger
  ADD COLUMN data_scope varchar(8) NOT NULL DEFAULT 'TEST';
ALTER TABLE public.simulation_fixture_contract_ledger
  ADD CONSTRAINT chk_sim_fixture_ledger_test_only CHECK (data_scope='TEST');

-- Only the TEST-only metadata ledger's check is expanded; no existing domain RLS/policy is changed.
ALTER TABLE public.simulation_fixture_contract_ledger
  DROP CONSTRAINT simulation_fixture_contract_ledger_isolation_mode_check;
ALTER TABLE public.simulation_fixture_contract_ledger
  ADD CONSTRAINT simulation_fixture_contract_ledger_isolation_mode_check
  CHECK (isolation_mode IN
     ('CLONE_REQUIRED','LIMITED_CORE_RESTRICTED_LOGIN','SHARED_DB_TEST_GATED'));

-- Enforce the TEST tag and preserve identity even if a future writer is added.
CREATE OR REPLACE FUNCTION public.sakhtyar_phase02_fixture_ledger_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE actual_workspace uuid;
BEGIN
  IF NEW.data_scope IS DISTINCT FROM 'TEST' THEN
    RAISE EXCEPTION 'Only TEST-tagged fixture ledger entries are allowed' USING ERRCODE='23514';
  END IF;
  SELECT workspace_id INTO actual_workspace FROM public.simulation_e2e_run WHERE id=NEW.run_id;
  IF actual_workspace IS NULL OR actual_workspace IS DISTINCT FROM NEW.workspace_id THEN
    RAISE EXCEPTION 'Phase02 fixture ledger workspace mismatch' USING ERRCODE='23514';
  END IF;
  IF TG_OP='UPDATE' AND (
     OLD.run_id IS DISTINCT FROM NEW.run_id OR
     OLD.scenario_id IS DISTINCT FROM NEW.scenario_id OR
     OLD.workspace_id IS DISTINCT FROM NEW.workspace_id OR
     OLD.deterministic_fixture_id IS DISTINCT FROM NEW.deterministic_fixture_id OR
     OLD.data_scope IS DISTINCT FROM NEW.data_scope) THEN
    RAISE EXCEPTION 'Phase02 fixture identity or TEST tag is immutable' USING ERRCODE='42501';
  END IF;
  RETURN NEW;
END $$;
COMMENT ON COLUMN public.simulation_fixture_contract_ledger.data_scope IS
'Always TEST: separate tagged fixture bookkeeping on the shared primary PostgreSQL database';

-- Harden V61 for the shared database: never accept a TEST session that omits the
-- TEST tag from a queue row, because the V58 default is REAL.
-- This is a defense-in-depth gate; session GUCs are NOT an authentication boundary.
CREATE OR REPLACE FUNCTION public.sakhtyar_block_test_external_queue()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE active_scope text;
BEGIN
  active_scope := NULLIF(current_setting('app.sakhtyar_scope', true), '');
  IF NEW.data_scope = 'TEST' OR active_scope = 'TEST' THEN
    RAISE EXCEPTION 'TEST network-effect queue denied: %', TG_TABLE_NAME
      USING ERRCODE='42501';
  END IF;
  IF active_scope IS NOT NULL AND active_scope <> 'REAL' THEN
    RAISE EXCEPTION 'Unknown application scope denied' USING ERRCODE='42501';
  END IF;
  RETURN NEW;
END $$;

-- Ensure that the V61 triggers exist for tables created before/after that migration.
-- If the table exists without V58 provenance, abort safely: no silent fallback.
DO $egress$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['notification_outbox','crawl_job'] LOOP
    IF to_regclass('public.'||t) IS NOT NULL THEN
      IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema='public' AND table_name=t AND column_name='data_scope') THEN
        RAISE EXCEPTION 'Phase02 safety gate: % lacks data_scope; migration refused',t;
      END IF;
      IF NOT EXISTS (SELECT 1 FROM pg_trigger tr
        WHERE tr.tgrelid=to_regclass('public.'||t)
          AND tr.tgname='trg_sakhtyar_test_egress' AND NOT tr.tgisinternal) THEN
        EXECUTE format('CREATE TRIGGER trg_sakhtyar_test_egress BEFORE INSERT OR UPDATE ON public.%I FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_block_test_external_queue()', t);
      END IF;
    END IF;
  END LOOP;
END $egress$;
