-- SakhtYar V48 - Reconcile V33 and V39 knowledge source document schemas.
-- Why: V33 created knowledge_source_document with filename/canonical_path and
-- required correlation_id; V39 used CREATE TABLE IF NOT EXISTS for the same table
-- with original_name/media_type/storage_uri. That statement was a no-op.
-- This additive migration does NOT delete or re-key existing documents.
-- New ingestion must write V33's required fields; original_name is derived
-- from filename, so metadata stays consistent across both legacy readers.

ALTER TABLE knowledge_source_document
    ADD COLUMN IF NOT EXISTS original_name text GENERATED ALWAYS AS (filename) STORED,
    ADD COLUMN IF NOT EXISTS media_type varchar(120),
    ADD COLUMN IF NOT EXISTS storage_uri text,
    ADD COLUMN IF NOT EXISTS authority text,
    ADD COLUMN IF NOT EXISTS jurisdiction_code varchar(120),
    ADD COLUMN IF NOT EXISTS effective_from date,
    ADD COLUMN IF NOT EXISTS effective_to date,
    ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();

UPDATE knowledge_source_document
   SET media_type = CASE
          WHEN lower(coalesce(extension,'')) = '.pdf' THEN 'application/pdf'
          WHEN lower(coalesce(extension,'')) = '.docx' THEN 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
          WHEN lower(coalesce(extension,'')) = '.pptx' THEN 'application/vnd.openxmlformats-officedocument.presentationml.presentation'
          ELSE 'application/octet-stream' END
 WHERE media_type IS NULL;

UPDATE knowledge_source_document
   SET storage_uri = canonical_path
 WHERE storage_uri IS NULL;

COMMENT ON COLUMN knowledge_source_document.original_name IS
    'Compatibility alias, generated from V33 filename. Review provenance uses canonical_path and version.';
-- No UNIQUE(sha256): the physical V33 schema permits the same content
-- at distinct canonical paths, and tracks versions for each path.
