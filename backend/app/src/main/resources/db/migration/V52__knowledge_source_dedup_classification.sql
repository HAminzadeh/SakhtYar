-- Persist only successfully normalized documents across completed intake executions.
-- File identity is a SHA-256 of the original source, never its path or filename.
ALTER TABLE knowledge_intake_inventory
  ADD COLUMN IF NOT EXISTS document_kind varchar(30) NOT NULL DEFAULT 'UNCLASSIFIED';

CREATE INDEX IF NOT EXISTS ix_kii_execution_kind
  ON knowledge_intake_inventory(execution_id,document_kind);

CREATE TABLE IF NOT EXISTS knowledge_processed_source (
    sha256 varchar(64) PRIMARY KEY CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    first_execution_id uuid REFERENCES knowledge_intake_execution(id) ON DELETE SET NULL,
    source_path text NOT NULL,
    document_kind varchar(30) NOT NULL DEFAULT 'UNCLASSIFIED'
      CHECK (document_kind IN ('LAW','EDUCATION','TECHNICAL','REFERENCE','OTHER','UNCLASSIFIED')),
    processed_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_kps_processed_at ON knowledge_processed_source(processed_at DESC);
COMMENT ON TABLE knowledge_processed_source IS
    'Only documents with a normalized source artifact in a successfully completed knowledge intake execution; skip by source SHA-256 on later runs.';

-- Backfill earlier successful executions; failed runs and files without readable
-- normalized artifacts are deliberately excluded from the "completed" registry.
INSERT INTO knowledge_processed_source
    (sha256,first_execution_id,source_path,document_kind,processed_at)
SELECT DISTINCT ON (i.sha256)
       i.sha256,i.execution_id,i.absolute_path,'UNCLASSIFIED',coalesce(e.finished_at,now())
  FROM knowledge_intake_inventory i
  JOIN knowledge_intake_execution e ON e.id=i.execution_id
 WHERE e.status IN ('WAITING_FOR_REVIEW','COMPLETED')
   AND i.selected_by_user=true
   AND i.sha256 ~ '^[0-9a-f]{64}$'
   AND EXISTS (
       SELECT 1 FROM knowledge_source_artifact a
        WHERE a.execution_id=i.execution_id
          AND a.document_id=i.document_id
          AND a.artifact_type='NORMALIZED_PAGE'
          AND length(btrim(coalesce(a.content,'')))>=12
   )
 ORDER BY i.sha256,e.finished_at DESC NULLS LAST
ON CONFLICT (sha256) DO NOTHING;
