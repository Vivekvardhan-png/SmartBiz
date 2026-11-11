-- =============================================================
-- V4: Inventory & Supplier Management Schema
-- NexusOps Platform - Prompt 04
-- =============================================================

CREATE TABLE suppliers (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL,
    name            VARCHAR(255) NOT NULL,
    contact_name    VARCHAR(255),
    email           VARCHAR(320),
    phone           VARCHAR(50),
    address         TEXT,
    notes           TEXT,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_suppliers      PRIMARY KEY (id),
    CONSTRAINT fk_suppliers_org  FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_suppliers_user FOREIGN KEY (created_by)      REFERENCES users (id)         ON DELETE SET NULL,
    CONSTRAINT ck_suppliers_name CHECK (LENGTH(TRIM(name)) > 0)
);

CREATE UNIQUE INDEX uq_suppliers_org_email
    ON suppliers (organization_id, LOWER(email))
    WHERE email IS NOT NULL;

CREATE INDEX idx_suppliers_org_active
    ON suppliers (organization_id, active);

CREATE INDEX idx_suppliers_org_name
    ON suppliers (organization_id, name);

CREATE TABLE products (
    id                  UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id     UUID           NOT NULL,
    sku                 VARCHAR(100)   NOT NULL,
    name                VARCHAR(255)   NOT NULL,
    description         TEXT,
    category            VARCHAR(100),
    unit_of_measure     VARCHAR(50)    NOT NULL DEFAULT 'PCS',
    unit_price          NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    cost_price          NUMERIC(14, 2),
    stock_quantity      NUMERIC(14, 3) NOT NULL DEFAULT 0.000,
    low_stock_threshold NUMERIC(14, 3) NOT NULL DEFAULT 0.000,
    active              BOOLEAN        NOT NULL DEFAULT TRUE,
    created_by          UUID,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT pk_products               PRIMARY KEY (id),
    CONSTRAINT fk_products_org           FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_products_user          FOREIGN KEY (created_by)      REFERENCES users (id)         ON DELETE SET NULL,
    CONSTRAINT ck_products_sku           CHECK (LENGTH(TRIM(sku)) > 0),
    CONSTRAINT ck_products_name          CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_products_uom           CHECK (LENGTH(TRIM(unit_of_measure)) > 0),
    CONSTRAINT ck_products_unit_price    CHECK (unit_price >= 0),
    CONSTRAINT ck_products_cost_price    CHECK (cost_price IS NULL OR cost_price >= 0),
    CONSTRAINT ck_products_stock_non_neg CHECK (stock_quantity >= 0),
    CONSTRAINT ck_products_threshold     CHECK (low_stock_threshold >= 0)
);

CREATE UNIQUE INDEX uq_products_org_sku
    ON products (organization_id, LOWER(sku));

CREATE INDEX idx_products_org_active
    ON products (organization_id, active);

CREATE INDEX idx_products_org_category
    ON products (organization_id, category);

CREATE TABLE supplier_products (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID           NOT NULL,
    supplier_id     UUID           NOT NULL,
    product_id      UUID           NOT NULL,
    supplier_sku    VARCHAR(100),
    unit_cost       NUMERIC(14, 2),
    lead_time_days  INTEGER,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT pk_supplier_products      PRIMARY KEY (id),
    CONSTRAINT uq_supplier_products_pair UNIQUE (organization_id, supplier_id, product_id),
    CONSTRAINT fk_sp_org                 FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_sp_supplier            FOREIGN KEY (supplier_id)     REFERENCES suppliers (id)     ON DELETE CASCADE,
    CONSTRAINT fk_sp_product             FOREIGN KEY (product_id)      REFERENCES products (id)      ON DELETE CASCADE,
    CONSTRAINT ck_sp_unit_cost           CHECK (unit_cost IS NULL OR unit_cost >= 0),
    CONSTRAINT ck_sp_lead_time           CHECK (lead_time_days IS NULL OR lead_time_days >= 0)
);

CREATE INDEX idx_sp_org_product
    ON supplier_products (organization_id, product_id);

CREATE INDEX idx_sp_org_supplier
    ON supplier_products (organization_id, supplier_id);

CREATE TABLE stock_movements (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID           NOT NULL,
    product_id      UUID           NOT NULL,
    supplier_id     UUID,
    movement_type   VARCHAR(40)    NOT NULL,
    quantity_delta  NUMERIC(14, 3) NOT NULL,
    quantity_before NUMERIC(14, 3) NOT NULL,
    quantity_after  NUMERIC(14, 3) NOT NULL,
    reason          VARCHAR(500)   NOT NULL,
    reference       VARCHAR(255),
    idempotency_key VARCHAR(128),
    actor_id        UUID,
    actor_name      VARCHAR(255),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT pk_stock_movements         PRIMARY KEY (id),
    CONSTRAINT fk_sm_org                  FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_sm_product              FOREIGN KEY (product_id)      REFERENCES products (id)      ON DELETE RESTRICT,
    CONSTRAINT fk_sm_supplier             FOREIGN KEY (supplier_id)     REFERENCES suppliers (id)     ON DELETE SET NULL,
    CONSTRAINT fk_sm_actor                FOREIGN KEY (actor_id)        REFERENCES users (id)         ON DELETE SET NULL,
    CONSTRAINT ck_sm_type                 CHECK (movement_type IN ('INITIAL', 'RECEIPT', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'RETURN_IN', 'ORDER_DEDUCTION')),
    CONSTRAINT ck_sm_delta_non_zero       CHECK (quantity_delta <> 0),
    CONSTRAINT ck_sm_before_non_neg       CHECK (quantity_before >= 0),
    CONSTRAINT ck_sm_after_non_neg        CHECK (quantity_after >= 0),
    CONSTRAINT ck_sm_reason_non_blank     CHECK (LENGTH(TRIM(reason)) > 0)
);

CREATE UNIQUE INDEX uq_stock_movements_org_idempotency
    ON stock_movements (organization_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_stock_movements_org_product
    ON stock_movements (organization_id, product_id, created_at DESC);

CREATE INDEX idx_stock_movements_org_created
    ON stock_movements (organization_id, created_at DESC);

