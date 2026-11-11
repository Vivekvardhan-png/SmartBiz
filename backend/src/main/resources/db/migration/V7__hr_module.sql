-- =============================================================
-- V7: Human Resources Management Module Schema
-- =============================================================

CREATE TABLE hr_employee_sequences (
    organization_id UUID   NOT NULL,
    next_number     BIGINT NOT NULL DEFAULT 1,

    CONSTRAINT pk_hr_employee_sequences PRIMARY KEY (organization_id),
    CONSTRAINT fk_hr_emp_seq_org        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT ck_hr_emp_seq_positive   CHECK (next_number >= 1)
);

CREATE TABLE hr_departments (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    organization_id  UUID         NOT NULL,
    name             VARCHAR(150) NOT NULL,
    description      TEXT,
    head_employee_id UUID,
    created_by       UUID,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_hr_departments      PRIMARY KEY (id),
    CONSTRAINT fk_hr_departments_org  FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_hr_departments_user FOREIGN KEY (created_by)      REFERENCES users (id)         ON DELETE SET NULL,
    CONSTRAINT ck_hr_departments_name CHECK (LENGTH(TRIM(name)) > 0)
);

CREATE UNIQUE INDEX uq_hr_departments_org_name
    ON hr_departments (organization_id, LOWER(name));

CREATE INDEX idx_hr_departments_org
    ON hr_departments (organization_id, name);

