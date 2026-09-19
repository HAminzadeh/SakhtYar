CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    role VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE construction_case (
    id UUID PRIMARY KEY,
    title VARCHAR(250) NOT NULL,
    status VARCHAR(30) NOT NULL,
    description TEXT,
    city VARCHAR(100),
    district VARCHAR(100),
    address TEXT,
    land_area_m2 NUMERIC(12, 2),
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_construction_case_status
    ON construction_case(status);

CREATE INDEX idx_construction_case_updated_at
    ON construction_case(updated_at DESC);

CREATE TABLE case_document (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES construction_case(id) ON DELETE CASCADE,
    original_filename VARCHAR(500) NOT NULL,
    content_type VARCHAR(200),
    size_bytes BIGINT NOT NULL,
    object_key VARCHAR(1000) NOT NULL UNIQUE,
    sha256 VARCHAR(64) NOT NULL,
    uploaded_by VARCHAR(100) NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_case_document_case_id
    ON case_document(case_id);

CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID,
    action VARCHAR(150) NOT NULL,
    actor VARCHAR(150) NOT NULL,
    payload_text TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_event_aggregate
    ON audit_event(aggregate_type, aggregate_id);

CREATE INDEX idx_audit_event_created_at
    ON audit_event(created_at DESC);
