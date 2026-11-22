-- Phase12: additive draft-revision ledger. RUN ON AN ISOLATED DATABASE CLONE FIRST.
-- No DML changes to existing contract_record or construction_case rows.
-- This is NOT a legal signature, approval, consent, or certified legal evidence system.
CREATE TABLE public.contract_revision (
    id uuid PRIMARY KEY,
    contract_id uuid NOT NULL REFERENCES public.contract_record(id) ON DELETE RESTRICT,
    revision_no integer NOT NULL CHECK (revision_no > 0),
    event_code varchar(24) NOT NULL
        CHECK (event_code IN ('CREATED','UPDATED','BASELINE_CAPTURED')),
    snapshot jsonb NOT NULL CHECK (jsonb_typeof(snapshot)='object'),
    recorded_by varchar(150) NOT NULL,
    recorded_at timestamptz NOT NULL,
    CONSTRAINT ux_contract_revision_number UNIQUE (contract_id,revision_no)
);
CREATE INDEX ix_contract_revision_history ON public.contract_revision(contract_id,revision_no DESC);

-- Application writers cannot erase or rewrite a previously recorded snapshot.
-- INSERT additionally confirms the parent belongs to a REAL case.
CREATE FUNCTION public.sakhtyar_contract_revision_guard()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION 'Contract revision history is append-only' USING ERRCODE='42501';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.contract_record cr
        JOIN public.construction_case c ON c.id=cr.case_id
        WHERE cr.id=NEW.contract_id AND c.data_scope='REAL'
            AND c.test_workspace_id IS NULL
    ) THEN
        RAISE EXCEPTION 'Contract revision parent scope invalid' USING ERRCODE='42501';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_sakhtyar_contract_revision_guard
BEFORE INSERT OR UPDATE OR DELETE ON public.contract_revision
FOR EACH ROW EXECUTE FUNCTION public.sakhtyar_contract_revision_guard();

ALTER TABLE public.contract_revision ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.contract_revision FORCE ROW LEVEL SECURITY;
CREATE POLICY contract_revision_real_scope ON public.contract_revision FOR ALL
USING (EXISTS (
    SELECT 1 FROM public.contract_record cr
    JOIN public.construction_case c ON c.id=cr.case_id
    WHERE cr.id=contract_id AND public.sakhtyar_core_scope_ok(c.data_scope,c.test_workspace_id)
      AND c.data_scope='REAL'
))
WITH CHECK (EXISTS (
    SELECT 1 FROM public.contract_record cr
    JOIN public.construction_case c ON c.id=cr.case_id
    WHERE cr.id=contract_id AND public.sakhtyar_core_scope_ok(c.data_scope,c.test_workspace_id)
      AND c.data_scope='REAL'
));

REVOKE ALL ON FUNCTION public.sakhtyar_contract_revision_guard() FROM PUBLIC;
COMMENT ON TABLE public.contract_revision IS
'Immutable draft snapshots for technical provenance only. NOT legal signature or acceptance proof.';
