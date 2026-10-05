-- SakhtYar Knowledge Platform Foundation v0.18.1
CREATE TABLE IF NOT EXISTS knowledge_source_document (
 id uuid PRIMARY KEY, sha256 varchar(64) NOT NULL, original_name text NOT NULL, media_type varchar(120), storage_uri text,
 authority text, jurisdiction_code varchar(120), effective_from date, effective_to date, created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(sha256)
);
CREATE TABLE IF NOT EXISTS knowledge_source_artifact (
 id uuid PRIMARY KEY, document_id uuid NOT NULL REFERENCES knowledge_source_document(id), parent_artifact_id uuid REFERENCES knowledge_source_artifact(id),
 artifact_type varchar(40) NOT NULL, processor_id varchar(160), processor_version varchar(80), content_hash varchar(64) NOT NULL,
 content text, storage_uri text, page_from int, page_to int, quality_score numeric(6,5), metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ksa_document ON knowledge_source_artifact(document_id, artifact_type);
CREATE INDEX IF NOT EXISTS idx_ksa_content_fts ON knowledge_source_artifact USING gin (to_tsvector('simple', coalesce(content,'')));
CREATE TABLE IF NOT EXISTS knowledge_source_node (
 id uuid PRIMARY KEY, artifact_id uuid NOT NULL REFERENCES knowledge_source_artifact(id), parent_node_id uuid REFERENCES knowledge_source_node(id),
 node_type varchar(40) NOT NULL, ordinal int NOT NULL DEFAULT 0, title text, content text, page_from int, page_to int, metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX IF NOT EXISTS idx_ksn_parent ON knowledge_source_node(artifact_id,parent_node_id,ordinal);
CREATE INDEX IF NOT EXISTS idx_ksn_content_fts ON knowledge_source_node USING gin (to_tsvector('simple', coalesce(title,'') || ' ' || coalesce(content,'')));

CREATE TABLE IF NOT EXISTS knowledge_provenance_activity (
 id uuid PRIMARY KEY, activity_type varchar(80) NOT NULL, component_id varchar(160) NOT NULL, component_version varchar(80) NOT NULL,
 execution_id varchar(160), started_at timestamptz NOT NULL, finished_at timestamptz, config_hash varchar(64), model_id varchar(160), model_version varchar(120), status varchar(30) NOT NULL
);
CREATE TABLE IF NOT EXISTS knowledge_provenance_link (
 id uuid PRIMARY KEY, activity_id uuid REFERENCES knowledge_provenance_activity(id), relation_type varchar(40) NOT NULL,
 entity_type varchar(80) NOT NULL, entity_id uuid NOT NULL, related_entity_type varchar(80), related_entity_id uuid, created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS construction_catalog_version (
 id uuid PRIMARY KEY, version_no bigint NOT NULL UNIQUE, status varchar(30) NOT NULL, based_on_id uuid REFERENCES construction_catalog_version(id), content_hash varchar(64), created_at timestamptz NOT NULL DEFAULT now(), published_at timestamptz
);
CREATE TABLE IF NOT EXISTS construction_concept (
 id uuid PRIMARY KEY, stable_key varchar(180) NOT NULL, catalog_version_id uuid NOT NULL REFERENCES construction_catalog_version(id), canonical_name_fa text NOT NULL,
 canonical_name_en text, domain varchar(100), parent_concept_id uuid, data_type varchar(50), unit_type varchar(80), description text, status varchar(30) NOT NULL,
 provenance_node_id uuid REFERENCES knowledge_source_node(id), confidence numeric(6,5), UNIQUE(catalog_version_id,stable_key)
);
CREATE TABLE IF NOT EXISTS construction_concept_alias (
 id uuid PRIMARY KEY, concept_id uuid NOT NULL REFERENCES construction_concept(id), alias text NOT NULL, alias_type varchar(40) NOT NULL DEFAULT 'SYNONYM', normalized_alias text NOT NULL, confidence numeric(6,5), source_node_id uuid REFERENCES knowledge_source_node(id)
);
CREATE INDEX IF NOT EXISTS idx_cca_normalized ON construction_concept_alias(normalized_alias);
CREATE TABLE IF NOT EXISTS construction_concept_relation (
 id uuid PRIMARY KEY, from_concept_id uuid NOT NULL REFERENCES construction_concept(id), relation_type varchar(60) NOT NULL, to_concept_id uuid NOT NULL REFERENCES construction_concept(id), confidence numeric(6,5), source_node_id uuid REFERENCES knowledge_source_node(id)
);
CREATE TABLE IF NOT EXISTS construction_catalog_candidate (
 id uuid PRIMARY KEY, catalog_version_id uuid NOT NULL REFERENCES construction_catalog_version(id), normalized_term text NOT NULL, display_term text NOT NULL,
 candidate_type varchar(50) NOT NULL, matched_concept_id uuid REFERENCES construction_concept(id), occurrence_count int NOT NULL DEFAULT 1, confidence numeric(6,5), status varchar(30) NOT NULL DEFAULT 'DISCOVERED', evidence_json jsonb NOT NULL DEFAULT '[]'::jsonb
);

CREATE TABLE IF NOT EXISTS structured_knowledge_version (
 id uuid PRIMARY KEY, version_no bigint NOT NULL UNIQUE, catalog_version_id uuid NOT NULL REFERENCES construction_catalog_version(id), status varchar(30) NOT NULL, content_hash varchar(64), created_at timestamptz NOT NULL DEFAULT now(), published_at timestamptz
);
CREATE TABLE IF NOT EXISTS knowledge_fact (
 id uuid PRIMARY KEY, knowledge_version_id uuid NOT NULL REFERENCES structured_knowledge_version(id), fact_type varchar(50) NOT NULL, stable_key varchar(200),
 subject_concept_id uuid REFERENCES construction_concept(id), predicate varchar(100), object_concept_id uuid REFERENCES construction_concept(id), literal_value text,
 numeric_value numeric, unit_concept_id uuid REFERENCES construction_concept(id), jurisdiction_code varchar(120), valid_from date, valid_to date,
 confidence numeric(6,5) NOT NULL DEFAULT 1, status varchar(30) NOT NULL DEFAULT 'CANDIDATE', source_node_id uuid REFERENCES knowledge_source_node(id), metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX IF NOT EXISTS idx_kf_lookup ON knowledge_fact(knowledge_version_id,subject_concept_id,predicate,jurisdiction_code);
CREATE TABLE IF NOT EXISTS knowledge_rule (
 id uuid PRIMARY KEY, knowledge_version_id uuid NOT NULL REFERENCES structured_knowledge_version(id), stable_key varchar(200) NOT NULL, subject_concept_id uuid REFERENCES construction_concept(id),
 operator varchar(30), numeric_value numeric, text_value text, unit_concept_id uuid REFERENCES construction_concept(id), jurisdiction_code varchar(120), valid_from date, valid_to date,
 confidence numeric(6,5) NOT NULL DEFAULT 1, status varchar(30) NOT NULL DEFAULT 'CANDIDATE', source_node_id uuid REFERENCES knowledge_source_node(id), UNIQUE(knowledge_version_id,stable_key)
);
CREATE TABLE IF NOT EXISTS knowledge_rule_condition (
 id uuid PRIMARY KEY, rule_id uuid NOT NULL REFERENCES knowledge_rule(id), ordinal int NOT NULL, concept_id uuid REFERENCES construction_concept(id), operator varchar(30) NOT NULL,
 numeric_value numeric, text_value text, unit_concept_id uuid REFERENCES construction_concept(id), negate boolean NOT NULL DEFAULT false
);
CREATE TABLE IF NOT EXISTS knowledge_reference (
 id uuid PRIMARY KEY, knowledge_version_id uuid NOT NULL REFERENCES structured_knowledge_version(id), from_entity_type varchar(50) NOT NULL, from_entity_id uuid NOT NULL,
 reference_type varchar(50) NOT NULL, to_entity_type varchar(50), to_entity_id uuid, target_label text, source_node_id uuid REFERENCES knowledge_source_node(id)
);

CREATE TABLE IF NOT EXISTS knowledge_graph_version (
 id uuid PRIMARY KEY, version_no bigint NOT NULL UNIQUE, knowledge_version_id uuid NOT NULL REFERENCES structured_knowledge_version(id), catalog_version_id uuid NOT NULL REFERENCES construction_catalog_version(id), status varchar(30) NOT NULL, content_hash varchar(64), created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS knowledge_graph_node (
 id uuid PRIMARY KEY, graph_version_id uuid NOT NULL REFERENCES knowledge_graph_version(id), node_type varchar(60) NOT NULL, entity_type varchar(60) NOT NULL, entity_id uuid NOT NULL, stable_key varchar(220), properties_json jsonb NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX IF NOT EXISTS idx_kgn_entity ON knowledge_graph_node(graph_version_id,entity_type,entity_id);
CREATE TABLE IF NOT EXISTS knowledge_graph_edge (
 id uuid PRIMARY KEY, graph_version_id uuid NOT NULL REFERENCES knowledge_graph_version(id), from_node_id uuid NOT NULL REFERENCES knowledge_graph_node(id), relation_type varchar(80) NOT NULL,
 to_node_id uuid NOT NULL REFERENCES knowledge_graph_node(id), confidence numeric(6,5), source_node_id uuid REFERENCES knowledge_source_node(id), valid_from date, valid_to date, properties_json jsonb NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX IF NOT EXISTS idx_kge_from ON knowledge_graph_edge(graph_version_id,from_node_id,relation_type);
CREATE INDEX IF NOT EXISTS idx_kge_to ON knowledge_graph_edge(graph_version_id,to_node_id,relation_type);

CREATE TABLE IF NOT EXISTS knowledge_graphrag_version (
 id uuid PRIMARY KEY, version_no bigint NOT NULL UNIQUE, graph_version_id uuid NOT NULL REFERENCES knowledge_graph_version(id), source_hash varchar(64) NOT NULL,
 community_algorithm varchar(100), community_algorithm_version varchar(80), summary_model varchar(160), summary_model_version varchar(120), summary_prompt_version varchar(80), status varchar(30) NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS knowledge_graph_community (
 id uuid PRIMARY KEY, graphrag_version_id uuid NOT NULL REFERENCES knowledge_graphrag_version(id), parent_community_id uuid REFERENCES knowledge_graph_community(id), level int NOT NULL DEFAULT 0,
 stable_key varchar(220), title text, summary text, summary_hash varchar(64), member_count int NOT NULL DEFAULT 0, metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb
);
CREATE TABLE IF NOT EXISTS knowledge_graph_community_member (
 community_id uuid NOT NULL REFERENCES knowledge_graph_community(id), graph_node_id uuid NOT NULL REFERENCES knowledge_graph_node(id), weight numeric(12,8), PRIMARY KEY(community_id,graph_node_id)
);

CREATE TABLE IF NOT EXISTS knowledge_release (
 id uuid PRIMARY KEY, release_no bigint NOT NULL UNIQUE, release_key varchar(160) NOT NULL UNIQUE, status varchar(30) NOT NULL,
 source_snapshot_hash varchar(64) NOT NULL, catalog_version_id uuid NOT NULL REFERENCES construction_catalog_version(id), knowledge_version_id uuid NOT NULL REFERENCES structured_knowledge_version(id),
 graph_version_id uuid NOT NULL REFERENCES knowledge_graph_version(id), graphrag_version_id uuid REFERENCES knowledge_graphrag_version(id), retrieval_version varchar(100), embedding_version varchar(160),
 compatibility_hash varchar(64) NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), published_at timestamptz, notes text
);
CREATE TABLE IF NOT EXISTS knowledge_release_component (
 id uuid PRIMARY KEY, release_id uuid NOT NULL REFERENCES knowledge_release(id), component_type varchar(60) NOT NULL, component_version varchar(160) NOT NULL,
 content_hash varchar(64) NOT NULL, dependency_hash varchar(64), compatibility_status varchar(30) NOT NULL, metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb, UNIQUE(release_id,component_type)
);
CREATE TABLE IF NOT EXISTS knowledge_dirty_dependency (
 id uuid PRIMARY KEY, entity_type varchar(60) NOT NULL, entity_id uuid NOT NULL, reason varchar(120) NOT NULL, downstream_component varchar(60) NOT NULL, status varchar(30) NOT NULL DEFAULT 'DIRTY', created_at timestamptz NOT NULL DEFAULT now(), resolved_at timestamptz
);