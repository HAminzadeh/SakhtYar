-- SakhtYar Knowledge Versioned Outputs v0.9
-- Append-only output/artifact ledger. Existing V31-V33 tables remain intact.

create table if not exists knowledge_output_version (
 id uuid primary key default gen_random_uuid(),
 workflow_id uuid,
 run_id uuid not null references knowledge_pipeline_run(id) on delete cascade,
 source_document_id uuid references knowledge_source_document(id),
 page_id uuid references knowledge_source_page(id),
 node_id uuid references knowledge_document_node(id),
 correlation_id uuid,
 stage_code varchar(80) not null,
 output_type varchar(80) not null,
 logical_key varchar(300) not null,
 version_no integer not null,
 pipeline_version varchar(100) not null,
 producer varchar(160),
 producer_version varchar(120),
 model_name varchar(240),
 model_revision varchar(160),
 runtime_info jsonb not null default '{}'::jsonb,
 content_hash varchar(64),
 payload jsonb not null default '{}'::jsonb,
 text_payload text,
 artifact_path text,
 artifact_sha256 varchar(64),
 artifact_size_bytes bigint,
 status varchar(40) not null default 'CURRENT',
 supersedes_id uuid references knowledge_output_version(id),
 provenance jsonb not null default '{}'::jsonb,
 created_by varchar(160) not null default 'system',
 created_at timestamptz not null default now(),
 constraint uq_knowledge_output_version unique(run_id,stage_code,output_type,logical_key,version_no),
 constraint ck_knowledge_output_status check(status in ('CURRENT','SUPERSEDED','FAILED','REJECTED','ARCHIVED'))
);
create unique index if not exists uq_knowledge_output_current
 on knowledge_output_version(run_id,stage_code,output_type,logical_key)
 where status='CURRENT';
create index if not exists ix_knowledge_output_document
 on knowledge_output_version(source_document_id,stage_code,output_type,version_no desc);
create index if not exists ix_knowledge_output_correlation
 on knowledge_output_version(correlation_id,created_at desc);
create index if not exists ix_knowledge_output_hash
 on knowledge_output_version(content_hash);

create table if not exists knowledge_intake_selection_version (
 id uuid primary key default gen_random_uuid(),
 workflow_id uuid,
 run_id uuid references knowledge_pipeline_run(id) on delete cascade,
 selection_key varchar(160) not null,
 version_no integer not null,
 status varchar(40) not null default 'CURRENT',
 selected_document_ids jsonb not null default '[]'::jsonb,
 processing_policy jsonb not null default '{}'::jsonb,
 source_report_hash varchar(64),
 supersedes_id uuid references knowledge_intake_selection_version(id),
 selected_by varchar(160) not null default 'system',
 created_at timestamptz not null default now(),
 constraint uq_knowledge_selection_version unique(selection_key,version_no),
 constraint ck_knowledge_selection_status check(status in ('CURRENT','SUPERSEDED','ARCHIVED'))
);
create unique index if not exists uq_knowledge_selection_current
 on knowledge_intake_selection_version(selection_key) where status='CURRENT';

create or replace view knowledge_current_output as
 select * from knowledge_output_version where status='CURRENT';

comment on table knowledge_output_version is
 'Append-only version history for extraction/OCR/normalization/correction/entity/quality/export outputs.';
comment on table knowledge_intake_selection_version is
 'Version history of Wizard document selections and processing policies.';
