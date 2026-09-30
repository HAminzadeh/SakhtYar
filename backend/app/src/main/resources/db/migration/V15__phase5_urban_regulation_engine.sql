-- SakhtYar Phase 5.7 - Urban & Regulation Engine

CREATE TABLE urban_rule (
    id UUID PRIMARY KEY,
    code VARCHAR(140) NOT NULL UNIQUE,
    name_fa VARCHAR(500) NOT NULL,
    name_en VARCHAR(500),
    rule_type VARCHAR(50) NOT NULL,
    jurisdiction_country VARCHAR(2),
    jurisdiction_province VARCHAR(100),
    jurisdiction_city VARCHAR(100),
    jurisdiction_district VARCHAR(100),
    property_type VARCHAR(80),
    source_id UUID NOT NULL REFERENCES knowledge_source(id) ON DELETE RESTRICT,
    source_url VARCHAR(2000),
    valid_from DATE,
    valid_to DATE,
    priority INTEGER NOT NULL DEFAULT 100,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_urban_rule_params_object CHECK (jsonb_typeof(parameters) = 'object'),
    CONSTRAINT chk_urban_rule_validity CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from)
);

CREATE INDEX idx_urban_rule_scope
    ON urban_rule(jurisdiction_province, jurisdiction_city, jurisdiction_district, property_type, active);

CREATE INDEX idx_urban_rule_type
    ON urban_rule(rule_type, priority);

CREATE TABLE urban_evaluation (
    id UUID PRIMARY KEY,
    property_id UUID NOT NULL REFERENCES property(id) ON DELETE RESTRICT,
    scenario_id UUID REFERENCES construction_scenario(id) ON DELETE RESTRICT,
    status VARCHAR(40) NOT NULL,
    rule_count INTEGER NOT NULL,
    passed_count INTEGER NOT NULL,
    failed_count INTEGER NOT NULL,
    review_count INTEGER NOT NULL,
    algorithm_version VARCHAR(40) NOT NULL,
    input_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    evaluated_by VARCHAR(150) NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_urban_eval_counts CHECK (
        rule_count >= 0 AND passed_count >= 0 AND failed_count >= 0 AND review_count >= 0
        AND passed_count + failed_count + review_count = rule_count
    ),
    CONSTRAINT chk_urban_eval_snapshot_object CHECK (jsonb_typeof(input_snapshot) = 'object')
);

CREATE INDEX idx_urban_evaluation_property
    ON urban_evaluation(property_id, evaluated_at DESC);

CREATE INDEX idx_urban_evaluation_scenario
    ON urban_evaluation(scenario_id, evaluated_at DESC);

CREATE TABLE urban_evaluation_result (
    id UUID PRIMARY KEY,
    evaluation_id UUID NOT NULL REFERENCES urban_evaluation(id) ON DELETE CASCADE,
    rule_id UUID NOT NULL REFERENCES urban_rule(id) ON DELETE RESTRICT,
    outcome VARCHAR(30) NOT NULL,
    actual_value VARCHAR(500),
    expected_value VARCHAR(500),
    message VARCHAR(2000) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_urban_result_details_object CHECK (jsonb_typeof(details) = 'object')
);

CREATE INDEX idx_urban_evaluation_result_eval
    ON urban_evaluation_result(evaluation_id);

CREATE INDEX idx_urban_evaluation_result_rule
    ON urban_evaluation_result(rule_id, outcome);