-- =============================================================
-- V3: CRM Module Schema (Customers, Leads, Follow-up Notes)
-- NexusOps Platform - Prompt 03
-- =============================================================

CREATE TABLE customers (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL,
    name            VARCHAR(255) NOT NULL,
    company_name    VARCHAR(255),
    email           VARCHAR(320),
    phone           VARCHAR(50),
    address         TEXT,
    follow_up_date  DATE,
    notes           TEXT,
    status          VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    created_by      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_customers        PRIMARY KEY (id),
    CONSTRAINT fk_customers_org    FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_customers_user   FOREIGN KEY (created_by)      REFERENCES users (id)         ON DELETE SET NULL,
    CONSTRAINT ck_customers_name   CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_customers_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX uq_customers_org_email
    ON customers (organization_id, LOWER(email))
    WHERE email IS NOT NULL;

CREATE INDEX idx_customers_org_created
    ON customers (organization_id, created_at DESC);

CREATE INDEX idx_customers_org_status
    ON customers (organization_id, status);

CREATE TABLE leads (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id       UUID           NOT NULL,
    title                 VARCHAR(255)   NOT NULL,
    contact_name          VARCHAR(255)   NOT NULL,
    company_name          VARCHAR(255),
    email                 VARCHAR(320),
    phone                 VARCHAR(50),
    source                VARCHAR(100),
    status                VARCHAR(30)    NOT NULL DEFAULT 'NEW',
    estimated_value       NUMERIC(14, 2),
    follow_up_date        DATE,
    notes                 TEXT,
    assigned_to           UUID,
    converted_customer_id UUID,
    converted_at          TIMESTAMPTZ,
    created_by            UUID,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT pk_leads                    PRIMARY KEY (id),
    CONSTRAINT fk_leads_org                FOREIGN KEY (organization_id)       REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_leads_assigned_to        FOREIGN KEY (assigned_to)           REFERENCES users (id)         ON DELETE SET NULL,
    CONSTRAINT fk_leads_created_by         FOREIGN KEY (created_by)            REFERENCES users (id)         ON DELETE SET NULL,
    CONSTRAINT fk_leads_converted_customer FOREIGN KEY (converted_customer_id) REFERENCES customers (id)     ON DELETE RESTRICT,
    CONSTRAINT ck_leads_title              CHECK (LENGTH(TRIM(title)) > 0),
    CONSTRAINT ck_leads_contact_name       CHECK (LENGTH(TRIM(contact_name)) > 0),
    CONSTRAINT ck_leads_status             CHECK (status IN ('NEW', 'CONTACTED', 'QUALIFIED', 'PROPOSAL', 'CONVERTED', 'LOST')),
    CONSTRAINT ck_leads_estimated_value    CHECK (estimated_value IS NULL OR estimated_value >= 0),
    CONSTRAINT ck_leads_conversion_state   CHECK (
        (status = 'CONVERTED' AND converted_customer_id IS NOT NULL AND converted_at IS NOT NULL)
        OR (status <> 'CONVERTED')
    )
);

CREATE INDEX idx_leads_org_status
    ON leads (organization_id, status);

CREATE INDEX idx_leads_org_follow_up
    ON leads (organization_id, follow_up_date);

CREATE INDEX idx_leads_org_created
    ON leads (organization_id, created_at DESC);

CREATE TABLE crm_notes (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL,
    entity_type     VARCHAR(20)  NOT NULL,
    entity_id       UUID         NOT NULL,
    content         TEXT         NOT NULL,
    follow_up_date  DATE,
    author_id       UUID,
    author_name     VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_crm_notes         PRIMARY KEY (id),
    CONSTRAINT fk_crm_notes_org     FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_crm_notes_author  FOREIGN KEY (author_id)       REFERENCES users (id)         ON DELETE SET NULL,
    CONSTRAINT ck_crm_notes_type    CHECK (entity_type IN ('LEAD', 'CUSTOMER')),
    CONSTRAINT ck_crm_notes_content CHECK (LENGTH(TRIM(content)) > 0)
);

CREATE INDEX idx_crm_notes_org_entity
    ON crm_notes (organization_id, entity_type, entity_id, created_at DESC);
