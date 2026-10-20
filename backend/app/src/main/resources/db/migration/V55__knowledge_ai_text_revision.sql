-- V57: immutable edit history for human corrections to AI staging content.
-- Source evidence remains unchanged, and publication is not automatic.
CREATE TABLE IF NOT EXISTS knowledge_ai_text_revision (
  id uuid PRIMARY KEY,
  item_id uuid REFERENCES knowledge_ai_import_item(id) ON DELETE SET NULL,
  document_title text NOT NULL,
  old_full_text text NOT NULL,
  new_full_text text NOT NULL,
  old_sha256 char(64) NOT NULL,
  new_sha256 char(64) NOT NULL,
  editor varchar(160) NOT NULL,
  edited_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_knowledge_ai_text_revision_item
  ON knowledge_ai_text_revision(item_id, edited_at DESC);
COMMENT ON TABLE knowledge_ai_text_revision IS 'Immutable human revision of AI staging text; retains earlier versions after staging item purge.';
