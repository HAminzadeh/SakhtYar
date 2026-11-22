-- SakhtYar Unified Knowledge Platform - governance/control plane.
-- Prepared document knowledge is imported in the next dataset step.

create table if not exists knowledge_pipeline_profile (
 id uuid primary key default gen_random_uuid(), code varchar(100) not null unique,
 name varchar(250) not null, repository varchar(300) not null, branch_name varchar(120) not null,
 source_path varchar(500) not null, runbook_version varchar(80) not null, runbook_path varchar(500) not null,
 default_mode varchar(30) not null default 'INCREMENTAL', enabled boolean not null default true,
 settings jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);

create table if not exists knowledge_dataset (
 id uuid primary key default gen_random_uuid(), dataset_version varchar(80) not null unique,
 taxonomy_version varchar(80), schema_version varchar(80) not null default '1', source_commit varchar(100), baseline_commit varchar(100),
 extraction_version varchar(100), embedding_model varchar(200), embedding_version varchar(100), status varchar(40) not null default 'PREPARED',
 manifest_sha256 varchar(64), manifest jsonb not null default '{}'::jsonb, statistics jsonb not null default '{}'::jsonb,
 generated_at timestamptz, imported_at timestamptz, activated_at timestamptz, archived_at timestamptz, created_at timestamptz not null default now()
);
create unique index if not exists ux_knowledge_dataset_single_active on knowledge_dataset ((status)) where status='ACTIVE';

create table if not exists knowledge_pipeline_run (
 id uuid primary key default gen_random_uuid(), run_code varchar(100) not null unique,
 profile_id uuid references knowledge_pipeline_profile(id), mode varchar(30) not null,
 status varchar(40) not null default 'COMMAND_GENERATED', stage varchar(80) not null default 'COMMAND_GENERATED',
 baseline_commit varchar(100), source_commit varchar(100), previous_dataset_version varchar(80), target_dataset_version varchar(80),
 command_text text not null, runbook_version varchar(80) not null, requested_by varchar(200), started_at timestamptz, finished_at timestamptz,
 error_message text, statistics jsonb not null default '{}'::jsonb, created_at timestamptz not null default now()
);
create index if not exists ix_knowledge_pipeline_run_created on knowledge_pipeline_run(created_at desc);

create table if not exists knowledge_dataset_artifact (
 id uuid primary key default gen_random_uuid(), dataset_id uuid not null references knowledge_dataset(id) on delete cascade,
 artifact_type varchar(80) not null, relative_path varchar(700) not null, sha256 varchar(64), size_bytes bigint,
 metadata jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(), unique(dataset_id,artifact_type,relative_path)
);

