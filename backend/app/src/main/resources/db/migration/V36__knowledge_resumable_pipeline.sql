alter table knowledge_intake_execution add column if not exists source_root text;
alter table knowledge_intake_execution add column if not exists stop_requested boolean not null default false;
alter table knowledge_intake_execution add column if not exists heartbeat_at timestamptz;
alter table knowledge_intake_execution add column if not exists resumed_at timestamptz;

create table if not exists knowledge_pipeline_checkpoint (
 id uuid primary key default gen_random_uuid(),
 execution_id uuid not null references knowledge_intake_execution(id) on delete cascade,
 run_id uuid not null references knowledge_pipeline_run(id) on delete cascade,
 stage_code varchar(80) not null,
 scope_type varchar(20) not null default 'STAGE',
 scope_key varchar(500) not null default '_',
 status varchar(30) not null default 'PENDING',
 attempt integer not null default 0,
 input_hash varchar(64),
 output_version integer,
 processed_count integer not null default 0,
 failed_count integer not null default 0,
 skipped_count integer not null default 0,
 cursor_json jsonb not null default '{}'::jsonb,
 error_message text,
 started_at timestamptz,
 heartbeat_at timestamptz,
 finished_at timestamptz,
 updated_at timestamptz not null default now(),
 unique(execution_id,stage_code,scope_type,scope_key)
);
create index if not exists ix_kpc_exec_stage on knowledge_pipeline_checkpoint(execution_id,stage_code,status);
create index if not exists ix_kpc_run on knowledge_pipeline_checkpoint(run_id,updated_at desc);

create table if not exists knowledge_intake_inventory (
 id uuid primary key default gen_random_uuid(),
 execution_id uuid not null references knowledge_intake_execution(id) on delete cascade,
 run_id uuid not null references knowledge_pipeline_run(id) on delete cascade,
 relative_path text not null,
 absolute_path text not null,
 sha256 varchar(64),
 size_bytes bigint not null default 0,
 page_count integer not null default 0,
 native_chars bigint not null default 0,
 suspicious_pages jsonb not null default '[]'::jsonb,
 intake_status varchar(40) not null default 'DISCOVERED',
 reason text,
 selected_by_user boolean not null default false,
 document_id uuid,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 unique(execution_id,relative_path)
);
create index if not exists ix_kii_execution_status on knowledge_intake_inventory(execution_id,intake_status);
create index if not exists ix_kii_sha on knowledge_intake_inventory(sha256);

create table if not exists knowledge_resumable_pipeline_event (
 id bigserial primary key,
 execution_id uuid not null references knowledge_intake_execution(id) on delete cascade,
 run_id uuid not null references knowledge_pipeline_run(id) on delete cascade,
 stage_code varchar(80),
 event_type varchar(50) not null,
 message text,
 payload jsonb not null default '{}'::jsonb,
 created_at timestamptz not null default now()
);
create index if not exists ix_kpe_execution on knowledge_resumable_pipeline_event(execution_id,id desc);