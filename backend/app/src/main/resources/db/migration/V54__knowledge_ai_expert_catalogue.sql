-- Additive AI-curation catalogue. Publication is expert review, never a legal enactment.
-- No staging rows or canonical laws are changed automatically by this migration.
CREATE TABLE IF NOT EXISTS knowledge_ai_publication (
  item_id uuid PRIMARY KEY REFERENCES knowledge_ai_import_item(id) ON DELETE RESTRICT,
  source_sha256 char(64) NOT NULL,
  publication_kind varchar(12) NOT NULL CHECK (publication_kind IN ('LAW','NONLAW')),
  published_by varchar(160) NOT NULL,
  review_note text NOT NULL,
  published_at timestamptz NOT NULL DEFAULT now(),
  revoked_at timestamptz
);
CREATE INDEX IF NOT EXISTS ix_knowledge_ai_publication_kind_time
 ON knowledge_ai_publication(publication_kind,published_at DESC) WHERE revoked_at IS NULL;
COMMENT ON TABLE knowledge_ai_publication IS
 'Expert-reviewed AI candidates available to a separated catalogue. Not a legally authoritative regulation release.';