create table if not exists knowledge_review_batch (
 id uuid primary key default gen_random_uuid(), dataset_id uuid references knowledge_dataset(id), batch_type varchar(60) not null,
 title varchar(300) not null, domain varchar(100), category varchar(120), authority varchar(100), confidence_band varchar(30),
 status varchar(40) not null default 'PENDING_REVIEW', total_items integer not null default 0, approved_items integer not null default 0,
 rejected_items integer not null default 0, metadata jsonb not null default '{}'::jsonb,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table if not exists knowledge_review_item (
 id uuid primary key default gen_random_uuid(), batch_id uuid not null references knowledge_review_batch(id) on delete cascade,
 item_type varchar(60) not null, external_key varchar(300) not null, title varchar(500), proposed_domain varchar(120),
 proposed_category varchar(150), proposed_authority varchar(120), confidence numeric(7,6), status varchar(40) not null default 'PENDING_REVIEW',
 payload jsonb not null default '{}'::jsonb, reviewed_by varchar(200), reviewed_at timestamptz, review_note text, unique(batch_id,external_key)
);
create index if not exists ix_knowledge_review_item_batch_status on knowledge_review_item(batch_id,status);

create table if not exists knowledge_source_policy (
 id uuid primary key default gen_random_uuid(), source_code varchar(150) not null unique, priority integer not null default 100,
 authority varchar(100), trust_status varchar(60), watch_for_updates boolean not null default false, stale_after_days integer,
 security_classification varchar(60) not null default 'INTERNAL', license_code varchar(120), redistribution_allowed boolean,
 display_full_text_allowed boolean, download_allowed boolean, policy jsonb not null default '{}'::jsonb, updated_at timestamptz not null default now()
);

create table if not exists knowledge_feedback (
 id uuid primary key default gen_random_uuid(), target_type varchar(60) not null, target_id uuid, feedback_type varchar(60) not null,
 rating integer, comment text, status varchar(40) not null default 'OPEN', created_by varchar(200), created_at timestamptz not null default now(),
 resolved_by varchar(200), resolved_at timestamptz, resolution_note text
);

create table if not exists knowledge_usage_event (
 id bigserial primary key, event_type varchar(80) not null, actor varchar(200), agent_code varchar(120), target_type varchar(60), target_id uuid,
 query_text text, metadata jsonb not null default '{}'::jsonb, created_at timestamptz not null default now()
);
create index if not exists ix_knowledge_usage_event_created on knowledge_usage_event(created_at desc);

create table if not exists knowledge_golden_question (
 id uuid primary key default gen_random_uuid(), code varchar(120) not null unique, question_fa text not null, question_en text,
 expected_domains jsonb not null default '[]'::jsonb, expected_source_keys jsonb not null default '[]'::jsonb,
 expected_concepts jsonb not null default '[]'::jsonb, minimum_score numeric(7,6), enabled boolean not null default true,
 created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);

create table if not exists knowledge_quality_run (
 id uuid primary key default gen_random_uuid(), dataset_id uuid references knowledge_dataset(id), run_type varchar(60) not null,
 status varchar(40) not null, score numeric(9,6), citation_coverage numeric(9,6), details jsonb not null default '{}'::jsonb,
 created_at timestamptz not null default now(), finished_at timestamptz
);

create table if not exists knowledge_conflict (
 id uuid primary key default gen_random_uuid(), dataset_id uuid references knowledge_dataset(id), conflict_key varchar(300) not null,
 conflict_type varchar(80) not null, severity varchar(30) not null default 'MEDIUM', status varchar(40) not null default 'OPEN',
 left_evidence jsonb not null default '{}'::jsonb, right_evidence jsonb not null default '{}'::jsonb,
 suggested_resolution jsonb not null default '{}'::jsonb, resolved_by varchar(200), resolved_at timestamptz, resolution_note text,
 created_at timestamptz not null default now()
);
create index if not exists ix_knowledge_conflict_status on knowledge_conflict(status,severity);

create table if not exists knowledge_document_diff (
 id uuid primary key default gen_random_uuid(), document_key varchar(300) not null, old_version_key varchar(300), new_version_key varchar(300) not null,
 change_summary jsonb not null default '{}'::jsonb, changed_sections jsonb not null default '[]'::jsonb, created_at timestamptz not null default now()
);
create table if not exists knowledge_impact_analysis (
 id uuid primary key default gen_random_uuid(), change_type varchar(80) not null, changed_object_type varchar(80) not null,
 changed_object_key varchar(300) not null, impacted_agents jsonb not null default '[]'::jsonb, impacted_rules jsonb not null default '[]'::jsonb,
 impacted_terms jsonb not null default '[]'::jsonb, impacted_calculations jsonb not null default '[]'::jsonb,
 details jsonb not null default '{}'::jsonb, created_at timestamptz not null default now()
);
create table if not exists knowledge_pinned_item (
 id uuid primary key default gen_random_uuid(), target_type varchar(60) not null, target_key varchar(300) not null,
 scope varchar(80) not null default 'GLOBAL', agent_code varchar(120), priority integer not null default 100, reason text,
 enabled boolean not null default true, created_at timestamptz not null default now(), unique(target_type,target_key,scope,agent_code)
);
create table if not exists knowledge_search_regression_case (
 id uuid primary key default gen_random_uuid(), code varchar(120) not null unique, query_text text not null, locale varchar(20) not null default 'fa',
 expected_keys jsonb not null default '[]'::jsonb, filters jsonb not null default '{}'::jsonb, enabled boolean not null default true,
 created_at timestamptz not null default now()
);
create table if not exists knowledge_agent_snapshot (
 id uuid primary key default gen_random_uuid(), dataset_id uuid references knowledge_dataset(id), agent_code varchar(120) not null,
 snapshot_version varchar(80) not null, domain_filter jsonb not null default '[]'::jsonb, authority_filter jsonb not null default '[]'::jsonb,
 jurisdiction_filter jsonb not null default '{}'::jsonb, status varchar(40) not null default 'PREPARED', created_at timestamptz not null default now(),
 unique(agent_code,snapshot_version)
);
create table if not exists knowledge_manual_override (
 id uuid primary key default gen_random_uuid(), target_type varchar(80) not null, target_key varchar(300) not null, field_name varchar(150) not null,
 old_value jsonb, new_value jsonb, reason text not null, changed_by varchar(200) not null, changed_at timestamptz not null default now()
);

insert into knowledge_pipeline_profile(code,name,repository,branch_name,source_path,runbook_version,runbook_path,default_mode,settings)
values('SAKHTYAR_INTERNAL_KNOWLEDGE','SakhtYar Internal Knowledge Extraction','HAminzadeh/SakhtYar','v2',
'DocumentationOfLawsAndRegulations','1.0','docs/knowledge/runbooks/knowledge-extraction-v1.md','INCREMENTAL',
jsonb_build_object('preparedDatasetOnly',true,'localOcrAllowed',false,'localLlmExtractionAllowed',false,
'localEmbeddingGenerationAllowed',false,'bulkReviewEnabled',true,'dryRunRequired',true,'activationRequired',true))
on conflict(code) do nothing;