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
@Table(name = "automation_runs")
public class AutomationRun {

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
    @Column(name = "trigger_source", nullable = false, length = 30)
    private AutomationTriggerSource triggerSource;

    @Column(name = "triggered_by_user_id", updatable = false)
    private UUID triggeredByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AutomationRunStatus status;

    @Column(name = "alerts_created", nullable = false)
    private int alertsCreated;

    @Column(name = "alerts_skipped_duplicate", nullable = false)
    private int alertsSkippedDuplicate;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AutomationRun() {
    }

    public AutomationRun(
            UUID organizationId,
            AutomationWorkflowType workflowType,
            AutomationTriggerSource triggerSource,
            UUID triggeredByUserId) {
        this.organizationId = organizationId;
        this.workflowType = workflowType;
        this.triggerSource = triggerSource;
        this.triggeredByUserId = triggeredByUserId;
        this.status = AutomationRunStatus.COMPLETED;
    }

    @PrePersist
    void onCreate() {
        if (this.startedAt == null) {
            this.startedAt = Instant.now();
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

    public AutomationTriggerSource getTriggerSource() {
        return triggerSource;
    }

    public UUID getTriggeredByUserId() {
        return triggeredByUserId;
    }

    public AutomationRunStatus getStatus() {
        return status;
    }

    public void markCompleted(int alertsCreated, int alertsSkippedDuplicate) {
        this.status = AutomationRunStatus.COMPLETED;
        this.alertsCreated = alertsCreated;
        this.alertsSkippedDuplicate = alertsSkippedDuplicate;
        this.completedAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.status = AutomationRunStatus.FAILED;
        this.errorMessage = errorMessage != null && errorMessage.length() > 500
                ? errorMessage.substring(0, 500)
                : errorMessage;
        this.completedAt = Instant.now();
    }

    public int getAlertsCreated() {
        return alertsCreated;
    }

    public int getAlertsSkippedDuplicate() {
        return alertsSkippedDuplicate;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
