-- SakhtYar Phase 5.1 - Core Domain Foundation
-- This migration is deliberately additive so existing Phase 0-4 APIs continue to work.

ALTER TABLE construction_case
    ADD COLUMN project_code VARCHAR(40),
    ADD COLUMN workflow_stage VARCHAR(50) NOT NULL DEFAULT 'DATA_COLLECTION',
    ADD COLUMN currency_code VARCHAR(3) NOT NULL DEFAULT 'IRR',
    ADD COLUMN data_schema_version VARCHAR(20) NOT NULL DEFAULT '2.0',
    ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

UPDATE construction_case
SET project_code = 'PRJ-' || UPPER(SUBSTRING(REPLACE(id::text, '-', '') FROM 1 FOR 12))
WHERE project_code IS NULL;

ALTER TABLE construction_case
    ALTER COLUMN project_code SET NOT NULL;

CREATE UNIQUE INDEX ux_construction_case_project_code
    ON construction_case(project_code);

CREATE INDEX idx_construction_case_workflow_stage
    ON construction_case(workflow_stage);

ALTER TABLE construction_case
    ADD CONSTRAINT chk_construction_case_metadata_object
        CHECK (jsonb_typeof(metadata) = 'object');

CREATE TABLE builder_profile (
    id UUID PRIMARY KEY,
    legal_name VARCHAR(250) NOT NULL,
    trade_name VARCHAR(250),
    national_identifier VARCHAR(30),
    registration_no VARCHAR(60),
    mobile VARCHAR(30),
    phone VARCHAR(30),
    email VARCHAR(250),
    website VARCHAR(500),
    province VARCHAR(100),
    city VARCHAR(100),
    address TEXT,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_builder_profile_metadata_object
        CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE INDEX idx_builder_profile_legal_name
    ON builder_profile(legal_name);

CREATE INDEX idx_builder_profile_city
    ON builder_profile(city);

CREATE UNIQUE INDEX ux_builder_profile_national_identifier
    ON builder_profile(national_identifier)
    WHERE national_identifier IS NOT NULL;

CREATE TABLE project_builder (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES construction_case(id) ON DELETE CASCADE,
    builder_id UUID NOT NULL REFERENCES builder_profile(id) ON DELETE RESTRICT,
    role VARCHAR(50) NOT NULL DEFAULT 'CANDIDATE',
    status VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    proposed_share_percent NUMERIC(7, 4),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_project_builder_share
        CHECK (
            proposed_share_percent IS NULL
            OR (proposed_share_percent >= 0 AND proposed_share_percent <= 100)
        ),
    CONSTRAINT chk_project_builder_metadata_object
        CHECK (jsonb_typeof(metadata) = 'object'),
    CONSTRAINT ux_project_builder UNIQUE (case_id, builder_id)
);

CREATE INDEX idx_project_builder_case
    ON project_builder(case_id, status);

CREATE UNIQUE INDEX ux_project_builder_primary
    ON project_builder(case_id)
    WHERE is_primary = TRUE;

CREATE TABLE contract_record (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES construction_case(id) ON DELETE CASCADE,
    contract_code VARCHAR(80) NOT NULL,
    contract_type VARCHAR(60) NOT NULL DEFAULT 'PARTICIPATION_IN_CONSTRUCTION',
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    title VARCHAR(300) NOT NULL,
    signed_at TIMESTAMPTZ,
    effective_from DATE,
    effective_to DATE,
    schema_version VARCHAR(20) NOT NULL DEFAULT '1.0',
    terms JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_contract_record_case_code UNIQUE (case_id, contract_code),
    CONSTRAINT chk_contract_record_terms_object
        CHECK (jsonb_typeof(terms) = 'object'),
    CONSTRAINT chk_contract_record_metadata_object
        CHECK (jsonb_typeof(metadata) = 'object'),
    CONSTRAINT chk_contract_record_effective_dates
        CHECK (
            effective_from IS NULL
            OR effective_to IS NULL
            OR effective_to >= effective_from
        )
);

CREATE INDEX idx_contract_record_case
    ON contract_record(case_id, status);

CREATE TABLE knowledge_source (
    id UUID PRIMARY KEY,
    source_code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(300) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    base_url VARCHAR(2000),
    jurisdiction_country VARCHAR(2),
    jurisdiction_province VARCHAR(100),
    jurisdiction_city VARCHAR(100),
    trust_level VARCHAR(40) NOT NULL DEFAULT 'UNVERIFIED',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_knowledge_source_metadata_object
        CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE INDEX idx_knowledge_source_type
    ON knowledge_source(source_type, enabled);

CREATE INDEX idx_knowledge_source_jurisdiction
    ON knowledge_source(jurisdiction_country, jurisdiction_province, jurisdiction_city);

CREATE TABLE analysis_snapshot (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES construction_case(id) ON DELETE CASCADE,
    analysis_type VARCHAR(80) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'CREATED',
    schema_version VARCHAR(20) NOT NULL DEFAULT '1.0',
    calculation_engine_version VARCHAR(50),
    knowledge_version VARCHAR(50),
    regulation_version VARCHAR(50),
    material_price_version VARCHAR(50),
    input_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_analysis_snapshot_input_object
        CHECK (jsonb_typeof(input_snapshot) = 'object'),
    CONSTRAINT chk_analysis_snapshot_result_object
        CHECK (jsonb_typeof(result_snapshot) = 'object')
);

CREATE INDEX idx_analysis_snapshot_case
    ON analysis_snapshot(case_id, analysis_type, created_at DESC);

CREATE TABLE data_lineage (
    id UUID PRIMARY KEY,
    case_id UUID REFERENCES construction_case(id) ON DELETE CASCADE,
    analysis_snapshot_id UUID REFERENCES analysis_snapshot(id) ON DELETE CASCADE,
    output_path VARCHAR(500) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_entity_type VARCHAR(100),
    source_entity_id UUID,
    source_url VARCHAR(3000),
    source_label VARCHAR(500),
    source_observed_at TIMESTAMPTZ,
    confidence NUMERIC(6, 5),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_data_lineage_confidence
        CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    CONSTRAINT chk_data_lineage_metadata_object
        CHECK (jsonb_typeof(metadata) = 'object'),
    CONSTRAINT chk_data_lineage_has_source
        CHECK (
            source_entity_id IS NOT NULL
            OR source_url IS NOT NULL
            OR source_label IS NOT NULL
        )
);

CREATE INDEX idx_data_lineage_snapshot
    ON data_lineage(analysis_snapshot_id, output_path);

CREATE INDEX idx_data_lineage_case
    ON data_lineage(case_id, created_at DESC);

-- Prepare document metadata for the central document/file-server architecture.
ALTER TABLE case_document
    ADD COLUMN document_category VARCHAR(80) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN related_entity_type VARCHAR(100),
    ADD COLUMN related_entity_id UUID,
    ADD COLUMN version_no INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN parent_document_id UUID REFERENCES case_document(id) ON DELETE SET NULL,
    ADD COLUMN source_url VARCHAR(3000),
    ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE case_document
    ADD CONSTRAINT chk_case_document_version
        CHECK (version_no > 0),
    ADD CONSTRAINT chk_case_document_metadata_object
        CHECK (jsonb_typeof(metadata) = 'object');

CREATE INDEX idx_case_document_relation
    ON case_document(related_entity_type, related_entity_id);

CREATE INDEX idx_case_document_parent
    ON case_document(parent_document_id, version_no);