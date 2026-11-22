-- Phase 5.18: supervised, document-level category. No implicit legal approval.
-- Existing knowledge/review rows and release versions remain unchanged.
CREATE TABLE IF NOT EXISTS knowledge_document_classification (
  document_id uuid PRIMARY KEY REFERENCES knowledge_source_document(id) ON DELETE RESTRICT,
  category varchar(30) NOT NULL CHECK (category IN ('LAW','EDUCATION','TECHNICAL','REFERENCE','OTHER')),
  notes text NOT NULL DEFAULT '',
  reviewed_by varchar(160) NOT NULL,
  reviewed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_kdc_category ON knowledge_document_classification(category);
CREATE TABLE IF NOT EXISTS knowledge_document_classification_event (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  document_id uuid NOT NULL REFERENCES knowledge_source_document(id) ON DELETE RESTRICT,
  category varchar(30) NOT NULL,
  notes text NOT NULL DEFAULT '',
  reviewed_by varchar(160) NOT NULL,
  reviewed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_kdce_document_time ON knowledge_document_classification_event(document_id,reviewed_at DESC);
