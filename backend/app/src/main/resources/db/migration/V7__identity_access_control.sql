ALTER TABLE app_user
    ADD COLUMN email VARCHAR(254),
    ADD COLUMN mobile VARCHAR(30),
    ADD COLUMN status VARCHAR(30),
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMPTZ,
    ADD COLUMN last_login_at TIMESTAMPTZ,
    ADD COLUMN password_changed_at TIMESTAMPTZ,
    ADD COLUMN updated_at TIMESTAMPTZ;

UPDATE app_user
SET
    status = CASE WHEN active THEN 'ACTIVE' ELSE 'SUSPENDED' END,
    password_changed_at = created_at,
    updated_at = created_at
WHERE status IS NULL
   OR password_changed_at IS NULL
   OR updated_at IS NULL;

ALTER TABLE app_user
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN password_changed_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE app_user
    ADD CONSTRAINT chk_app_user_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED'));

CREATE UNIQUE INDEX ux_app_user_username_lower
    ON app_user (LOWER(username));

CREATE UNIQUE INDEX ux_app_user_email_lower
    ON app_user (LOWER(email))
    WHERE email IS NOT NULL;

CREATE INDEX idx_app_user_status ON app_user(status);
CREATE INDEX idx_app_user_role ON app_user(role);

CREATE TABLE auth_session (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(64) NOT NULL UNIQUE,
    client_type VARCHAR(20) NOT NULL,
    device_name VARCHAR(200),
    user_agent VARCHAR(1000),
    ip_address VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX idx_auth_session_user
    ON auth_session(user_id, created_at DESC);

CREATE INDEX idx_auth_session_expires
    ON auth_session(expires_at);

CREATE INDEX idx_auth_session_active
    ON auth_session(user_id, revoked_at, expires_at);
