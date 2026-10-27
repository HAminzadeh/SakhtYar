-- Additive, durable per-subprocess evidence for SAFE read-only simulation preflights.
-- No business records are written or mutated by this migration.
CREATE TABLE IF NOT EXISTS simulation_run_step (
    run_id uuid NOT NULL REFERENCES simulation_run(id) ON DELETE CASCADE,
    scenario_id varchar(30) NOT NULL,
    step_no smallint NOT NULL CHECK (step_no BETWEEN 1 AND 8),
    step_code varchar(40) NOT NULL,
    title varchar(200) NOT NULL,
    state varchar(20) NOT NULL DEFAULT 'QUEUED'
        CHECK (state IN ('QUEUED','RUNNING','PASSED','FAILED','BLOCKED')),
    detail text,
    started_at timestamptz,
    finished_at timestamptz,
    PRIMARY KEY (run_id,scenario_id,step_no),
    CONSTRAINT fk_sim_step_result FOREIGN KEY (run_id,scenario_id)
      REFERENCES simulation_result(run_id,scenario_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS ix_sim_step_run_state
  ON simulation_run_step(run_id,state);
