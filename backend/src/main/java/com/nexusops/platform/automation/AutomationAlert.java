package com.nexusops.platform.automation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "automation_alerts")
public class AutomationAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_type", nullable = false, length = 50)
    private AutomationWorkflowType workflowType;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 40)
    private AutomationEntityType entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Column(name = "dedupe_key", nullable = false, length = 160, updatable = false)
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private AutomationAlertSeverity severity;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AutomationAlertStatus status = AutomationAlertStatus.OPEN;

    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @Column(name = "acknowledged_by_user_id")
    private UUID acknowledgedByUserId;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AutomationAlert() {
    }

    public AutomationAlert(
            UUID organizationId,
            AutomationWorkflowType workflowType,
            AutomationEntityType entityType,
            UUID entityId,
            String dedupeKey,
            AutomationAlertSeverity severity,
            String title,
            String message,
            UUID runId) {
        this.organizationId = organizationId;
        this.workflowType = workflowType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.dedupeKey = dedupeKey;
        this.severity = severity;
        this.title = title;
        this.message = message;
        this.runId = runId;
        this.status = AutomationAlertStatus.OPEN;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public AutomationWorkflowType getWorkflowType() {
        return workflowType;
    }

    public AutomationEntityType getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public AutomationAlertSeverity getSeverity() {
        return severity;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public AutomationAlertStatus getStatus() {
        return status;
    }

    public UUID getRunId() {
        return runId;
    }

    public UUID getAcknowledgedByUserId() {
        return acknowledgedByUserId;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void acknowledge(UUID userId) {
        this.status = AutomationAlertStatus.ACKNOWLEDGED;
        this.acknowledgedByUserId = userId;
        this.acknowledgedAt = Instant.now();
    }
}
