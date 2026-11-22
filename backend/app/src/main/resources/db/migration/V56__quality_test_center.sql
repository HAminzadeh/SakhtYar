-- SakhtYar Quality Center. Additive migration: no existing tables altered.
CREATE TABLE quality_test_run (
  id uuid PRIMARY KEY,
  request_key varchar(100) NOT NULL,
  requested_by varchar(160) NOT NULL,
  scope varchar(100) NOT NULL,
  environment varchar(32) NOT NULL,
  git_commit varchar(80),
  status varchar(24) NOT NULL CHECK (status IN ('QUEUED','RUNNING','COMPLETED','INTERRUPTED')),
  total integer NOT NULL DEFAULT 0,
  passed integer NOT NULL DEFAULT 0,
  failed integer NOT NULL DEFAULT 0,
  blocked integer NOT NULL DEFAULT 0,
  started_at timestamptz NOT NULL DEFAULT now(),
  finished_at timestamptz,
  CONSTRAINT uq_quality_run_request UNIQUE (requested_by, request_key)
);
CREATE INDEX ix_quality_run_started ON quality_test_run(started_at DESC);
CREATE INDEX ix_quality_run_scope ON quality_test_run(scope,started_at DESC);
CREATE TABLE quality_test_result (
  run_id uuid NOT NULL REFERENCES quality_test_run(id) ON DELETE CASCADE,
  test_id varchar(100) NOT NULL,
  module varchar(80) NOT NULL,
  title varchar(300) NOT NULL,
  status varchar(24) NOT NULL CHECK (status IN ('QUEUED','PASSED','FAILED','BLOCKED')),
  expected text,
  actual text,
  detail text,
  duration_ms bigint,
  started_at timestamptz,
  finished_at timestamptz,
  PRIMARY KEY(run_id,test_id)
);
CREATE INDEX ix_quality_result_test ON quality_test_result(test_id);
CREATE INDEX ix_quality_result_status ON quality_test_result(status);
