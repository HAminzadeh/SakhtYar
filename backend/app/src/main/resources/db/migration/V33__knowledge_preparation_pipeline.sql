-- SakhtYar Knowledge Preparation Pipeline v1
-- Non-destructive extension of V31/V32.

alter table knowledge_pipeline_run add column if not exists workflow_id uuid;
alter table knowledge_pipeline_run add column if not exists correlation_id uuid;
alter table knowledge_pipeline_run add column if not exists pipeline_version varchar(100);
create index if not exists ix_knowledge_pipeline_run_workflow on knowledge_pipeline_run(workflow_id, created_at desc);

create table if not exists knowledge_source_document (
 id uuid primary key default gen_random_uuid(), correlation_id uuid not null,
 canonical_path text not null, filename text not null, extension varchar(30), sha256 varchar(64) not null,
 size_bytes bigint, document_version integer not null default 1,
 previous_document_id uuid references knowledge_source_document(id), page_count integer, text_chars bigint,
 source_representation varchar(80), extraction_version varchar(100), status varchar(40) not null default 'DISCOVERED',
 metadata jsonb not null default '{}'::jsonb, discovered_at timestamptz not null default now(), prepared_at timestamptz,
 unique(canonical_path, document_version), unique(sha256, canonical_path)
);
create index if not exists ix_knowledge_source_document_corr on knowledge_source_document(correlation_id, document_version desc);
create index if not exists ix_knowledge_source_document_sha on knowledge_source_document(sha256);

create table if not exists knowledge_pipeline_run_document (
 id uuid primary key default gen_random_uuid(), run_id uuid not null references knowledge_pipeline_run(id) on delete cascade,
 source_document_id uuid not null references knowledge_source_document(id), correlation_id uuid not null,
 status varchar(40) not null default 'DISCOVERED', reused boolean not null default false,
 statistics jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(), finished_at timestamptz,
 unique(run_id, source_document_id)
);

create table if not exists knowledge_pipeline_step_run (
 id uuid primary key default gen_random_uuid(), run_id uuid not null references knowledge_pipeline_run(id) on delete cascade,
 source_document_id uuid references knowledge_source_document(id), step_code varchar(80) not null,
 status varchar(40) not null default 'PENDING', attempt integer not null default 1,
 input_hash varchar(64), output_hash varchar(64), statistics jsonb not null default '{}'::jsonb,
 started_at timestamptz, finished_at timestamptz, error_message text, created_at timestamptz not null default now(),
 unique(run_id, source_document_id, step_code, attempt)
);
create index if not exists ix_knowledge_step_run_status on knowledge_pipeline_step_run(run_id,status,step_code);

create table if not exists knowledge_pipeline_event (
 id bigserial primary key, run_id uuid not null references knowledge_pipeline_run(id) on delete cascade,
 source_document_id uuid references knowledge_source_document(id), event_type varchar(80) not null,
 level varchar(20) not null default 'INFO', message text, details jsonb not null default '{}'::jsonb,
 created_at timestamptz not null default now()
);

create table if not exists knowledge_source_page (
 id uuid primary key default gen_random_uuid(), run_id uuid not null references knowledge_pipeline_run(id) on delete cascade,
 source_document_id uuid not null references knowledge_source_document(id), page_number integer not null,
 raw_text text, normalized_text text, text_hash varchar(64), char_count integer,
 metadata jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(),
 unique(run_id, source_document_id, page_number)
);

create table if not exists knowledge_document_node (
 id uuid primary key default gen_random_uuid(), run_id uuid not null references knowledge_pipeline_run(id) on delete cascade,
 source_document_id uuid not null references knowledge_source_document(id), parent_node_id uuid references knowledge_document_node(id),
 correlation_id uuid not null, node_index integer not null, node_type varchar(60) not null,
 heading text, normalized_text text not null, text_hash varchar(64) not null, simhash64 varchar(16),
 page_from integer, page_to integer, depth integer not null default 0, path text,
 information_density numeric(8,6), importance_score numeric(8,6), metadata jsonb not null default '{}'::jsonb,
 created_at timestamptz not null default now(), unique(run_id,source_document_id,node_index)
);
create index if not exists ix_knowledge_node_hash on knowledge_document_node(text_hash);
create index if not exists ix_knowledge_node_corr on knowledge_document_node(correlation_id);

