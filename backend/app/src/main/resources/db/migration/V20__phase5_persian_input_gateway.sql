-- SakhtYar Phase 5.12 - Persian Input Gateway
-- Persists immutable normalization evidence before agent orchestration.

CREATE TABLE persian_input_request (
    id UUID PRIMARY KEY,
    case_id UUID REFERENCES construction_case(id) ON DELETE SET NULL,
    conversation_id UUID,
    raw_text TEXT NOT NULL,
    normalized_text TEXT NOT NULL,
    locale VARCHAR(10) NOT NULL DEFAULT 'fa-IR',
    status VARCHAR(40) NOT NULL,
    schema_version VARCHAR(20) NOT NULL,
    normalizer_version VARCHAR(20) NOT NULL,
    canonical_parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
    recognized_terms JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_persian_input_status CHECK (status IN ('READY','NEEDS_CLARIFICATION')),
    CONSTRAINT chk_persian_input_canonical_parameters_object CHECK (jsonb_typeof(canonical_parameters)='object'),
    CONSTRAINT chk_persian_input_recognized_terms_object CHECK (jsonb_typeof(recognized_terms)='object'),
    CONSTRAINT chk_persian_input_metadata_object CHECK (jsonb_typeof(metadata)='object')
);

CREATE INDEX idx_persian_input_case
    ON persian_input_request(case_id,created_at DESC);

CREATE INDEX idx_persian_input_conversation
    ON persian_input_request(conversation_id,created_at DESC);

CREATE INDEX idx_persian_input_status
    ON persian_input_request(status,created_at DESC);