-- =============================================================
-- V5: Sales Orders, Invoices & Payments Schema
-- NexusOps Platform - Prompt 05
-- =============================================================

CREATE TABLE invoice_sequences (
    organization_id UUID   NOT NULL,
    next_number     BIGINT NOT NULL DEFAULT 1,

    CONSTRAINT pk_invoice_sequences PRIMARY KEY (organization_id),
    CONSTRAINT fk_inv_seq_org       FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT ck_inv_seq_positive  CHECK (next_number >= 1)
);

CREATE TABLE sales_orders (
    id               UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id  UUID           NOT NULL,
    order_number     VARCHAR(50)    NOT NULL,
    customer_id      UUID           NOT NULL,
    status           VARCHAR(30)    NOT NULL DEFAULT 'DRAFT',
    order_date       DATE           NOT NULL,
    subtotal_amount  NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    discount_amount  NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    tax_rate_percent NUMERIC(5, 2)  NOT NULL DEFAULT 0.00,
    tax_amount       NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    total_amount     NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    stock_deducted   BOOLEAN        NOT NULL DEFAULT FALSE,
    notes            TEXT,
    created_by       UUID,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT pk_sales_orders            PRIMARY KEY (id),
    CONSTRAINT uq_sales_orders_org_number UNIQUE (organization_id, order_number),
    CONSTRAINT fk_sales_orders_org        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_sales_orders_customer   FOREIGN KEY (customer_id)     REFERENCES customers (id)     ON DELETE RESTRICT,
    CONSTRAINT fk_sales_orders_creator    FOREIGN KEY (created_by)      REFERENCES users (id)         ON DELETE SET NULL,
    CONSTRAINT ck_sales_orders_status     CHECK (status IN ('DRAFT', 'CONFIRMED', 'FULFILLED', 'CANCELLED')),
    CONSTRAINT ck_sales_orders_subtotal   CHECK (subtotal_amount >= 0),
    CONSTRAINT ck_sales_orders_discount   CHECK (discount_amount >= 0 AND discount_amount <= subtotal_amount),
    CONSTRAINT ck_sales_orders_tax_rate   CHECK (tax_rate_percent >= 0 AND tax_rate_percent <= 100),
    CONSTRAINT ck_sales_orders_tax_amt    CHECK (tax_amount >= 0),
    CONSTRAINT ck_sales_orders_total      CHECK (total_amount >= 0)
);

CREATE INDEX idx_sales_orders_org_status
    ON sales_orders (organization_id, status);

CREATE INDEX idx_sales_orders_org_date
    ON sales_orders (organization_id, order_date DESC);

CREATE INDEX idx_sales_orders_org_customer
    ON sales_orders (organization_id, customer_id);

CREATE TABLE sales_order_items (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID           NOT NULL,
    sales_order_id  UUID           NOT NULL,
    product_id      UUID           NOT NULL,
    product_sku     VARCHAR(100)   NOT NULL,
    product_name    VARCHAR(255)   NOT NULL,
    unit_of_measure VARCHAR(50)    NOT NULL,
    quantity        NUMERIC(14, 3) NOT NULL,
    unit_price      NUMERIC(14, 2) NOT NULL,
    line_total      NUMERIC(14, 2) NOT NULL,

    CONSTRAINT pk_sales_order_items      PRIMARY KEY (id),
    CONSTRAINT fk_soi_org                FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_soi_order              FOREIGN KEY (sales_order_id)  REFERENCES sales_orders (id)  ON DELETE CASCADE,
    CONSTRAINT fk_soi_product            FOREIGN KEY (product_id)      REFERENCES products (id)      ON DELETE RESTRICT,
    CONSTRAINT ck_soi_qty                CHECK (quantity > 0),
    CONSTRAINT ck_soi_unit_price         CHECK (unit_price >= 0),
    CONSTRAINT ck_soi_line_total         CHECK (line_total >= 0)
);

CREATE INDEX idx_soi_order ON sales_order_items (sales_order_id);

CREATE TABLE sales_order_history (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL,
    sales_order_id  UUID         NOT NULL,
    from_status     VARCHAR(30),
    to_status       VARCHAR(30)  NOT NULL,
    note            VARCHAR(500),
    actor_id        UUID,
    actor_name      VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_sales_order_history PRIMARY KEY (id),
    CONSTRAINT fk_soh_org             FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_soh_order           FOREIGN KEY (sales_order_id)  REFERENCES sales_orders (id)  ON DELETE CASCADE,
    CONSTRAINT fk_soh_actor           FOREIGN KEY (actor_id)        REFERENCES users (id)         ON DELETE SET NULL
);

CREATE INDEX idx_soh_org_order
    ON sales_order_history (organization_id, sales_order_id, created_at DESC);

