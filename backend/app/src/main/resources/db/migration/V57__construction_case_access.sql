-- S01: additive per-case membership. Existing case rows remain untouched.
-- Read access: case creator, administrator, or an explicitly granted active member.
CREATE TABLE IF NOT EXISTS construction_case_access (
    case_id uuid NOT NULL REFERENCES construction_case(id) ON DELETE CASCADE,
    username varchar(100) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    granted_by varchar(100),
    granted_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (case_id, username)
);
CREATE INDEX IF NOT EXISTS idx_construction_case_access_username
    ON construction_case_access (lower(username)) WHERE active = true;
