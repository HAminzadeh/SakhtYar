-- SakhtYar final knowledge processing runtime v0.19.0
-- V34-V40 remain immutable.
create table if not exists knowledge_execution_version (
 execution_id uuid primary key references knowledge_intake_execution(id) on delete cascade,
 catalog_version_id uuid references construction_catalog_version(id),
 knowledge_version_id uuid references structured_knowledge_version(id),
 graph_version_id uuid references knowledge_graph_version(id),
 graphrag_version_id uuid references knowledge_graphrag_version(id),
 release_id uuid references knowledge_release(id),
 updated_at timestamptz not null default now()
);
create index if not exists ix_ksa_doc_page_type on knowledge_source_artifact(document_id,page_from,artifact_type);
create index if not exists ix_ksn_artifact_page on knowledge_source_node(artifact_id,page_from,node_type);
alter table knowledge_source_artifact add column if not exists execution_id uuid;
alter table knowledge_source_node add column if not exists execution_id uuid;
create index if not exists ix_ksa_execution on knowledge_source_artifact(execution_id,artifact_type);
create index if not exists ix_ksn_execution on knowledge_source_node(execution_id,node_type);
