create table if not exists knowledge_intake_execution (
 id uuid primary key default gen_random_uuid(),
 selection_version_id uuid references knowledge_intake_selection_version(id),
 run_id uuid references knowledge_pipeline_run(id) on delete cascade,
 workflow_id uuid,
 correlation_id uuid,
 status varchar(40) not null default 'QUEUED',
 total_documents integer not null default 0,
 completed_documents integer not null default 0,
 failed_documents integer not null default 0,
 current_stage varchar(80),
 statistics jsonb not null default '{}'::jsonb,
 error_message text,
 requested_by varchar(160) not null default 'system',
 created_at timestamptz not null default now(),
 started_at timestamptz,
 finished_at timestamptz
);
create index if not exists ix_knowledge_intake_execution_run on knowledge_intake_execution(run_id,created_at desc);
create index if not exists ix_knowledge_intake_execution_status on knowledge_intake_execution(status,created_at desc);

alter table knowledge_pipeline_run_document add column if not exists selected_by_user boolean not null default false;
alter table knowledge_pipeline_run_document add column if not exists selection_version_id uuid references knowledge_intake_selection_version(id);