create table if not exists knowledge_node_classification (
 id uuid primary key default gen_random_uuid(), run_id uuid not null references knowledge_pipeline_run(id) on delete cascade,
 node_id uuid not null references knowledge_document_node(id) on delete cascade,
 classification_type varchar(40) not null, label varchar(120) not null, confidence numeric(8,6) not null,
 classifier_version varchar(100) not null, metadata jsonb not null default '{}'::jsonb,
 unique(run_id,node_id,classification_type,label,classifier_version)
);

create table if not exists knowledge_node_entity (
 id uuid primary key default gen_random_uuid(), run_id uuid not null references knowledge_pipeline_run(id) on delete cascade,
 node_id uuid not null references knowledge_document_node(id) on delete cascade,
 entity_type varchar(80) not null, value text not null, normalized_value text, unit varchar(60),
 confidence numeric(8,6), start_offset integer, end_offset integer, metadata jsonb not null default '{}'::jsonb
);

create table if not exists knowledge_node_reference (
 id uuid primary key default gen_random_uuid(), run_id uuid not null references knowledge_pipeline_run(id) on delete cascade,
 node_id uuid not null references knowledge_document_node(id) on delete cascade,
 reference_type varchar(80) not null, reference_text text not null, normalized_reference text,
 target_source_document_id uuid references knowledge_source_document(id), confidence numeric(8,6), metadata jsonb not null default '{}'::jsonb
);

create table if not exists knowledge_dedup_cluster (
 id uuid primary key default gen_random_uuid(), run_id uuid not null references knowledge_pipeline_run(id) on delete cascade,
 cluster_type varchar(30) not null, representative_node_id uuid references knowledge_document_node(id),
 fingerprint varchar(128), similarity_threshold numeric(8,6), created_at timestamptz not null default now()
);
create table if not exists knowledge_dedup_member (
 cluster_id uuid not null references knowledge_dedup_cluster(id) on delete cascade,
 node_id uuid not null references knowledge_document_node(id) on delete cascade,
 similarity numeric(8,6) not null, is_representative boolean not null default false,
 primary key(cluster_id,node_id)
);

create table if not exists knowledge_preparation_candidate (
 id uuid primary key default gen_random_uuid(), run_id uuid not null references knowledge_pipeline_run(id) on delete cascade,
 source_document_id uuid not null references knowledge_source_document(id), node_id uuid not null references knowledge_document_node(id),
 correlation_id uuid not null, candidate_type varchar(80) not null, statement text not null, statement_hash varchar(64) not null,
 domain varchar(120), topics jsonb not null default '[]'::jsonb, applicability jsonb not null default '{}'::jsonb,
 authority jsonb not null default '{}'::jsonb, legal_refs jsonb not null default '[]'::jsonb,
 confidence numeric(8,6), importance_score numeric(8,6), status varchar(40) not null default 'PREPARED',
 provenance jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(),
 unique(run_id,node_id,statement_hash)
);

create table if not exists knowledge_lineage_edge (
 id uuid primary key default gen_random_uuid(), workflow_id uuid, run_id uuid references knowledge_pipeline_run(id) on delete cascade,
 correlation_id uuid not null, from_type varchar(60) not null, from_id uuid not null,
 to_type varchar(60) not null, to_id uuid not null, relation_type varchar(80) not null,
 metadata jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(),
 unique(run_id,from_type,from_id,to_type,to_id,relation_type)
);
create index if not exists ix_knowledge_lineage_corr on knowledge_lineage_edge(correlation_id);

create table if not exists knowledge_llm_export (
 id uuid primary key default gen_random_uuid(), workflow_id uuid not null, run_id uuid not null references knowledge_pipeline_run(id),
 export_version varchar(80) not null, package_type varchar(80) not null default 'SAKHTYAR_LLM_INPUT',
 schema_version varchar(40) not null default '1', status varchar(40) not null default 'CREATED',
 output_path text not null, sha256 varchar(64), size_bytes bigint, manifest jsonb not null default '{}'::jsonb,
 statistics jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(), unique(run_id,export_version)
);

-- Optional lineage bridge into V32 imported knowledge objects.
alter table knowledge_document add column if not exists workflow_id uuid;
alter table knowledge_document add column if not exists correlation_id uuid;
alter table knowledge_document add column if not exists source_document_id uuid references knowledge_source_document(id);
alter table knowledge_chunk add column if not exists correlation_id uuid;
alter table knowledge_chunk add column if not exists source_node_id uuid references knowledge_document_node(id);
alter table knowledge_evidence add column if not exists correlation_id uuid;
alter table knowledge_assertion_candidate add column if not exists correlation_id uuid;
alter table knowledge_rule_candidate add column if not exists correlation_id uuid;
