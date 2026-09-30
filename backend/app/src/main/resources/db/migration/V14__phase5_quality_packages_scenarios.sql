CREATE TABLE quality_package (
    id UUID PRIMARY KEY,
    code VARCHAR(120) NOT NULL UNIQUE,
    name_fa VARCHAR(400) NOT NULL,
    name_en VARCHAR(400),
    quality_level VARCHAR(40) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_quality_package_metadata_object CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE INDEX idx_quality_package_level ON quality_package(quality_level, active);

CREATE TABLE quality_package_selection (
    id UUID PRIMARY KEY,
    package_id UUID NOT NULL REFERENCES quality_package(id) ON DELETE CASCADE,
    slot_code VARCHAR(120) NOT NULL,
    slot_name_fa VARCHAR(300) NOT NULL,
    assembly_id UUID NOT NULL REFERENCES cost_assembly(id) ON DELETE RESTRICT,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    quantity_multiplier NUMERIC(20,6) NOT NULL DEFAULT 1,
    sort_order INTEGER NOT NULL DEFAULT 0,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_quality_package_slot UNIQUE(package_id, slot_code),
    CONSTRAINT chk_quality_package_multiplier CHECK (quantity_multiplier > 0)
);

CREATE INDEX idx_quality_package_selection_package
    ON quality_package_selection(package_id, sort_order, id);

CREATE TABLE construction_scenario (
    id UUID PRIMARY KEY,
    code VARCHAR(120) NOT NULL UNIQUE,
    name_fa VARCHAR(400) NOT NULL,
    name_en VARCHAR(400),
    quality_package_id UUID REFERENCES quality_package(id) ON DELETE SET NULL,
    structural_system VARCHAR(160),
    scenario_status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    description TEXT,
    assumptions JSONB NOT NULL DEFAULT '{}'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_construction_scenario_assumptions_object CHECK (jsonb_typeof(assumptions) = 'object')
);

CREATE INDEX idx_construction_scenario_package
    ON construction_scenario(quality_package_id, scenario_status, active);

CREATE TABLE construction_scenario_item (
    id UUID PRIMARY KEY,
    scenario_id UUID NOT NULL REFERENCES construction_scenario(id) ON DELETE CASCADE,
    slot_code VARCHAR(120),
    assembly_id UUID NOT NULL REFERENCES cost_assembly(id) ON DELETE RESTRICT,
    quantity NUMERIC(20,6) NOT NULL,
    source VARCHAR(40) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_scenario_item_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_construction_scenario_item_scenario
    ON construction_scenario_item(scenario_id, sort_order, id);

CREATE TABLE scenario_cost_snapshot (
    id UUID PRIMARY KEY,
    scenario_id UUID NOT NULL REFERENCES construction_scenario(id) ON DELETE RESTRICT,
    price_type VARCHAR(40) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    province VARCHAR(160),
    city VARCHAR(160),
    total_cost NUMERIC(20,2) NOT NULL,
    algorithm_version VARCHAR(40) NOT NULL,
    calculated_by VARCHAR(150) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_scenario_cost_snapshot_total CHECK (total_cost >= 0)
);

CREATE INDEX idx_scenario_cost_snapshot_scenario
    ON scenario_cost_snapshot(scenario_id, calculated_at DESC);

CREATE TABLE scenario_cost_snapshot_line (
    id UUID PRIMARY KEY,
    snapshot_id UUID NOT NULL REFERENCES scenario_cost_snapshot(id) ON DELETE CASCADE,
    scenario_item_id UUID NOT NULL REFERENCES construction_scenario_item(id) ON DELETE RESTRICT,
    assembly_id UUID NOT NULL REFERENCES cost_assembly(id) ON DELETE RESTRICT,
    assembly_estimate_id UUID NOT NULL REFERENCES cost_estimate(id) ON DELETE RESTRICT,
    quantity NUMERIC(20,6) NOT NULL,
    unit_cost NUMERIC(20,2) NOT NULL,
    line_total NUMERIC(20,2) NOT NULL,
    CONSTRAINT chk_scenario_cost_line_quantity CHECK (quantity > 0),
    CONSTRAINT chk_scenario_cost_line_values CHECK (unit_cost >= 0 AND line_total >= 0)
);

CREATE INDEX idx_scenario_cost_snapshot_line_snapshot
    ON scenario_cost_snapshot_line(snapshot_id);