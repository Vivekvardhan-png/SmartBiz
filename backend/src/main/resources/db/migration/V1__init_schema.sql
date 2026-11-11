-- =============================================================
-- V1: Initial Schema
-- NexusOps Platform - Phase 00 / Prompt 01
--
-- PURPOSE:
--   Establishes the database schema version tracking and a
--   minimal organization table that the multi-tenant isolation
--   architecture (Prompt 02) will build upon.
--
-- DESIGN RULES:
--   - Every organization-owned table MUST have an organization_id FK.
--   - Use UUID primary keys to prevent enumeration attacks.
--   - Use TIMESTAMPTZ (timezone-aware) for all timestamps.
--   - Use NUMERIC for all monetary amounts (never FLOAT/REAL).
--   - Flyway owns the schema; Hibernate validates only.
-- =============================================================

-- Enable the pgcrypto extension for gen_random_uuid() (PostgreSQL 13+
-- supports gen_random_uuid() natively; this is a safe no-op on 14+).
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================================
-- organizations
-- Root aggregate for multi-tenant isolation.
-- Every business record links back to exactly one organization.
-- =============================================================
CREATE TABLE organizations (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    name         VARCHAR(255) NOT NULL,
    slug         VARCHAR(100) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_organizations       PRIMARY KEY (id),
    CONSTRAINT uq_organizations_slug  UNIQUE (slug),
    CONSTRAINT ck_organizations_slug  CHECK (slug ~ '^[a-z0-9-]+$'),
    CONSTRAINT ck_organizations_name  CHECK (LENGTH(TRIM(name)) > 0)
);

COMMENT ON TABLE  organizations             IS 'Root aggregate for multi-tenant isolation. All business records must reference an organization.';
COMMENT ON COLUMN organizations.slug        IS 'URL-safe, lowercase identifier. Immutable after creation.';
COMMENT ON COLUMN organizations.created_at  IS 'UTC timestamp when the organization was created.';

-- =============================================================
-- users
-- Platform identity record. Authentication details (password
-- hash / OAuth tokens) are managed in Prompt 02.
-- =============================================================
CREATE TABLE users (
    id             UUID        NOT NULL DEFAULT gen_random_uuid(),
    email          VARCHAR(320) NOT NULL,
    display_name   VARCHAR(255),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_users        PRIMARY KEY (id),
    CONSTRAINT uq_users_email  UNIQUE (email),
    CONSTRAINT ck_users_email  CHECK (email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$')
);

COMMENT ON TABLE  users              IS 'Platform user identity. Auth credentials added in Prompt 02.';
COMMENT ON COLUMN users.email        IS 'Globally unique email address used as the login identifier.';

-- =============================================================
-- organization_members
-- Joins users to organizations and assigns a role.
-- =============================================================
CREATE TABLE organization_members (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID        NOT NULL,
    user_id         UUID        NOT NULL,
    role            VARCHAR(50) NOT NULL,
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_organization_members         PRIMARY KEY (id),
    CONSTRAINT uq_organization_members_pair    UNIQUE (organization_id, user_id),
    CONSTRAINT fk_org_members_organization     FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_org_members_user             FOREIGN KEY (user_id)         REFERENCES users (id),
    CONSTRAINT ck_org_members_role             CHECK (role IN ('OWNER', 'ADMIN', 'MANAGER', 'EMPLOYEE'))
);

CREATE INDEX idx_org_members_organization ON organization_members (organization_id);
CREATE INDEX idx_org_members_user         ON organization_members (user_id);

COMMENT ON TABLE  organization_members       IS 'Membership and role assignments linking users to organizations.';
COMMENT ON COLUMN organization_members.role  IS 'One of: OWNER, ADMIN, MANAGER, EMPLOYEE. Enforced by application and DB constraint.';

