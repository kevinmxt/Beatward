ALTER TABLE app_user DROP CONSTRAINT chk_app_user_role;
ALTER TABLE app_user ADD CONSTRAINT chk_app_user_role CHECK (role IN ('ADMIN', 'USER'));

ALTER TABLE app_user ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE app_user ADD CONSTRAINT chk_app_user_status CHECK (status IN ('ACTIVE', 'DISABLED'));
ALTER TABLE app_user ADD COLUMN disabled_at TIMESTAMPTZ;
ALTER TABLE app_user ADD COLUMN last_login_at TIMESTAMPTZ;

CREATE TABLE invite_code (
    id BIGSERIAL PRIMARY KEY,
    code_hash VARCHAR(64) NOT NULL,
    label VARCHAR(200),
    max_uses INTEGER NOT NULL,
    used_count INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_by BIGINT NOT NULL REFERENCES app_user (id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_invite_code_code_hash UNIQUE (code_hash),
    CONSTRAINT chk_invite_code_max_uses CHECK (max_uses BETWEEN 1 AND 100),
    CONSTRAINT chk_invite_code_used_count CHECK (used_count >= 0 AND used_count <= max_uses)
);

CREATE INDEX idx_invite_code_created_by ON invite_code (created_by);

CREATE TABLE password_reset_token (
    id BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES app_user (id),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_by BIGINT NOT NULL REFERENCES app_user (id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_password_reset_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_password_reset_token_user_id ON password_reset_token (user_id);
