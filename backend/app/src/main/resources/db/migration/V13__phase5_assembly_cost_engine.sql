-- SakhtYar Phase 5.5 - Assembly & Cost Engine
-- Assemblies are reusable recipes/BOMs. Estimates snapshot selected market aggregates
-- so later results remain explainable even when market prices change.

CREATE TABLE cost_assembly (
    id UUID PRIMARY KEY,
    code VARCHAR(120) NOT NULL UNIQUE,
    name_fa VARCHAR(400) NOT NULL,
    name_en VARCHAR(400),
    category VARCHAR(160) NOT NULL,
    output_unit_code VARCHAR(40) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_cost_assembly_metadata_object CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE INDEX idx_cost_assembly_category
    ON cost_assembly(category, active);

CREATE TABLE cost_assembly_component (
    id UUID PRIMARY KEY,
    assembly_id UUID NOT NULL REFERENCES cost_assembly(id) ON DELETE CASCADE,
    material_item_id UUID NOT NULL REFERENCES material_item(id) ON DELETE RESTRICT,
    variant_id UUID REFERENCES material_variant(id) ON DELETE RESTRICT,
    quantity NUMERIC(20,6) NOT NULL,
    waste_factor NUMERIC(10,6) NOT NULL DEFAULT 0,
    unit_code VARCHAR(40) NOT NULL,
    price_basis VARCHAR(30) NOT NULL DEFAULT 'MEDIAN',
    sort_order INTEGER NOT NULL DEFAULT 0,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_assembly_component_quantity CHECK (quantity > 0),
    CONSTRAINT chk_assembly_component_waste CHECK (waste_factor >= 0 AND waste_factor <= 10),
    CONSTRAINT chk_assembly_component_price_basis CHECK (price_basis IN ('MEDIAN','AVERAGE','MIN','MAX'))
);

CREATE INDEX idx_cost_assembly_component_assembly
    ON cost_assembly_component(assembly_id, sort_order, id);

CREATE TABLE cost_estimate (
    id UUID PRIMARY KEY,
    assembly_id UUID NOT NULL REFERENCES cost_assembly(id) ON DELETE RESTRICT,
    requested_quantity NUMERIC(20,6) NOT NULL,
    output_unit_code VARCHAR(40) NOT NULL,
    price_type VARCHAR(40) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    province VARCHAR(160),
    city VARCHAR(160),
    unit_cost NUMERIC(20,2) NOT NULL,
    total_cost NUMERIC(20,2) NOT NULL,
    algorithm_version VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'CALCULATED',
    calculated_by VARCHAR(150) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_cost_estimate_quantity CHECK (requested_quantity > 0),
    CONSTRAINT chk_cost_estimate_costs CHECK (unit_cost >= 0 AND total_cost >= 0)
);

CREATE INDEX idx_cost_estimate_assembly
    ON cost_estimate(assembly_id, calculated_at DESC);

CREATE TABLE cost_estimate_line (
    id UUID PRIMARY KEY,
    estimate_id UUID NOT NULL REFERENCES cost_estimate(id) ON DELETE CASCADE,
    assembly_component_id UUID NOT NULL REFERENCES cost_assembly_component(id) ON DELETE RESTRICT,
    material_item_id UUID NOT NULL REFERENCES material_item(id) ON DELETE RESTRICT,
    variant_id UUID REFERENCES material_variant(id) ON DELETE RESTRICT,
    aggregate_id UUID NOT NULL REFERENCES material_price_aggregate(id) ON DELETE RESTRICT,
    base_quantity NUMERIC(20,6) NOT NULL,
    waste_factor NUMERIC(10,6) NOT NULL,
    effective_quantity NUMERIC(20,6) NOT NULL,
    unit_code VARCHAR(40) NOT NULL,
    unit_price NUMERIC(20,2) NOT NULL,
    line_total NUMERIC(20,2) NOT NULL,
    price_basis VARCHAR(30) NOT NULL,
    source_sample_count INTEGER NOT NULL,
    source_period_start TIMESTAMPTZ NOT NULL,
    source_period_end TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_cost_estimate_line_quantities CHECK (
        base_quantity > 0 AND effective_quantity > 0 AND waste_factor >= 0
    ),
    CONSTRAINT chk_cost_estimate_line_prices CHECK (unit_price >= 0 AND line_total >= 0)
);

CREATE INDEX idx_cost_estimate_line_estimate
    ON cost_estimate_line(estimate_id);

CREATE INDEX idx_cost_estimate_line_material
    ON cost_estimate_line(material_item_id, aggregate_id);