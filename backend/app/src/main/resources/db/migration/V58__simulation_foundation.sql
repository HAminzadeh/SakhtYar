-- T01 data-origin foundations. No existing business row is deleted or changed to TEST.
-- IMPORTANT: This migration alone DOES NOT establish safe REAL/TEST query isolation.
-- The future guarded writer and Row-Level Security rollout must pass a separate security gate.
CREATE TABLE IF NOT EXISTS simulation_workspace (
    id uuid PRIMARY KEY,
    code varchar(80) NOT NULL UNIQUE,
    title varchar(250) NOT NULL,
    owner_username varchar(160) NOT NULL,
    state varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (state IN ('ACTIVE','ARCHIVED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS simulation_run (
    id uuid PRIMARY KEY,
    workspace_id uuid NOT NULL REFERENCES simulation_workspace(id) ON DELETE RESTRICT,
    request_key varchar(100) NOT NULL,
    requested_by varchar(160) NOT NULL,
    requested_at timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz,
    state varchar(24) NOT NULL CHECK (state IN ('QUEUED','RUNNING','COMPLETED','BLOCKED','FAILED','INTERRUPTED')),
    scope varchar(30) NOT NULL DEFAULT 'TEST',
    scenario_count integer NOT NULL DEFAULT 0,
    git_commit varchar(80),
    simulated_clock_at timestamptz,
    external_effects_blocked boolean NOT NULL DEFAULT true,
    gate_reason text,
    UNIQUE(workspace_id,request_key),
    CONSTRAINT chk_sim_run_scope CHECK (scope = 'TEST')
);
CREATE INDEX IF NOT EXISTS ix_simulation_run_workspace ON simulation_run(workspace_id,requested_at DESC);

CREATE TABLE IF NOT EXISTS simulation_result (
    run_id uuid NOT NULL REFERENCES simulation_run(id) ON DELETE CASCADE,
    scenario_id varchar(30) NOT NULL,
    title varchar(250) NOT NULL,
    phase varchar(6) NOT NULL,
    state varchar(16) NOT NULL CHECK (state IN ('QUEUED','PASSED','FAILED','BLOCKED','SKIPPED')),
    evidence jsonb NOT NULL DEFAULT '{}'::jsonb,
    summary text,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (run_id, scenario_id)
);
CREATE INDEX IF NOT EXISTS ix_simulation_result_status ON simulation_result(state);

CREATE TABLE IF NOT EXISTS simulation_audit (
    id bigserial PRIMARY KEY,
    workspace_id uuid REFERENCES simulation_workspace(id) ON DELETE RESTRICT,
    run_id uuid REFERENCES simulation_run(id) ON DELETE SET NULL,
    actor varchar(160) NOT NULL,
    action varchar(120) NOT NULL,
    details jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Scope markers deliberately default existing rows to REAL. These markers are
-- classified metadata until every relevant query and background path is guarded.
DO $scope$
DECLARE t text; con text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'construction_case',
    'property',
    'property_owner',
    'case_document',
    'builder_profile',
    'project_builder',
    'contract_record',
    'construction_case_access',
    'knowledge_source',
    'knowledge_dataset',
    'knowledge_document',
    'knowledge_chunk',
    'knowledge_evidence',
    'knowledge_rule_candidate',
    'knowledge_legal_segment',
    'knowledge_ai_import_batch',
    'knowledge_ai_import_document',
    'knowledge_ai_import_item',
    'knowledge_pipeline_run',
    'knowledge_intake_execution',
    'knowledge_intake_inventory',
    'knowledge_rule_review',
    'knowledge_relation',
    'knowledge_term',
    'material_item',
    'material_variant',
    'material_price_observation',
    'material_price_aggregate',
    'material_price_anomaly',
    'cost_assembly',
    'cost_assembly_component',
    'cost_estimate',
    'cost_estimate_line',
    'quality_package',
    'construction_scenario',
    'construction_scenario_item',
    'scenario_cost_snapshot',
    'urban_rule',
    'urban_evaluation',
    'feasibility_assessment',
    'financial_analysis',
    'financial_participation_allocation',
    'sensitivity_analysis',
    'analysis_snapshot',
    'data_lineage',
    'crawl_source',
    'crawl_job',
    'crawl_url',
    'crawled_document',
    'market_observation',
    'market_snapshot',
    'monitoring_rule',
    'monitoring_event',
    'notification_outbox',
    'intelligence_workflow_execution',
    'intelligence_workflow_step',
    'learning_event'
  ] LOOP
    IF to_regclass('public.'||t) IS NOT NULL THEN
      EXECUTE format('ALTER TABLE public.%I ADD COLUMN IF NOT EXISTS data_scope varchar(8) NOT NULL DEFAULT ''REAL''',t);
      EXECUTE format('ALTER TABLE public.%I ADD COLUMN IF NOT EXISTS test_workspace_id uuid',t);
      EXECUTE format('ALTER TABLE public.%I ADD COLUMN IF NOT EXISTS test_run_id uuid',t);
      con := 'chk_' || t || '_sim_scope';
      IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = con AND conrelid = ('public.'||t)::regclass) THEN
        EXECUTE format('ALTER TABLE public.%I ADD CONSTRAINT %I CHECK (data_scope IN (''REAL'',''TEST'') AND ((data_scope = ''REAL'' AND test_workspace_id IS NULL AND test_run_id IS NULL) OR (data_scope = ''TEST'' AND test_workspace_id IS NOT NULL)))',t,con);
      END IF;
      EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON public.%I(test_workspace_id) WHERE data_scope = ''TEST''','ix_'||t||'_sim_workspace',t);
    END IF;
  END LOOP;
END $scope$;

-- Intentional: do not enable RLS before verified per-connection context propagation
-- and non-bypass application roles. Enabling RLS here could lock out existing users.
