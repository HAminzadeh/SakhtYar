-- Additive E2E phase-01 metadata. Old runs remain readable, NULLs mean legacy unknown.
-- Flyway runs this PostgreSQL migration transactionally. No business tables are modified.
ALTER TABLE public.simulation_e2e_run
    ADD COLUMN IF NOT EXISTS tester_user_id uuid REFERENCES public.app_user(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS tester_username varchar(100),
    ADD COLUMN IF NOT EXISTS tester_identity_action varchar(12),
    ADD COLUMN IF NOT EXISTS source_commit varchar(64),
    ADD COLUMN IF NOT EXISTS trace_id varchar(80);

ALTER TABLE public.simulation_e2e_run
    ADD CONSTRAINT ck_sim_e2e_tester_action
    CHECK (tester_identity_action IS NULL OR tester_identity_action IN ('CREATED','REUSED'));

ALTER TABLE public.simulation_e2e_result
    ADD COLUMN IF NOT EXISTS evidence_schema_version smallint;
-- Existing legacy evidence remains NULL, never mislabelled as version 1.

-- PostgreSQL account names are unique case-insensitively too. If pre-existing collisions exist,
-- Flyway aborts transaction instead of modifying any password/account/role.
CREATE UNIQUE INDEX IF NOT EXISTS ux_sakhtyar_app_user_username_ci
    ON public.app_user (lower(username));
