-- SakhtYar legal-language candidates, not legally approved rules.
-- Additive migration; existing numeric observations and release history are preserved.
CREATE TABLE IF NOT EXISTS knowledge_legal_segment (
    id uuid PRIMARY KEY,
    execution_id uuid NOT NULL REFERENCES knowledge_intake_execution(id),
    document_id uuid NOT NULL REFERENCES knowledge_source_document(id),
    source_node_id uuid NOT NULL REFERENCES knowledge_source_node(id),
    rule_id uuid REFERENCES knowledge_rule(id),
    ordinal int NOT NULL CHECK (ordinal > 0),
    segment_type varchar(30) NOT NULL CHECK (segment_type IN ('ARTICLE','NOTE','CLAUSE','PASSAGE')),
    article_label text,
    full_text text NOT NULL CHECK (length(btrim(full_text)) > 0),
    text_sha256 char(64) NOT NULL,
    page_from int NOT NULL CHECK (page_from > 0),
    page_to int NOT NULL CHECK (page_to >= page_from),
    model_metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    review_status varchar(40) NOT NULL DEFAULT 'CANDIDATE',
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(execution_id, document_id, ordinal)
);
CREATE INDEX IF NOT EXISTS ix_knowledge_legal_segment_exec
    ON knowledge_legal_segment(execution_id, document_id, page_from, ordinal);
CREATE INDEX IF NOT EXISTS ix_knowledge_legal_segment_rule
    ON knowledge_legal_segment(rule_id);
COMMENT ON TABLE knowledge_legal_segment IS
'Source-preserving article/note/clause candidates. LLM metadata and evidence are not human legal verification.';
