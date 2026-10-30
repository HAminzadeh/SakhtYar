-- T10: metadata/history only; no Fixture inserts and no alteration of existing business tables.
-- Flyway applies this migration transactionally on PostgreSQL.
CREATE TABLE IF NOT EXISTS public.t10_e2e_run (
 id uuid PRIMARY KEY,
 workspace_id uuid NOT NULL,
 requested_by varchar(200),
 source_commit varchar(64),
 started_at timestamptz NOT NULL DEFAULT now(),
 finished_at timestamptz,
 status varchar(24) NOT NULL CHECK(status IN ('PENDING','RUNNING','PASSED','FAILED','BLOCKED','CANCELLED')),
 scenario_count integer NOT NULL DEFAULT 0 CHECK(scenario_count >= 0),
 summary jsonb NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX IF NOT EXISTS idx_t10_e2e_run_workspace_started ON public.t10_e2e_run(workspace_id,started_at DESC);
CREATE TABLE IF NOT EXISTS public.t10_e2e_scenario_run (
 id uuid PRIMARY KEY,
 run_id uuid NOT NULL REFERENCES public.t10_e2e_run(id) ON DELETE RESTRICT,
 scenario_id varchar(120) NOT NULL,
 started_at timestamptz,
 finished_at timestamptz,
 status varchar(24) NOT NULL CHECK(status IN ('PENDING','RUNNING','PASSED','FAILED','BLOCKED','SKIPPED')),
 result jsonb NOT NULL DEFAULT '{}'::jsonb,
 UNIQUE(run_id,scenario_id)
);
CREATE TABLE IF NOT EXISTS public.t10_e2e_step (
 id uuid PRIMARY KEY,
 scenario_run_id uuid NOT NULL REFERENCES public.t10_e2e_scenario_run(id) ON DELETE RESTRICT,
 step_key varchar(160) NOT NULL,
 ordinal integer NOT NULL CHECK(ordinal >= 0),
 started_at timestamptz,
 finished_at timestamptz,
 status varchar(24) NOT NULL CHECK(status IN ('PENDING','RUNNING','PASSED','FAILED','BLOCKED','SKIPPED')),
 evidence jsonb NOT NULL DEFAULT '{}'::jsonb,
 UNIQUE(scenario_run_id,step_key)
);
CREATE INDEX IF NOT EXISTS idx_t10_step_scenario ON public.t10_e2e_step(scenario_run_id,ordinal);
-- Explicitly deny access by default; policies and role grants must be reviewed separately.
ALTER TABLE public.t10_e2e_run ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.t10_e2e_run FORCE ROW LEVEL SECURITY;
ALTER TABLE public.t10_e2e_scenario_run ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.t10_e2e_scenario_run FORCE ROW LEVEL SECURITY;
ALTER TABLE public.t10_e2e_step ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.t10_e2e_step FORCE ROW LEVEL SECURITY;
-- Do not add a permissive policy here: zero-privilege until phase-2 role design is approved.

-- T10 workspace integrity: prevent orphaned history runs. The referenced
-- simulation_workspace.id must already exist as a UUID primary/unique key.
ALTER TABLE public.t10_e2e_run
  ADD CONSTRAINT fk_t10_e2e_run_workspace
  FOREIGN KEY (workspace_id) REFERENCES public.simulation_workspace(id)
  ON UPDATE RESTRICT ON DELETE RESTRICT;