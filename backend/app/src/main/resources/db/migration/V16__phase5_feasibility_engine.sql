-- SakhtYar Phase 5.8 - Feasibility Engine

CREATE TABLE feasibility_assessment (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES construction_case(id) ON DELETE RESTRICT,
    property_id UUID NOT NULL REFERENCES property(id) ON DELETE RESTRICT,
    scenario_id UUID NOT NULL REFERENCES construction_scenario(id) ON DELETE RESTRICT,
    scenario_cost_snapshot_id UUID NOT NULL REFERENCES scenario_cost_snapshot(id) ON DELETE RESTRICT,
    urban_evaluation_id UUID NOT NULL REFERENCES urban_evaluation(id) ON DELETE RESTRICT,
    status VARCHAR(40) NOT NULL,
    readiness_score NUMERIC(5,2) NOT NULL,
    scenario_cost NUMERIC(20,2) NOT NULL,
    cost_currency_code VARCHAR(3) NOT NULL,
    land_area_m2 NUMERIC(20,6),
    total_built_area_m2 NUMERIC(20,6),
    cost_per_land_m2 NUMERIC(20,2),
    cost_per_built_m2 NUMERIC(20,2),
    proposed_floors INTEGER,
    calculated_far NUMERIC(20,6),
    regulation_status VARCHAR(40) NOT NULL,
    blocking_reason_count INTEGER NOT NULL,
    warning_count INTEGER NOT NULL,
    algorithm_version VARCHAR(40) NOT NULL,
    input_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    assessed_by VARCHAR(150) NOT NULL,
    assessed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_feasibility_readiness_score CHECK (readiness_score >= 0 AND readiness_score <= 100),
    CONSTRAINT chk_feasibility_cost CHECK (scenario_cost >= 0),
    CONSTRAINT chk_feasibility_counts CHECK (blocking_reason_count >= 0 AND warning_count >= 0),
    CONSTRAINT chk_feasibility_input_object CHECK (jsonb_typeof(input_snapshot) = 'object'),
    CONSTRAINT chk_feasibility_result_object CHECK (jsonb_typeof(result_snapshot) = 'object')
);

CREATE INDEX idx_feasibility_case
    ON feasibility_assessment(case_id, assessed_at DESC);

CREATE INDEX idx_feasibility_scenario
    ON feasibility_assessment(scenario_id, assessed_at DESC);

CREATE TABLE feasibility_reason (
    id UUID PRIMARY KEY,
    assessment_id UUID NOT NULL REFERENCES feasibility_assessment(id) ON DELETE CASCADE,
    reason_type VARCHAR(40) NOT NULL,
    reason_code VARCHAR(120) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    source_entity_type VARCHAR(120),
    source_entity_id UUID,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_feasibility_reason_details_object CHECK (jsonb_typeof(details) = 'object')
);

CREATE INDEX idx_feasibility_reason_assessment
    ON feasibility_reason(assessment_id, reason_type);