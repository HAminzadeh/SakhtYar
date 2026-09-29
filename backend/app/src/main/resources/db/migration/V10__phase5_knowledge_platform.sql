-- SakhtYar Phase 5.2 - Knowledge Platform
-- Human-reviewed knowledge is authoritative. Candidate/LLM/crawler output is never auto-promoted.

CREATE TABLE knowledge_term (
    id UUID PRIMARY KEY,
    code VARCHAR(120) NOT NULL UNIQUE,
    domain VARCHAR(80) NOT NULL,
    category VARCHAR(120),
    name_fa VARCHAR(300) NOT NULL,
    name_en VARCHAR(300),
    definition TEXT,
    unit_code VARCHAR(40),
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING_REVIEW',
    confidence NUMERIC(6,5),
    source_id UUID REFERENCES knowledge_source(id) ON DELETE SET NULL,
    provenance_url VARCHAR(3000),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(150) NOT NULL,
    updated_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_knowledge_term_confidence
        CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    CONSTRAINT chk_knowledge_term_metadata_object
        CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE INDEX idx_knowledge_term_domain_status
    ON knowledge_term(domain, status);

CREATE INDEX idx_knowledge_term_name_fa
    ON knowledge_term(name_fa);

CREATE TABLE knowledge_term_alias (
    id UUID PRIMARY KEY,
    term_id UUID NOT NULL REFERENCES knowledge_term(id) ON DELETE CASCADE,
    locale VARCHAR(10) NOT NULL DEFAULT 'fa',
    alias VARCHAR(300) NOT NULL,
    alias_normalized VARCHAR(300) NOT NULL,
    alias_type VARCHAR(40) NOT NULL DEFAULT 'SYNONYM',
    status VARCHAR(40) NOT NULL DEFAULT 'APPROVED',
    confidence NUMERIC(6,5),
    source_id UUID REFERENCES knowledge_source(id) ON DELETE SET NULL,
    created_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_knowledge_alias_confidence
        CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    CONSTRAINT ux_knowledge_term_alias
        UNIQUE (term_id, locale, alias_normalized)
);

CREATE INDEX idx_knowledge_alias_normalized
    ON knowledge_term_alias(locale, alias_normalized);

CREATE TABLE knowledge_relation (
    id UUID PRIMARY KEY,
    from_term_id UUID NOT NULL REFERENCES knowledge_term(id) ON DELETE CASCADE,
    relation_type VARCHAR(60) NOT NULL,
    to_term_id UUID NOT NULL REFERENCES knowledge_term(id) ON DELETE CASCADE,
    status VARCHAR(40) NOT NULL DEFAULT 'APPROVED',
    source_id UUID REFERENCES knowledge_source(id) ON DELETE SET NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_knowledge_relation_not_self
        CHECK (from_term_id <> to_term_id),
    CONSTRAINT chk_knowledge_relation_metadata_object
        CHECK (jsonb_typeof(metadata) = 'object'),
    CONSTRAINT ux_knowledge_relation
        UNIQUE (from_term_id, relation_type, to_term_id)
);

CREATE INDEX idx_knowledge_relation_from
    ON knowledge_relation(from_term_id, relation_type);

CREATE INDEX idx_knowledge_relation_to
    ON knowledge_relation(to_term_id, relation_type);

CREATE TABLE knowledge_candidate (
    id UUID PRIMARY KEY,
    candidate_type VARCHAR(60) NOT NULL,
    origin VARCHAR(40) NOT NULL,
    raw_input TEXT NOT NULL,
    normalized_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    proposed_term_code VARCHAR(120),
    proposed_name_fa VARCHAR(300),
    proposed_name_en VARCHAR(300),
    source_id UUID REFERENCES knowledge_source(id) ON DELETE SET NULL,
    source_url VARCHAR(3000),
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING_REVIEW',
    confidence NUMERIC(6,5),
    reason TEXT,
    review_note TEXT,
    created_by VARCHAR(150) NOT NULL,
    reviewed_by VARCHAR(150),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_knowledge_candidate_payload_object
        CHECK (jsonb_typeof(normalized_payload) = 'object'),
    CONSTRAINT chk_knowledge_candidate_confidence
        CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);

CREATE INDEX idx_knowledge_candidate_review_queue
    ON knowledge_candidate(status, created_at);

CREATE INDEX idx_knowledge_candidate_origin
    ON knowledge_candidate(origin, candidate_type);

CREATE TABLE knowledge_term_revision (
    id UUID PRIMARY KEY,
    term_id UUID NOT NULL REFERENCES knowledge_term(id) ON DELETE CASCADE,
    revision_no INTEGER NOT NULL,
    snapshot JSONB NOT NULL,
    change_reason VARCHAR(500),
    changed_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_knowledge_revision_positive
        CHECK (revision_no > 0),
    CONSTRAINT chk_knowledge_revision_snapshot_object
        CHECK (jsonb_typeof(snapshot) = 'object'),
    CONSTRAINT ux_knowledge_term_revision
        UNIQUE (term_id, revision_no)
);

CREATE INDEX idx_knowledge_revision_term
    ON knowledge_term_revision(term_id, revision_no DESC);