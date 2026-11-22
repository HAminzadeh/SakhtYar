ALTER TABLE construction_case
    ADD COLUMN IF NOT EXISTS cover_image_url VARCHAR(1000);

CREATE INDEX IF NOT EXISTS idx_construction_case_status_updated
    ON construction_case(status, updated_at DESC);
