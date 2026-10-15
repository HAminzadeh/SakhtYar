-- V53 AI-assisted knowledge intake: untrusted evidence, human review, no automatic publication.
-- Existing Flyway V50 remains unchanged.
CREATE TABLE IF NOT EXISTS knowledge_ai_import_batch (
  id uuid PRIMARY KEY,
  archive_name varchar(250) NOT NULL,
  archive_sha256 char(64) NOT NULL UNIQUE,
  archive_format varchar(8) NOT NULL CHECK (archive_format IN ('ZIP','RAR')),
  schema_version varchar(40) NOT NULL,
  source_ai varchar(160) NOT NULL,
  instruction_version varchar(40) NOT NULL,
  status varchar(40) NOT NULL DEFAULT 'AWAITING_REVIEW' CHECK (status IN ('AWAITING_REVIEW','REVIEWED')),
  uploaded_by varchar(160) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS knowledge_ai_import_document (
  id uuid PRIMARY KEY,
  batch_id uuid NOT NULL REFERENCES knowledge_ai_import_batch(id) ON DELETE RESTRICT,
  doc_key varchar(120) NOT NULL,
  title text NOT NULL,
  category varchar(30) NOT NULL CHECK (category IN ('LAW','EDUCATION','TECHNICAL','REFERENCE','OTHER','UNCLASSIFIED')),
  source_reference text NOT NULL,
  authority text,
  source_sha256 char(64),
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(batch_id, doc_key)
);
CREATE INDEX IF NOT EXISTS ix_knowledge_ai_doc_batch ON knowledge_ai_import_document(batch_id);
CREATE TABLE IF NOT EXISTS knowledge_ai_import_item (
  id uuid PRIMARY KEY,
  batch_id uuid NOT NULL REFERENCES knowledge_ai_import_batch(id) ON DELETE RESTRICT,
  document_id uuid NOT NULL REFERENCES knowledge_ai_import_document(id) ON DELETE RESTRICT,
  item_key varchar(160) NOT NULL,
  record_type varchar(20) NOT NULL CHECK (record_type IN ('RULE','SEGMENT')),
  segment_type varchar(20) NOT NULL CHECK (segment_type IN ('ARTICLE','NOTE','CLAUSE','PASSAGE')),
  article_label varchar(250),
  full_text text NOT NULL CHECK (length(btrim(full_text)) BETWEEN 12 AND 20000),
  evidence_quote text NOT NULL CHECK (length(btrim(evidence_quote)) >= 8),
  page_from int NOT NULL CHECK (page_from > 0),
  page_to int NOT NULL CHECK (page_to >= page_from),
  content_sha256 char(64) NOT NULL,
  review_status varchar(25) NOT NULL DEFAULT 'PENDING_REVIEW' CHECK (review_status IN ('PENDING_REVIEW','APPROVED','REJECTED')),
  review_notes text,
  reviewed_by varchar(160),
  reviewed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(batch_id, record_type, item_key)
);
CREATE INDEX IF NOT EXISTS ix_knowledge_ai_item_batch ON knowledge_ai_import_item(batch_id,record_type,review_status);
CREATE INDEX IF NOT EXISTS ix_knowledge_ai_item_document ON knowledge_ai_import_item(document_id);
COMMENT ON TABLE knowledge_ai_import_item IS 'Untrusted AI-extracted source candidates. Approval here records human review but does not automatically create/publish a verified knowledge_rule.';
CREATE TABLE IF NOT EXISTS knowledge_ai_import_review_event (
  id uuid PRIMARY KEY,
  item_id uuid NOT NULL REFERENCES knowledge_ai_import_item(id) ON DELETE RESTRICT,
  decision varchar(25) NOT NULL CHECK (decision IN ('PENDING_REVIEW','APPROVED','REJECTED')),
  notes text,
  reviewer varchar(160) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_knowledge_ai_review_item ON knowledge_ai_import_review_event(item_id,created_at DESC);
