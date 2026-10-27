-- V62: Honest, persisted business-E2E execution independent of read-only V3 preflight.
-- No changes to existing business records, policies, or fixture data.
CREATE TABLE simulation_e2e_run (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES simulation_workspace(id) ON DELETE RESTRICT,
    request_key VARCHAR(100) NOT NULL,
    requested_by VARCHAR(160) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
        CHECK (state IN ('QUEUED','RUNNING','COMPLETED','INTERRUPTED')),
    scenario_count INTEGER NOT NULL CHECK (scenario_count BETWEEN 1 AND 24),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    UNIQUE(workspace_id, request_key)
);
CREATE INDEX ix_sim_e2e_run_workspace ON simulation_e2e_run(workspace_id, requested_at DESC);

CREATE TABLE simulation_e2e_result (
    run_id UUID NOT NULL REFERENCES simulation_e2e_run(id) ON DELETE CASCADE,
    scenario_id VARCHAR(10) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
        CHECK (state IN ('QUEUED','RUNNING','PASSED','FAILED','BLOCKED')),
    summary TEXT NOT NULL DEFAULT '',
    evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    PRIMARY KEY(run_id, scenario_id)
);
CREATE INDEX ix_sim_e2e_result_state ON simulation_e2e_result(run_id,state);

CREATE TABLE simulation_e2e_step (
    run_id UUID NOT NULL,
    scenario_id VARCHAR(10) NOT NULL,
    step_no INTEGER NOT NULL,
    title VARCHAR(180) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
        CHECK (state IN ('QUEUED','RUNNING','PASSED','FAILED','BLOCKED')),
    detail TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    PRIMARY KEY(run_id, scenario_id, step_no),
    FOREIGN KEY(run_id,scenario_id) REFERENCES simulation_e2e_result(run_id,scenario_id) ON DELETE CASCADE
);
