-- Phase 5.15: audit-friendly knowledge review. Never backfill approvals.
CREATE TABLE IF NOT EXISTS knowledge_rule_review (
    rule_id uuid PRIMARY KEY REFERENCES knowledge_rule(id) ON DELETE RESTRICT,
    decision varchar(20) NOT NULL CHECK (decision IN ('APPROVED','REJECTED')),
    subject_code varchar(160),
    verified_operator varchar(30),
    unit_code varchar(80),
    scope_country varchar(2),
    scope_city varchar(120),
    scope_district varchar(120),
    source_authority text,
    source_document_id uuid REFERENCES knowledge_source_document(id),
    source_sha256 varchar(64),
    source_page integer,
    valid_from date,
    valid_to date,
    reviewer varchar(160) NOT NULL,
    reviewed_at timestamptz NOT NULL DEFAULT now(),
    notes text NOT NULL DEFAULT '',
    CHECK (valid_from IS NULL OR valid_to IS NULL OR valid_to >= valid_from),
    CHECK (decision <> 'APPROVED' OR (
       nullif(subject_code,'') IS NOT NULL AND nullif(verified_operator,'') IS NOT NULL
       AND nullif(unit_code,'') IS NOT NULL AND nullif(scope_country,'') IS NOT NULL
       AND nullif(scope_city,'') IS NOT NULL AND nullif(source_authority,'') IS NOT NULL
       AND source_document_id IS NOT NULL AND source_sha256 IS NOT NULL
       AND source_page > 0 AND valid_from IS NOT NULL
    ))
);
CREATE TABLE IF NOT EXISTS knowledge_rule_review_event (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id uuid NOT NULL REFERENCES knowledge_rule(id) ON DELETE RESTRICT,
    decision varchar(20) NOT NULL,
    reviewed_by varchar(160) NOT NULL,
    reviewed_at timestamptz NOT NULL DEFAULT now(),
    review_snapshot jsonb NOT NULL CHECK (jsonb_typeof(review_snapshot)='object')
);
CREATE INDEX IF NOT EXISTS ix_krreview_decision ON knowledge_rule_review(decision,scope_country,scope_city,scope_district);
CREATE INDEX IF NOT EXISTS ix_krreview_event ON knowledge_rule_review_event(rule_id,reviewed_at DESC);
-- This links a manually authored urban rule to one specifically approved, published knowledge rule.
-- It is deliberately nullable; old urban rules are not silently upgraded.
ALTER TABLE urban_rule ADD COLUMN IF NOT EXISTS verified_knowledge_rule_id uuid REFERENCES knowledge_rule(id);
CREATE INDEX IF NOT EXISTS ix_urban_rule_evidence ON urban_rule(verified_knowledge_rule_id);
