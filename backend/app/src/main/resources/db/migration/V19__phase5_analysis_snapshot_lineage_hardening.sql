-- SakhtYar Phase 5.11 - Data Lineage & Analysis Snapshot Hardening
-- Extends the generic Phase 5.1 analysis_snapshot/data_lineage foundation.

ALTER TABLE analysis_snapshot
    ADD COLUMN scenario_id UUID REFERENCES construction_scenario(id) ON DELETE RESTRICT,
    ADD COLUMN root_entity_type VARCHAR(100),
    ADD COLUMN root_entity_id UUID,
    ADD COLUMN parent_snapshot_id UUID REFERENCES analysis_snapshot(id) ON DELETE SET NULL,
    ADD COLUMN content_sha256 VARCHAR(64),
    ADD COLUMN source_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE analysis_snapshot
    ADD CONSTRAINT chk_analysis_snapshot_source_count CHECK (source_count >= 0),
    ADD CONSTRAINT chk_analysis_snapshot_metadata_object CHECK (jsonb_typeof(metadata) = 'object'),
    ADD CONSTRAINT chk_analysis_snapshot_hash CHECK (
        content_sha256 IS NULL OR content_sha256 ~ '^[0-9a-f]{64}$'
    );

CREATE INDEX idx_analysis_snapshot_scenario
    ON analysis_snapshot(scenario_id, created_at DESC);

CREATE INDEX idx_analysis_snapshot_root
    ON analysis_snapshot(root_entity_type, root_entity_id, created_at DESC);

CREATE INDEX idx_analysis_snapshot_parent
    ON analysis_snapshot(parent_snapshot_id);

CREATE INDEX idx_analysis_snapshot_hash
    ON analysis_snapshot(content_sha256);

ALTER TABLE data_lineage
    ADD COLUMN relationship_type VARCHAR(50) NOT NULL DEFAULT 'DERIVED_FROM',
    ADD COLUMN source_version VARCHAR(100),
    ADD COLUMN source_hash VARCHAR(128);

CREATE INDEX idx_data_lineage_source_entity
    ON data_lineage(source_entity_type, source_entity_id);

CREATE INDEX idx_data_lineage_relationship
    ON data_lineage(analysis_snapshot_id, relationship_type, output_path);