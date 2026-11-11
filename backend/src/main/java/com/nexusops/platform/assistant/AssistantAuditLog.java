package com.nexusops.platform.assistant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assistant_audit_logs")
public class AssistantAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "event_type", nullable = false, length = 40, updatable = false)
    private String eventType;

    @Column(name = "tool_name", length = 80, updatable = false)
    private String toolName;

    @Column(name = "outcome", nullable = false, length = 40, updatable = false)
    private String outcome;

    @Column(name = "details", length = 1000, updatable = false)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssistantAuditLog() {
    }

    public AssistantAuditLog(
            UUID organizationId,
            UUID userId,
            String eventType,
            String toolName,
            String outcome,
            String details) {
        this.organizationId = organizationId;
        this.userId = userId;
        this.eventType = eventType;
        this.toolName = toolName;
        this.outcome = outcome;
        this.details = details != null && details.length() > 1000 ? details.substring(0, 1000) : details;
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

    public UUID getUserId() {
        return userId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getToolName() {
        return toolName;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
