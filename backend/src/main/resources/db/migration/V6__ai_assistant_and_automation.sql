-- Prompt 07: Controlled AI Assistant Proposals/Audit & Deterministic Automation Tables

CREATE TABLE assistant_action_proposals (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    organization_id     UUID         NOT NULL,
    proposed_by_user_id UUID         NOT NULL,
    tool_name           VARCHAR(80)  NOT NULL,
    arguments_json      TEXT         NOT NULL,
    action_summary      VARCHAR(500) NOT NULL,
    status              VARCHAR(30)  NOT NULL,
    result_summary      VARCHAR(500),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ  NOT NULL,
    resolved_at         TIMESTAMPTZ,

    CONSTRAINT pk_assistant_proposals PRIMARY KEY (id),
    CONSTRAINT fk_ap_org              FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_ap_user             FOREIGN KEY (proposed_by_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_ap_status           CHECK (status IN ('PENDING', 'EXECUTED', 'CANCELLED', 'EXPIRED', 'FAILED'))
);

CREATE INDEX idx_ap_org_user_created
    ON assistant_action_proposals (organization_id, proposed_by_user_id, created_at DESC);

CREATE TABLE assistant_audit_logs (
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    organization_id UUID          NOT NULL,
    user_id         UUID,
    event_type      VARCHAR(40)   NOT NULL,
    tool_name       VARCHAR(80),
    outcome         VARCHAR(40)   NOT NULL,
    details         VARCHAR(1000),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_assistant_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_aal_org              FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_aal_user             FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_aal_org_created
    ON assistant_audit_logs (organization_id, created_at DESC);

CREATE TABLE automation_runs (
    id                       UUID         NOT NULL DEFAULT gen_random_uuid(),
    organization_id          UUID         NOT NULL,
    workflow_type            VARCHAR(50)  NOT NULL,
    trigger_source           VARCHAR(30)  NOT NULL,
    triggered_by_user_id     UUID,
    status                   VARCHAR(30)  NOT NULL,
    alerts_created           INTEGER      NOT NULL DEFAULT 0,
    alerts_skipped_duplicate INTEGER      NOT NULL DEFAULT 0,
    error_message            VARCHAR(500),
    started_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at             TIMESTAMPTZ,

    CONSTRAINT pk_automation_runs   PRIMARY KEY (id),
    CONSTRAINT fk_ar_org            FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_ar_user           FOREIGN KEY (triggered_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_ar_workflow       CHECK (workflow_type IN ('LOW_STOCK_ALERT', 'FOLLOW_UP_REMINDER')),
    CONSTRAINT ck_ar_trigger        CHECK (trigger_source IN ('MANUAL', 'SCHEDULED')),
    CONSTRAINT ck_ar_status         CHECK (status IN ('COMPLETED', 'FAILED'))
);

CREATE INDEX idx_ar_org_started
    ON automation_runs (organization_id, started_at DESC);

CREATE TABLE automation_alerts (
    id                      UUID          NOT NULL DEFAULT gen_random_uuid(),
    organization_id         UUID          NOT NULL,
    workflow_type           VARCHAR(50)   NOT NULL,
    entity_type             VARCHAR(40)   NOT NULL,
    entity_id               UUID          NOT NULL,
    dedupe_key              VARCHAR(160)  NOT NULL,
    severity                VARCHAR(20)   NOT NULL,
    title                   VARCHAR(255)  NOT NULL,
    message                 VARCHAR(1000) NOT NULL,
    status                  VARCHAR(30)   NOT NULL,
    run_id                  UUID          NOT NULL,
    acknowledged_by_user_id UUID,
    acknowledged_at         TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_automation_alerts PRIMARY KEY (id),
    CONSTRAINT fk_aa_org            FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_aa_run            FOREIGN KEY (run_id) REFERENCES automation_runs (id) ON DELETE CASCADE,
    CONSTRAINT fk_aa_ack_user       FOREIGN KEY (acknowledged_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_aa_workflow       CHECK (workflow_type IN ('LOW_STOCK_ALERT', 'FOLLOW_UP_REMINDER')),
    CONSTRAINT ck_aa_entity_type    CHECK (entity_type IN ('PRODUCT', 'LEAD')),
    CONSTRAINT ck_aa_severity       CHECK (severity IN ('INFO', 'WARNING')),
    CONSTRAINT ck_aa_status         CHECK (status IN ('OPEN', 'ACKNOWLEDGED'))
);

CREATE UNIQUE INDEX uq_automation_alerts_open_dedupe
    ON automation_alerts (organization_id, dedupe_key)
    WHERE status = 'OPEN';

CREATE INDEX idx_aa_org_status_created
    ON automation_alerts (organization_id, status, created_at DESC);
