-- SakhtYar Phase 5.3 - Crawler & Ingestion
-- Reuses knowledge_source as provenance/trust source of truth.
-- Crawler output is stored as evidence and creates PENDING_REVIEW knowledge candidates only.

CREATE TABLE crawl_source (
    id UUID PRIMARY KEY,
    knowledge_source_id UUID NOT NULL REFERENCES knowledge_source(id) ON DELETE CASCADE,
    name VARCHAR(300) NOT NULL,
    seed_url VARCHAR(3000) NOT NULL,
    allowed_host VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    max_depth INTEGER NOT NULL DEFAULT 1,
    max_pages INTEGER NOT NULL DEFAULT 25,
    request_delay_ms INTEGER NOT NULL DEFAULT 500,
    timeout_ms INTEGER NOT NULL DEFAULT 15000,
    respect_robots BOOLEAN NOT NULL DEFAULT TRUE,
    user_agent VARCHAR(500) NOT NULL DEFAULT 'SakhtYarCrawler/1.0',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_crawl_source_depth CHECK (max_depth >= 0 AND max_depth <= 10),
    CONSTRAINT chk_crawl_source_pages CHECK (max_pages > 0 AND max_pages <= 1000),
    CONSTRAINT chk_crawl_source_delay CHECK (request_delay_ms >= 0),
    CONSTRAINT chk_crawl_source_timeout CHECK (timeout_ms >= 1000),
    CONSTRAINT chk_crawl_source_metadata_object CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE INDEX idx_crawl_source_enabled
    ON crawl_source(enabled, knowledge_source_id);

CREATE TABLE crawl_job (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES crawl_source(id) ON DELETE CASCADE,
    status VARCHAR(40) NOT NULL DEFAULT 'QUEUED',
    started_by VARCHAR(150) NOT NULL,
    pages_discovered INTEGER NOT NULL DEFAULT 0,
    pages_fetched INTEGER NOT NULL DEFAULT 0,
    pages_changed INTEGER NOT NULL DEFAULT 0,
    pages_failed INTEGER NOT NULL DEFAULT 0,
    candidates_created INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_crawl_job_source
    ON crawl_job(source_id, created_at DESC);

CREATE TABLE crawl_url (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES crawl_job(id) ON DELETE CASCADE,
    url VARCHAR(3000) NOT NULL,
    url_hash VARCHAR(64) NOT NULL,
    depth INTEGER NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'DISCOVERED',
    http_status INTEGER,
    error_message TEXT,
    discovered_at TIMESTAMPTZ NOT NULL,
    fetched_at TIMESTAMPTZ,
    CONSTRAINT chk_crawl_url_depth CHECK (depth >= 0),
    CONSTRAINT ux_crawl_url_job_hash UNIQUE(job_id, url_hash)
);

CREATE INDEX idx_crawl_url_job_status
    ON crawl_url(job_id, status);

CREATE TABLE crawled_document (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES crawl_source(id) ON DELETE CASCADE,
    job_id UUID NOT NULL REFERENCES crawl_job(id) ON DELETE CASCADE,
    source_url VARCHAR(3000) NOT NULL,
    canonical_url VARCHAR(3000) NOT NULL,
    canonical_url_hash VARCHAR(64) NOT NULL,
    content_type VARCHAR(300),
    document_kind VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    state VARCHAR(40) NOT NULL DEFAULT 'DISCOVERED',
    http_status INTEGER,
    title VARCHAR(1000),
    content_hash VARCHAR(64) NOT NULL,
    extracted_text TEXT,
    etag VARCHAR(1000),
    last_modified VARCHAR(1000),
    knowledge_candidate_id UUID REFERENCES knowledge_candidate(id) ON DELETE SET NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_crawled_document_metadata_object CHECK (jsonb_typeof(metadata) = 'object'),
    CONSTRAINT ux_crawled_document_version UNIQUE(source_id, canonical_url_hash, content_hash)
);

CREATE INDEX idx_crawled_document_source_url
    ON crawled_document(source_id, canonical_url_hash, observed_at DESC);

CREATE INDEX idx_crawled_document_job
    ON crawled_document(job_id, state);

CREATE INDEX idx_crawled_document_candidate
    ON crawled_document(knowledge_candidate_id)
    WHERE knowledge_candidate_id IS NOT NULL;