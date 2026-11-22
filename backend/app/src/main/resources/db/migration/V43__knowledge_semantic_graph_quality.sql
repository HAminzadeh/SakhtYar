-- SakhtYar semantic graph quality layer.
create table if not exists knowledge_semantic_relation (
    id uuid primary key,
    knowledge_version_id uuid not null references structured_knowledge_version(id),
    from_rule_id uuid references knowledge_rule(id),
    to_rule_id uuid references knowledge_rule(id),
    relation_type varchar(80) not null,
    confidence numeric(8,5) not null,
    source_node_id uuid references knowledge_source_node(id),
    properties_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    unique(knowledge_version_id,from_rule_id,relation_type,to_rule_id)
);
create index if not exists ix_knowledge_semantic_relation_version on knowledge_semantic_relation(knowledge_version_id);
create index if not exists ix_knowledge_semantic_relation_source on knowledge_semantic_relation(source_node_id);