CREATE TABLE invoices (
    id               UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id  UUID           NOT NULL,
    sales_order_id   UUID           NOT NULL,
    customer_id      UUID           NOT NULL,
    invoice_number   VARCHAR(50)    NOT NULL,
    customer_name    VARCHAR(255)   NOT NULL,
    customer_email   VARCHAR(320),
    status           VARCHAR(30)    NOT NULL DEFAULT 'ISSUED',
    issue_date       DATE           NOT NULL,
    due_date         DATE,
    subtotal_amount  NUMERIC(14, 2) NOT NULL,
    discount_amount  NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    tax_rate_percent NUMERIC(5, 2)  NOT NULL DEFAULT 0.00,
    tax_amount       NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    total_amount     NUMERIC(14, 2) NOT NULL,
    paid_amount      NUMERIC(14, 2) NOT NULL DEFAULT 0.00,
    notes            TEXT,
    created_by       UUID,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT pk_invoices            PRIMARY KEY (id),
    CONSTRAINT uq_invoices_org_number UNIQUE (organization_id, invoice_number),
    CONSTRAINT uq_invoices_org_order  UNIQUE (organization_id, sales_order_id),
    CONSTRAINT fk_invoices_org        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_invoices_order      FOREIGN KEY (sales_order_id)  REFERENCES sales_orders (id)  ON DELETE RESTRICT,
    CONSTRAINT fk_invoices_customer   FOREIGN KEY (customer_id)     REFERENCES customers (id)     ON DELETE RESTRICT,
    CONSTRAINT fk_invoices_creator    FOREIGN KEY (created_by)      REFERENCES users (id)         ON DELETE SET NULL,
    CONSTRAINT ck_invoices_status     CHECK (status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID', 'REFUNDED', 'VOID')),
    CONSTRAINT ck_invoices_total      CHECK (total_amount >= 0),
    CONSTRAINT ck_invoices_paid       CHECK (paid_amount >= 0 AND paid_amount <= total_amount)
);

CREATE INDEX idx_invoices_org_status
    ON invoices (organization_id, status);

CREATE INDEX idx_invoices_org_issue_date
    ON invoices (organization_id, issue_date DESC);

CREATE TABLE invoice_items (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID           NOT NULL,
    invoice_id      UUID           NOT NULL,
    product_id      UUID           NOT NULL,
    product_sku     VARCHAR(100)   NOT NULL,
    description     VARCHAR(255)   NOT NULL,
    unit_of_measure VARCHAR(50)    NOT NULL,
    quantity        NUMERIC(14, 3) NOT NULL,
    unit_price      NUMERIC(14, 2) NOT NULL,
    line_total      NUMERIC(14, 2) NOT NULL,

    CONSTRAINT pk_invoice_items PRIMARY KEY (id),
    CONSTRAINT fk_ii_org        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_ii_invoice    FOREIGN KEY (invoice_id)      REFERENCES invoices (id)      ON DELETE CASCADE,
    CONSTRAINT fk_ii_product    FOREIGN KEY (product_id)      REFERENCES products (id)      ON DELETE RESTRICT,
    CONSTRAINT ck_ii_qty        CHECK (quantity > 0),
    CONSTRAINT ck_ii_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_ii_line_total CHECK (line_total >= 0)
);

CREATE INDEX idx_ii_invoice ON invoice_items (invoice_id);

CREATE TABLE payments (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID           NOT NULL,
    invoice_id      UUID           NOT NULL,
    entry_type      VARCHAR(20)    NOT NULL,
    amount          NUMERIC(14, 2) NOT NULL,
    payment_date    DATE           NOT NULL,
    method          VARCHAR(30)    NOT NULL,
    reference       VARCHAR(255),
    notes           VARCHAR(1000),
    idempotency_key VARCHAR(128),
    recorded_by     UUID,
    recorded_by_name VARCHAR(255),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT pk_payments           PRIMARY KEY (id),
    CONSTRAINT fk_payments_org       FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_payments_invoice   FOREIGN KEY (invoice_id)      REFERENCES invoices (id)      ON DELETE RESTRICT,
    CONSTRAINT fk_payments_actor     FOREIGN KEY (recorded_by)     REFERENCES users (id)         ON DELETE SET NULL,
    CONSTRAINT ck_payments_type      CHECK (entry_type IN ('PAYMENT', 'REFUND')),
    CONSTRAINT ck_payments_method    CHECK (method IN ('CASH', 'BANK_TRANSFER', 'CARD', 'CHECK', 'OTHER')),
    CONSTRAINT ck_payments_amount    CHECK (amount > 0)
);

CREATE UNIQUE INDEX uq_payments_org_idempotency
    ON payments (organization_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_payments_org_invoice
    ON payments (organization_id, invoice_id, created_at DESC);

