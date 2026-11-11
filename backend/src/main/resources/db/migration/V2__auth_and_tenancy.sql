-- =============================================================
-- V2: Authentication & Multi-Tenant Session Schema
-- NexusOps Platform - Prompt 02
-- =============================================================

ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(255) NOT NULL,
    ADD COLUMN enabled       BOOLEAN      NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN users.password_hash IS 'BCrypt password hash (cost 12). Never plaintext.';
COMMENT ON COLUMN users.enabled       IS 'Whether the user account is active and allowed to authenticate.';

-- =============================================================
-- auth_sessions
-- Server-side revocable session tracking bound to a specific
-- (organization_id, user_id) membership pair.
-- =============================================================
CREATE TABLE auth_sessions (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL,
    organization_id UUID        NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_auth_sessions            PRIMARY KEY (id),
    CONSTRAINT fk_auth_sessions_user       FOREIGN KEY (user_id)         REFERENCES users (id)         ON DELETE CASCADE,
    CONSTRAINT fk_auth_sessions_org        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_auth_sessions_membership FOREIGN KEY (organization_id, user_id)
        REFERENCES organization_members (organization_id, user_id) ON DELETE CASCADE
);

CREATE INDEX idx_auth_sessions_user       ON auth_sessions (user_id);
CREATE INDEX idx_auth_sessions_org        ON auth_sessions (organization_id);
CREATE INDEX idx_auth_sessions_expires_at ON auth_sessions (expires_at);

COMMENT ON TABLE auth_sessions IS 'Active and revoked authentication sessions bound to verified organization memberships.';

