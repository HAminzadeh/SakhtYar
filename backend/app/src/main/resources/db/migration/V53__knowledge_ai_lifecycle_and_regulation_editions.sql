-- V53.3 -- reversible knowledge staging, import issue queue, and historical regulation editions.
-- NEVER modify existing Flyway migrations. This migration is additive and non-destructive.
ALTER TABLE knowledge_ai_import_batch
 ADD COLUMN IF NOT EXISTS archived_at timestamptz,
 ADD COLUMN IF NOT EXISTS archived_by varchar(160),
 ADD COLUMN IF NOT EXISTS archive_reason text,
 ADD COLUMN IF NOT EXISTS source_mode varchar(30);

ALTER TABLE knowledge_ai_import_document
 ADD COLUMN IF NOT EXISTS archived_at timestamptz,
 ADD COLUMN IF NOT EXISTS archived_by varchar(160),
 ADD COLUMN IF NOT EXISTS archive_reason text,
 ADD COLUMN IF NOT EXISTS suggested_category varchar(30),
 ADD COLUMN IF NOT EXISTS classification_reason text,
 ADD COLUMN IF NOT EXISTS classification_confirmed_by varchar(160),
 ADD COLUMN IF NOT EXISTS regulation_family varchar(220),
 ADD COLUMN IF NOT EXISTS edition_label varchar(100),
 ADD COLUMN IF NOT EXISTS calendar_type varchar(20),
 ADD COLUMN IF NOT EXISTS resolution_date date,
 ADD COLUMN IF NOT EXISTS publication_date date,
 ADD COLUMN IF NOT EXISTS effective_from date,
 ADD COLUMN IF NOT EXISTS effective_to date,
 ADD COLUMN IF NOT EXISTS territorial_scope text,
 ADD COLUMN IF NOT EXISTS relation_to_previous varchar(40),
 ADD COLUMN IF NOT EXISTS previous_edition_id uuid REFERENCES knowledge_ai_import_document(id) ON DELETE SET NULL,
 ADD COLUMN IF NOT EXISTS edition_reviewed_by varchar(160),
 ADD COLUMN IF NOT EXISTS edition_reviewed_at timestamptz;

ALTER TABLE knowledge_ai_import_item
 ADD COLUMN IF NOT EXISTS archived_at timestamptz,
 ADD COLUMN IF NOT EXISTS archived_by varchar(160),
 ADD COLUMN IF NOT EXISTS archive_reason text;

CREATE INDEX IF NOT EXISTS ix_kaid_family ON knowledge_ai_import_document(regulation_family,edition_label);
CREATE INDEX IF NOT EXISTS ix_kaid_archived ON knowledge_ai_import_document(archived_at);
CREATE INDEX IF NOT EXISTS ix_kaii_archived ON knowledge_ai_import_item(archived_at);

CREATE TABLE IF NOT EXISTS knowledge_ai_import_issue (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 batch_id uuid NOT NULL REFERENCES knowledge_ai_import_batch(id) ON DELETE RESTRICT,
 origin_file varchar(120) NOT NULL,
 line_number int NOT NULL CHECK(line_number >= 0),
 doc_key varchar(120),
 item_key varchar(160),
 issue_code varchar(80) NOT NULL,
 explanation text NOT NULL,
 suggestion text NOT NULL,
 row_payload jsonb,
 state varchar(30) NOT NULL DEFAULT 'OPEN' CHECK(state IN ('OPEN','RESOLVED','DISMISSED')),
 decision_by varchar(160),
 decision_note text,
 resolved_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_kai_issue_batch_state ON knowledge_ai_import_issue(batch_id,state);

-- Audit rows are independent of deleted staging entities, and may outlive those entities.
CREATE TABLE IF NOT EXISTS knowledge_ai_lifecycle_event (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 actor varchar(160) NOT NULL,
 event_type varchar(60) NOT NULL,
 scope_type varchar(40) NOT NULL,
 scope_id varchar(100),
 affected_count int NOT NULL DEFAULT 0,
 note text,
 details jsonb NOT NULL DEFAULT '{}'::jsonb,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_kal_event_time ON knowledge_ai_lifecycle_event(created_at DESC);

-- Server-side recoverable snapshot. A hard delete requires a prior export token;
-- these JSON backups are independent of deleted staging tables.
CREATE TABLE IF NOT EXISTS knowledge_ai_delete_backup (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 actor varchar(160) NOT NULL,
 selection jsonb NOT NULL,
 snapshot jsonb NOT NULL,
 record_count int NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 consumed_at timestamptz
);

-- Baselines are immutable *labels/snapshots* of selected staging IDs, not a legal release.
CREATE TABLE IF NOT EXISTS knowledge_ai_baseline (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 label varchar(220) NOT NULL UNIQUE,
 note text,
 snapshot jsonb NOT NULL,
 created_by varchar(160) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE knowledge_ai_import_issue IS 'Recoverable import issues; invalid rows are never silently promoted into rules.';
COMMENT ON TABLE knowledge_ai_baseline IS 'Staging snapshots for comparing intakes; not a legally verified publication.';
