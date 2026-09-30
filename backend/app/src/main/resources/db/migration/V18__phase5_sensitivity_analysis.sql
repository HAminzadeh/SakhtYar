-- SakhtYar Phase 5.10 - Sensitivity Analysis

CREATE TABLE sensitivity_analysis (
    id UUID PRIMARY KEY,
    financial_analysis_id UUID NOT NULL REFERENCES financial_analysis(id) ON DELETE RESTRICT,
    case_id UUID NOT NULL REFERENCES construction_case(id) ON DELETE RESTRICT,
    scenario_id UUID NOT NULL REFERENCES construction_scenario(id) ON DELETE RESTRICT,
    currency_code VARCHAR(3) NOT NULL,
    point_count INTEGER NOT NULL,
    best_profit NUMERIC(20,2),
    worst_profit NUMERIC(20,2),
    best_roi_percent NUMERIC(20,6),
    worst_roi_percent NUMERIC(20,6),
    algorithm_version VARCHAR(40) NOT NULL,
    input_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    summary_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    calculated_by VARCHAR(150) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_sensitivity_point_count CHECK (point_count > 0),
    CONSTRAINT chk_sensitivity_input_object CHECK (jsonb_typeof(input_snapshot) = 'object'),
    CONSTRAINT chk_sensitivity_summary_object CHECK (jsonb_typeof(summary_snapshot) = 'object')
);

CREATE INDEX idx_sensitivity_analysis_financial
    ON sensitivity_analysis(financial_analysis_id, calculated_at DESC);

CREATE INDEX idx_sensitivity_analysis_case
    ON sensitivity_analysis(case_id, calculated_at DESC);

CREATE TABLE sensitivity_point (
    id UUID PRIMARY KEY,
    sensitivity_analysis_id UUID NOT NULL REFERENCES sensitivity_analysis(id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL,
    sale_price_change_percent NUMERIC(12,6) NOT NULL,
    sellable_area_change_percent NUMERIC(12,6) NOT NULL,
    construction_cost_change_percent NUMERIC(12,6) NOT NULL,
    adjusted_sellable_area_m2 NUMERIC(20,6) NOT NULL,
    adjusted_sale_price_per_m2 NUMERIC(20,2) NOT NULL,
    adjusted_base_construction_cost NUMERIC(20,2) NOT NULL,
    gross_revenue NUMERIC(20,2) NOT NULL,
    total_project_cost NUMERIC(20,2) NOT NULL,
    projected_profit NUMERIC(20,2) NOT NULL,
    roi_percent NUMERIC(20,6),
    profit_margin_percent NUMERIC(20,6),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_sensitivity_sequence UNIQUE(sensitivity_analysis_id, sequence_no),
    CONSTRAINT chk_sensitivity_adjusted_area CHECK (adjusted_sellable_area_m2 > 0),
    CONSTRAINT chk_sensitivity_adjusted_values CHECK (
        adjusted_sale_price_per_m2 >= 0 AND adjusted_base_construction_cost >= 0
        AND gross_revenue >= 0 AND total_project_cost >= 0
    )
);

CREATE INDEX idx_sensitivity_point_analysis
    ON sensitivity_point(sensitivity_analysis_id, sequence_no);