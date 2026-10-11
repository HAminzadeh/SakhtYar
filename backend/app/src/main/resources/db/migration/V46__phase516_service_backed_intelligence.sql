-- Phase 5.16: service-backed intelligence, market analytics, monitoring and workflow execution.

CREATE TABLE IF NOT EXISTS market_observation (
    id UUID PRIMARY KEY,
    source_code VARCHAR(120) NOT NULL,
    external_id VARCHAR(300),
    observed_at TIMESTAMPTZ NOT NULL,
    country_code VARCHAR(2) NOT NULL DEFAULT 'IR',
    province VARCHAR(120),
    city VARCHAR(120) NOT NULL,
    district VARCHAR(120),
    neighborhood VARCHAR(160),
    property_type VARCHAR(100),
    construction_year INTEGER,
    area_m2 NUMERIC(14,2) NOT NULL CHECK (area_m2 > 0),
    total_price NUMERIC(24,2),
    price_per_m2 NUMERIC(24,2) NOT NULL CHECK (price_per_m2 >= 0),
    currency_code VARCHAR(8) NOT NULL,
    listing_type VARCHAR(40),
    latitude NUMERIC(10,7),
    longitude NUMERIC(10,7),
    source_url VARCHAR(3000),
    confidence NUMERIC(6,5) CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    raw_payload JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(raw_payload)='object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_market_observation_external
    ON market_observation(source_code,external_id,observed_at)
    WHERE external_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_market_observation_scope_time
    ON market_observation(city,district,neighborhood,observed_at DESC);

CREATE INDEX IF NOT EXISTS ix_market_observation_property_type
    ON market_observation(property_type,area_m2,observed_at DESC);

CREATE INDEX IF NOT EXISTS ix_market_observation_price
    ON market_observation(city,district,price_per_m2);

CREATE TABLE IF NOT EXISTS market_snapshot (
    id UUID PRIMARY KEY,
    city VARCHAR(120) NOT NULL,
    district VARCHAR(120),
    neighborhood VARCHAR(160),
    property_type VARCHAR(100),
    period_days INTEGER NOT NULL CHECK (period_days > 0),
    sample_count BIGINT NOT NULL CHECK (sample_count >= 0),
    median_price_per_m2 NUMERIC(24,2),
    average_price_per_m2 NUMERIC(24,2),
    change_percent NUMERIC(12,4),
    currency_code VARCHAR(8),
    source_observed_at TIMESTAMPTZ,
    generated_at TIMESTAMPTZ NOT NULL,
    generated_by VARCHAR(160) NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_market_snapshot_scope
    ON market_snapshot(city,district,neighborhood,property_type,period_days,generated_at DESC);

CREATE TABLE IF NOT EXISTS notification_outbox (
    id UUID PRIMARY KEY,
    channel VARCHAR(40) NOT NULL,
    target VARCHAR(500) NOT NULL,
    subject VARCHAR(300) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','SENT','FAILED','CANCELLED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    last_error TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
        CHECK (jsonb_typeof(metadata)='object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS ix_notification_outbox_status
    ON notification_outbox(status,created_at);

CREATE TABLE IF NOT EXISTS monitoring_rule (
    id UUID PRIMARY KEY,
    code VARCHAR(120) NOT NULL UNIQUE,
    metric_code VARCHAR(80) NOT NULL,
    operator VARCHAR(8) NOT NULL CHECK (operator IN ('GT','GTE','LT','LTE','EQ')),
    threshold NUMERIC(24,6) NOT NULL,
    city VARCHAR(120) NOT NULL,
    district VARCHAR(120),
    period_days INTEGER NOT NULL CHECK (period_days > 0),
    notification_channel VARCHAR(40) NOT NULL,
    notification_target VARCHAR(500) NOT NULL,
    cooldown_minutes INTEGER NOT NULL DEFAULT 60 CHECK (cooldown_minutes > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_monitoring_rule_active
    ON monitoring_rule(active,metric_code);

CREATE TABLE IF NOT EXISTS monitoring_event (
    id UUID PRIMARY KEY,
    rule_id UUID NOT NULL REFERENCES monitoring_rule(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL,
    observed_value NUMERIC(24,6),
    threshold NUMERIC(24,6),
    message TEXT NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_monitoring_event_rule_time
    ON monitoring_event(rule_id,observed_at DESC);

CREATE TABLE IF NOT EXISTS intelligence_workflow_execution (
    id UUID PRIMARY KEY,
    workflow_id VARCHAR(160) NOT NULL,
    workflow_version VARCHAR(80) NOT NULL,
    status VARCHAR(30) NOT NULL,
    input_artifact_id VARCHAR(300),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS ix_intelligence_execution_workflow
    ON intelligence_workflow_execution(workflow_id,started_at DESC);

CREATE TABLE IF NOT EXISTS intelligence_workflow_step (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL
        REFERENCES intelligence_workflow_execution(id) ON DELETE CASCADE,
    step_id VARCHAR(160) NOT NULL,
    component_id VARCHAR(160) NOT NULL,
    status VARCHAR(30) NOT NULL,
    input_artifact_id VARCHAR(300),
    output_artifact_id VARCHAR(300),
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS ix_intelligence_step_execution
    ON intelligence_workflow_step(execution_id,started_at);

CREATE INDEX IF NOT EXISTS ix_builder_profile_metadata_gin
    ON builder_profile USING gin(metadata);
