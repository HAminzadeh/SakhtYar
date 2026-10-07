-- SakhtYar Knowledge Closure v0.26.0
-- Additive only. V42/V43 and published releases remain immutable.

create unique index if not exists ux_knowledge_extraction_quality_execution_rule_fact
    on knowledge_extraction_quality(execution_id, rule_id, fact_id);

create index if not exists ix_knowledge_extraction_quality_source_page
    on knowledge_extraction_quality(source_node_id, page_number);

create index if not exists ix_knowledge_semantic_relation_rules
    on knowledge_semantic_relation(knowledge_version_id, from_rule_id, to_rule_id);

-- Repair catalog workflow state only for unmatched candidates.
-- A candidate is not validated merely because its confidence is high.
update construction_catalog_candidate
set status='CANDIDATE'
where status='VALIDATED' and matched_concept_id is null;