CREATE TABLE hr_employees (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    organization_id      UUID         NOT NULL,
    employee_code        VARCHAR(40)  NOT NULL,
    user_id              UUID,
    first_name           VARCHAR(120) NOT NULL,
    last_name            VARCHAR(120) NOT NULL,
    email                VARCHAR(320) NOT NULL,
    phone                VARCHAR(50)  NOT NULL,
    department_id        UUID         NOT NULL,
    designation          VARCHAR(150) NOT NULL,
    employment_type      VARCHAR(30)  NOT NULL,
    date_of_joining      DATE         NOT NULL,
    employment_status    VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    reporting_manager_id UUID,
    address              TEXT,
    created_by           UUID,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_hr_employees            PRIMARY KEY (id),
    CONSTRAINT uq_hr_employees_org_code   UNIQUE (organization_id, employee_code),
    CONSTRAINT fk_hr_employees_org        FOREIGN KEY (organization_id)      REFERENCES organizations (id)  ON DELETE CASCADE,
    CONSTRAINT fk_hr_employees_user       FOREIGN KEY (user_id)              REFERENCES users (id)          ON DELETE SET NULL,
    CONSTRAINT fk_hr_employees_dept       FOREIGN KEY (department_id)        REFERENCES hr_departments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_employees_manager    FOREIGN KEY (reporting_manager_id) REFERENCES hr_employees (id)   ON DELETE SET NULL,
    CONSTRAINT fk_hr_employees_creator    FOREIGN KEY (created_by)           REFERENCES users (id)          ON DELETE SET NULL,
    CONSTRAINT ck_hr_employees_first      CHECK (LENGTH(TRIM(first_name)) > 0),
    CONSTRAINT ck_hr_employees_last       CHECK (LENGTH(TRIM(last_name)) > 0),
    CONSTRAINT ck_hr_employees_email      CHECK (LENGTH(TRIM(email)) > 0),
    CONSTRAINT ck_hr_employees_phone      CHECK (LENGTH(TRIM(phone)) > 0),
    CONSTRAINT ck_hr_employees_desig      CHECK (LENGTH(TRIM(designation)) > 0),
    CONSTRAINT ck_hr_employees_type       CHECK (employment_type IN ('FULL_TIME', 'PART_TIME', 'CONTRACT')),
    CONSTRAINT ck_hr_employees_status     CHECK (employment_status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_hr_employees_not_self   CHECK (reporting_manager_id IS NULL OR reporting_manager_id <> id)
);

CREATE UNIQUE INDEX uq_hr_employees_org_email
    ON hr_employees (organization_id, LOWER(email));

CREATE UNIQUE INDEX uq_hr_employees_org_user
    ON hr_employees (organization_id, user_id)
    WHERE user_id IS NOT NULL;

CREATE INDEX idx_hr_employees_org_status
    ON hr_employees (organization_id, employment_status);

CREATE INDEX idx_hr_employees_org_dept
    ON hr_employees (organization_id, department_id);

CREATE INDEX idx_hr_employees_org_manager
    ON hr_employees (organization_id, reporting_manager_id);

ALTER TABLE hr_departments
    ADD CONSTRAINT fk_hr_departments_head
        FOREIGN KEY (head_employee_id) REFERENCES hr_employees (id) ON DELETE SET NULL;

CREATE TABLE hr_attendance (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    organization_id  UUID         NOT NULL,
    employee_id      UUID         NOT NULL,
    attendance_date  DATE         NOT NULL,
    status           VARCHAR(30)  NOT NULL,
    notes            VARCHAR(500),
    recorded_by      UUID,
    recorded_by_name VARCHAR(255),
    updated_by       UUID,
    updated_by_name  VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_hr_attendance          PRIMARY KEY (id),
    CONSTRAINT uq_hr_attendance_emp_date UNIQUE (organization_id, employee_id, attendance_date),
    CONSTRAINT fk_hr_attendance_org      FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_hr_attendance_emp      FOREIGN KEY (employee_id)     REFERENCES hr_employees (id)  ON DELETE CASCADE,
    CONSTRAINT fk_hr_attendance_rec      FOREIGN KEY (recorded_by)     REFERENCES users (id)         ON DELETE SET NULL,
    CONSTRAINT fk_hr_attendance_upd      FOREIGN KEY (updated_by)      REFERENCES users (id)         ON DELETE SET NULL,
    CONSTRAINT ck_hr_attendance_status   CHECK (status IN ('PRESENT', 'ABSENT', 'HALF_DAY', 'ON_LEAVE'))
);

CREATE INDEX idx_hr_attendance_org_date
    ON hr_attendance (organization_id, attendance_date DESC);

CREATE INDEX idx_hr_attendance_org_emp_date
    ON hr_attendance (organization_id, employee_id, attendance_date DESC);

CREATE TABLE hr_leave_requests (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    organization_id  UUID          NOT NULL,
    employee_id      UUID          NOT NULL,
    leave_type       VARCHAR(30)   NOT NULL,
    start_date       DATE          NOT NULL,
    end_date         DATE          NOT NULL,
    total_days       INTEGER       NOT NULL,
    reason           VARCHAR(1000) NOT NULL,
    status           VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    submitted_by     UUID,
    submitted_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    reviewer_user_id UUID,
    reviewer_name    VARCHAR(255),
    review_comments  VARCHAR(1000),
    reviewed_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_hr_leave_requests      PRIMARY KEY (id),
    CONSTRAINT fk_hr_leave_org           FOREIGN KEY (organization_id)  REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_hr_leave_emp           FOREIGN KEY (employee_id)      REFERENCES hr_employees (id)  ON DELETE CASCADE,
    CONSTRAINT fk_hr_leave_sub_by        FOREIGN KEY (submitted_by)     REFERENCES users (id)         ON DELETE SET NULL,
    CONSTRAINT fk_hr_leave_rev_by        FOREIGN KEY (reviewer_user_id) REFERENCES users (id)         ON DELETE SET NULL,
    CONSTRAINT ck_hr_leave_type          CHECK (leave_type IN ('CASUAL_LEAVE', 'SICK_LEAVE', 'EARNED_LEAVE', 'UNPAID_LEAVE')),
    CONSTRAINT ck_hr_leave_dates         CHECK (end_date >= start_date),
    CONSTRAINT ck_hr_leave_days          CHECK (total_days >= 1),
    CONSTRAINT ck_hr_leave_reason        CHECK (LENGTH(TRIM(reason)) > 0),
    CONSTRAINT ck_hr_leave_status        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

CREATE INDEX idx_hr_leave_org_status
    ON hr_leave_requests (organization_id, status, submitted_at DESC);

CREATE INDEX idx_hr_leave_org_emp_dates
    ON hr_leave_requests (organization_id, employee_id, start_date, end_date);

CREATE TABLE hr_leave_audit_logs (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    organization_id  UUID          NOT NULL,
    leave_request_id UUID          NOT NULL,
    from_status      VARCHAR(30),
    to_status        VARCHAR(30)   NOT NULL,
    comment          VARCHAR(1000),
    actor_id         UUID,
    actor_name       VARCHAR(255),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_hr_leave_audit     PRIMARY KEY (id),
    CONSTRAINT fk_hr_la_org          FOREIGN KEY (organization_id)  REFERENCES organizations (id)     ON DELETE CASCADE,
    CONSTRAINT fk_hr_la_req          FOREIGN KEY (leave_request_id) REFERENCES hr_leave_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_hr_la_actor        FOREIGN KEY (actor_id)         REFERENCES users (id)             ON DELETE SET NULL
);

CREATE INDEX idx_hr_leave_audit_org_req
    ON hr_leave_audit_logs (organization_id, leave_request_id, created_at DESC);

