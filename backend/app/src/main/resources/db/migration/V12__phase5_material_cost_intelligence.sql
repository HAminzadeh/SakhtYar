-- SakhtYar Phase 5.4 - Material & Cost Intelligence
-- Price observations are append-only evidence. Aggregates are derived and reproducible.

CREATE TABLE material_item (
    id UUID PRIMARY KEY,
    code VARCHAR(120) NOT NULL UNIQUE,
    name_fa VARCHAR(400) NOT NULL,
    name_en VARCHAR(400),
    category VARCHAR(160) NOT NULL,
    unit_code VARCHAR(40) NOT NULL,
    knowledge_term_id UUID REFERENCES knowledge_term(id) ON DELETE SET NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_material_item_metadata_object CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE INDEX idx_material_item_category
    ON material_item(category, active);

CREATE TABLE material_variant (
    id UUID PRIMARY KEY,
    material_item_id UUID NOT NULL REFERENCES material_item(id) ON DELETE CASCADE,
    brand VARCHAR(250),
    model VARCHAR(250),
    grade VARCHAR(160),
    manufacturer VARCHAR(250),
    country_code VARCHAR(2),
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_material_variant_attributes_object CHECK (jsonb_typeof(attributes) = 'object')
);

CREATE INDEX idx_material_variant_item
    ON material_variant(material_item_id, active);

CREATE TABLE material_price_observation (
    id UUID PRIMARY KEY,
    material_item_id UUID NOT NULL REFERENCES material_item(id) ON DELETE CASCADE,
    variant_id UUID REFERENCES material_variant(id) ON DELETE SET NULL,
    price_type VARCHAR(40) NOT NULL,
    amount NUMERIC(20,2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    unit_code VARCHAR(40) NOT NULL,
    quantity_basis NUMERIC(20,6) NOT NULL DEFAULT 1,
    province VARCHAR(160),
    city VARCHAR(160),
    source_id UUID NOT NULL REFERENCES knowledge_source(id) ON DELETE RESTRICT,
    source_url VARCHAR(3000),
    observed_at TIMESTAMPTZ NOT NULL,
    collected_at TIMESTAMPTZ NOT NULL,
    confidence NUMERIC(5,4),
    review_status VARCHAR(40) NOT NULL DEFAULT 'PENDING_REVIEW',
    origin VARCHAR(40) NOT NULL,
    raw_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(150) NOT NULL,
    reviewed_by VARCHAR(150),
    reviewed_at TIMESTAMPTZ,
    review_note TEXT,
    CONSTRAINT chk_material_price_amount CHECK (amount > 0),
    CONSTRAINT chk_material_price_quantity CHECK (quantity_basis > 0),
    CONSTRAINT chk_material_price_confidence CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    CONSTRAINT chk_material_price_payload_object CHECK (jsonb_typeof(raw_payload) = 'object')
);

CREATE INDEX idx_material_price_item_time
    ON material_price_observation(material_item_id, observed_at DESC);

CREATE INDEX idx_material_price_review
    ON material_price_observation(review_status, collected_at DESC);

CREATE INDEX idx_material_price_region
    ON material_price_observation(province, city, observed_at DESC);

CREATE TABLE material_price_aggregate (
    id UUID PRIMARY KEY,
    material_item_id UUID NOT NULL REFERENCES material_item(id) ON DELETE CASCADE,
    variant_id UUID REFERENCES material_variant(id) ON DELETE CASCADE,
    price_type VARCHAR(40) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    unit_code VARCHAR(40) NOT NULL,
    province VARCHAR(160),
    city VARCHAR(160),
    sample_count INTEGER NOT NULL,
    min_amount NUMERIC(20,2) NOT NULL,
    max_amount NUMERIC(20,2) NOT NULL,
    average_amount NUMERIC(20,2) NOT NULL,
    median_amount NUMERIC(20,2) NOT NULL,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    algorithm_version VARCHAR(40) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_material_price_aggregate_samples CHECK (sample_count > 0)
);

CREATE INDEX idx_material_price_aggregate_item
    ON material_price_aggregate(material_item_id, calculated_at DESC);

CREATE TABLE material_price_anomaly (
    id UUID PRIMARY KEY,
    observation_id UUID NOT NULL UNIQUE REFERENCES material_price_observation(id) ON DELETE CASCADE,
    anomaly_type VARCHAR(60) NOT NULL,
    severity VARCHAR(30) NOT NULL,
    score NUMERIC(10,4) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    reason TEXT NOT NULL,
    reviewed_by VARCHAR(150),
    reviewed_at TIMESTAMPTZ,
    review_note TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_material_price_anomaly_status
    ON material_price_anomaly(status, created_at DESC);