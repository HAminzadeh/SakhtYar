-- Required PostgreSQL extensions for Knowledge Dataset import/search indexes.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
create table if not exists knowledge_dataset_package (
    id uuid primary key default gen_random_uuid(),
    original_filename varchar(500) not null,
    object_key varchar(900) not null unique,
    sha256 varchar(64) not null,
    size_bytes bigint not null,
    content_type varchar(200),
    dataset_version varchar(100),
    status varchar(50) not null default 'UPLOADED',
    manifest jsonb not null default '{}'::jsonb,
    validation_report jsonb not null default '{}'::jsonb,
    dry_run_report jsonb not null default '{}'::jsonb,
    imported_dataset_id uuid references knowledge_dataset(id),
    error_message text,
    uploaded_by varchar(200),
    uploaded_at timestamptz not null default now(),
    validated_at timestamptz,
    import_started_at timestamptz,
    imported_at timestamptz,
    updated_at timestamptz not null default now()
);

create index if not exists ix_knowledge_dataset_package_sha
    on knowledge_dataset_package(sha256);
create index if not exists ix_knowledge_dataset_package_created
    on knowledge_dataset_package(uploaded_at desc);

create table if not exists knowledge_dataset_package_event (
    id bigserial primary key,
    package_id uuid not null references knowledge_dataset_package(id) on delete cascade,
    event_type varchar(80) not null,
    status varchar(50),
    message text,
    details jsonb not null default '{}'::jsonb,
    actor varchar(200),
    created_at timestamptz not null default now()
);

create index if not exists ix_knowledge_dataset_package_event_package
    on knowledge_dataset_package_event(package_id, created_at desc);

create table if not exists knowledge_document (
    id uuid primary key,
    dataset_id uuid not null references knowledge_dataset(id) on delete cascade,
    document_key varchar(128) not null,
    title text,
    canonical_path text not null,
    filename text not null,
    sha256 varchar(64) not null,
    extension varchar(20),
    size_bytes bigint,
    page_count integer,
    text_chars bigint,
    domain varchar(120),
    classification_confidence numeric(8,6),
    authority varchar(120),
    trust varchar(60),
    jurisdiction jsonb not null default '{}'::jsonb,
    extraction_quality varchar(60),
    summary_short text,
    summary_detailed text,
    topics jsonb not null default '[]'::jsonb,
    entities jsonb not null default '[]'::jsonb,
    agents jsonb not null default '[]'::jsonb,
    review_status varchar(40) not null default 'PENDING_REVIEW',
    security_classification varchar(60) not null default 'PUBLIC',
    source_representation varchar(80),
    text_hash varchar(64),
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    unique(dataset_id, document_key)
);

create table if not exists knowledge_document_source_location (
    id uuid primary key,
    dataset_id uuid not null references knowledge_dataset(id) on delete cascade,
    document_id uuid not null references knowledge_document(id) on delete cascade,
    path text not null,
    is_canonical boolean not null default false,
    source_folder text,
    source_type varchar(80),
    unique(dataset_id, path)
);

create table if not exists knowledge_chunk (
    id uuid primary key,
    dataset_id uuid not null references knowledge_dataset(id) on delete cascade,
    document_id uuid not null references knowledge_document(id) on delete cascade,
    chunk_index integer not null,
    page_from integer,
    page_to integer,
    normalized_text text not null,
    text_hash varchar(64),
    summary text,
    keywords jsonb not null default '[]'::jsonb,
    topics jsonb not null default '[]'::jsonb,
    entities jsonb not null default '[]'::jsonb,
    legal_refs jsonb not null default '[]'::jsonb,
    questions_answered jsonb not null default '[]'::jsonb,
    confidence numeric(8,6),
    provenance jsonb not null default '{}'::jsonb,
    embedding vector,
    unique(dataset_id, document_id, chunk_index)
);

create index if not exists ix_knowledge_chunk_dataset_document
    on knowledge_chunk(dataset_id, document_id);
create index if not exists ix_knowledge_chunk_fts
    on knowledge_chunk using gin(to_tsvector('simple', normalized_text));
create index if not exists ix_knowledge_chunk_trgm
    on knowledge_chunk using gin(normalized_text gin_trgm_ops);

create table if not exists knowledge_evidence (
    id uuid primary key,
    dataset_id uuid not null references knowledge_dataset(id) on delete cascade,
    document_id uuid not null references knowledge_document(id) on delete cascade,
    chunk_id uuid not null references knowledge_chunk(id) on delete cascade,
    evidence_type varchar(60) not null,
    page_number integer,
    quote_text text not null,
    confidence numeric(8,6),
    provenance jsonb not null default '{}'::jsonb
);

create table if not exists knowledge_assertion_candidate (
    id uuid primary key,
    dataset_id uuid not null references knowledge_dataset(id) on delete cascade,
    document_id uuid not null references knowledge_document(id) on delete cascade,
    evidence_id uuid not null references knowledge_evidence(id) on delete cascade,
    candidate_type varchar(60) not null,
    statement text not null,
    statement_hash varchar(64) not null,
    domain varchar(120),
    authority varchar(120),
    trust varchar(60),
    confidence numeric(8,6),
    status varchar(40) not null default 'PENDING_REVIEW',
    topics jsonb not null default '[]'::jsonb,
    provenance jsonb not null default '{}'::jsonb,
    unique(dataset_id, statement_hash)
);

create table if not exists knowledge_rule_candidate (
    id uuid primary key,
    dataset_id uuid not null references knowledge_dataset(id) on delete cascade,
    document_id uuid not null references knowledge_document(id) on delete cascade,
    evidence_id uuid not null references knowledge_evidence(id) on delete cascade,
    statement text not null,
    statement_hash varchar(64) not null,
    domain varchar(120),
    rule_type varchar(80),
    legal_refs jsonb not null default '[]'::jsonb,
    authority varchar(120),
    trust varchar(60),
    jurisdiction jsonb not null default '{}'::jsonb,
    effective_from date,
    effective_to date,
    confidence numeric(8,6),
    status varchar(40) not null default 'PENDING_REVIEW',
    bulk_approvable boolean not null default false,
    provenance jsonb not null default '{}'::jsonb,
    unique(dataset_id, statement_hash)
);

create table if not exists knowledge_document_relation (
    id uuid primary key,
    dataset_id uuid not null references knowledge_dataset(id) on delete cascade,
    from_document_id uuid not null references knowledge_document(id) on delete cascade,
    to_document_id uuid not null references knowledge_document(id) on delete cascade,
    relation_type varchar(80) not null,
    confidence numeric(8,6)
);

create table if not exists knowledge_embedding_model (
    dataset_id uuid primary key references knowledge_dataset(id) on delete cascade,
    model_code varchar(200) not null,
    dimension integer not null,
    metadata jsonb not null default '{}'::jsonb
);