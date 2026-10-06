-- SakhtYar v0.24.0
-- Additive quality metadata only. Existing published releases remain immutable.
create table if not exists knowledge_extraction_quality (
    id uuid primary key,
    execution_id uuid not null references knowledge_intake_execution(id),
    rule_id uuid references knowledge_rule(id),
    fact_id uuid references knowledge_fact(id),
    source_node_id uuid references knowledge_source_node(id),
    evidence_status varchar(40) not null,
    evidence_text text,
    page_number integer,
    confidence numeric(8,5),
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);
create index if not exists ix_knowledge_extraction_quality_execution on knowledge_extraction_quality(execution_id);
create index if not exists ix_knowledge_extraction_quality_rule on knowledge_extraction_quality(rule_id);

-- Candidates remain reviewable; publishing a release must not imply catalog validation.
update construction_catalog_candidate
set status='CANDIDATE'
where status='VALIDATED' and matched_concept_id is null;