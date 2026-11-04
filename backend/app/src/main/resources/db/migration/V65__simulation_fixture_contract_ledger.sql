-- Phase02 additive inventory ledger ONLY. No rows, roles, grants or policies on REAL tables are changed.
-- This is deliberately NOT a fixture factory or a permission to write business records.
CREATE TABLE public.simulation_fixture_contract_ledger (
    run_id uuid NOT NULL,
    scenario_id varchar(10) NOT NULL,
    workspace_id uuid NOT NULL REFERENCES public.simulation_workspace(id) ON DELETE RESTRICT,
    deterministic_fixture_id uuid NOT NULL,
    lifecycle_state varchar(18) NOT NULL DEFAULT 'BLOCKED'
        CHECK (lifecycle_state IN ('BLOCKED','PREPARED','QUARANTINED','CLEANED')),
    isolation_mode varchar(40) NOT NULL
        CHECK (isolation_mode IN ('CLONE_REQUIRED','LIMITED_CORE_RESTRICTED_LOGIN')),
    external_effect_count integer NOT NULL DEFAULT 0 CHECK (external_effect_count >= 0),
    observed_at timestamptz NOT NULL DEFAULT now(),
    evidence jsonb NOT NULL DEFAULT '{}'::jsonb,
    PRIMARY KEY (run_id,scenario_id),
    UNIQUE (deterministic_fixture_id),
    FOREIGN KEY (run_id,scenario_id) REFERENCES public.simulation_e2e_result(run_id,scenario_id) ON DELETE RESTRICT
);
CREATE INDEX ix_simulation_fixture_ledger_workspace ON public.simulation_fixture_contract_ledger(workspace_id, observed_at DESC);

-- Metadata integrity: cross-workspace inserts and reassignment are rejected even for application writers.
CREATE FUNCTION public.sakhtyar_phase02_fixture_ledger_guard() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE actual_workspace uuid;
BEGIN
    SELECT workspace_id INTO actual_workspace FROM public.simulation_e2e_run WHERE id=NEW.run_id;
    IF actual_workspace IS NULL OR actual_workspace IS DISTINCT FROM NEW.workspace_id THEN
        RAISE EXCEPTION 'Phase02 fixture ledger workspace mismatch' USING ERRCODE='23514';
    END IF;
    IF TG_OP='UPDATE' AND (
      OLD.run_id IS DISTINCT FROM NEW.run_id OR
      OLD.scenario_id IS DISTINCT FROM NEW.scenario_id OR
      OLD.workspace_id IS DISTINCT FROM NEW.workspace_id OR
      OLD.deterministic_fixture_id IS DISTINCT FROM NEW.deterministic_fixture_id) THEN
      RAISE EXCEPTION 'Phase02 fixture identity is immutable' USING ERRCODE='42501';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_sakhtyar_phase02_fixture_ledger_guard
BEFORE INSERT OR UPDATE ON public.simulation_fixture_contract_ledger
FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_phase02_fixture_ledger_guard();

-- No PUBLIC write permissions and no new privileges to TEST users.
REVOKE ALL ON public.simulation_fixture_contract_ledger FROM PUBLIC;
REVOKE ALL ON FUNCTION public.sakhtyar_phase02_fixture_ledger_guard() FROM PUBLIC;
COMMENT ON TABLE public.simulation_fixture_contract_ledger IS
'Phase02 reserved audit metadata, not proof of complete business E2E or production REAL/TEST isolation';
