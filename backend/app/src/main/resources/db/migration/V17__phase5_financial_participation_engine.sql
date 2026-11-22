-- SakhtYar Phase 5.9 - Financial & Participation Engine

CREATE TABLE financial_analysis (
    id UUID PRIMARY KEY,
    feasibility_assessment_id UUID NOT NULL REFERENCES feasibility_assessment(id) ON DELETE RESTRICT,
    case_id UUID NOT NULL REFERENCES construction_case(id) ON DELETE RESTRICT,
    property_id UUID NOT NULL REFERENCES property(id) ON DELETE RESTRICT,
    scenario_id UUID NOT NULL REFERENCES construction_scenario(id) ON DELETE RESTRICT,
    currency_code VARCHAR(3) NOT NULL,
    sellable_area_m2 NUMERIC(20,6) NOT NULL,
    expected_sale_price_per_m2 NUMERIC(20,2) NOT NULL,
    other_revenue NUMERIC(20,2) NOT NULL DEFAULT 0,
    base_construction_cost NUMERIC(20,2) NOT NULL,
    additional_cost NUMERIC(20,2) NOT NULL DEFAULT 0,
    financing_cost NUMERIC(20,2) NOT NULL DEFAULT 0,
    taxes_and_fees NUMERIC(20,2) NOT NULL DEFAULT 0,
    gross_revenue NUMERIC(20,2) NOT NULL,
    total_project_cost NUMERIC(20,2) NOT NULL,
    projected_profit NUMERIC(20,2) NOT NULL,
    roi_percent NUMERIC(20,6),
    profit_margin_percent NUMERIC(20,6),
    algorithm_version VARCHAR(40) NOT NULL,
    input_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    calculated_by VARCHAR(150) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_financial_sellable_area CHECK (sellable_area_m2 > 0),
    CONSTRAINT chk_financial_sale_price CHECK (expected_sale_price_per_m2 >= 0),
    CONSTRAINT chk_financial_costs CHECK (
        other_revenue >= 0 AND base_construction_cost >= 0 AND additional_cost >= 0
        AND financing_cost >= 0 AND taxes_and_fees >= 0
    ),
    CONSTRAINT chk_financial_input_object CHECK (jsonb_typeof(input_snapshot) = 'object'),
    CONSTRAINT chk_financial_result_object CHECK (jsonb_typeof(result_snapshot) = 'object')
);

CREATE INDEX idx_financial_analysis_case
    ON financial_analysis(case_id, calculated_at DESC);

CREATE INDEX idx_financial_analysis_scenario
    ON financial_analysis(scenario_id, calculated_at DESC);

CREATE TABLE financial_participation_allocation (
    id UUID PRIMARY KEY,
    financial_analysis_id UUID NOT NULL REFERENCES financial_analysis(id) ON DELETE CASCADE,
    participant_type VARCHAR(40) NOT NULL,
    participant_ref_id UUID,
    participant_label VARCHAR(400) NOT NULL,
    value_share_percent NUMERIC(9,6) NOT NULL,
    cost_share_percent NUMERIC(9,6) NOT NULL,
    allocated_revenue NUMERIC(20,2) NOT NULL,
    allocated_cost NUMERIC(20,2) NOT NULL,
    projected_net_value NUMERIC(20,2) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_financial_value_share CHECK (value_share_percent >= 0 AND value_share_percent <= 100),
    CONSTRAINT chk_financial_cost_share CHECK (cost_share_percent >= 0 AND cost_share_percent <= 100),
    CONSTRAINT chk_financial_allocation_metadata CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE INDEX idx_financial_participation_analysis
    ON financial_participation_allocation(financial_analysis_id, participant_type);