package com.nexusops.platform.assistant;

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
@Table(name = "assistant_action_proposals")
public class AssistantActionProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "proposed_by_user_id", nullable = false, updatable = false)
    private UUID proposedByUserId;

    @Column(name = "tool_name", nullable = false, length = 80, updatable = false)
    private String toolName;

    @Column(name = "arguments_json", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String argumentsJson;

    @Column(name = "action_summary", nullable = false, length = 500, updatable = false)
    private String actionSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ProposalStatus status = ProposalStatus.PENDING;

    @Column(name = "result_summary", length = 500)
    private String resultSummary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected AssistantActionProposal() {
    }

    public AssistantActionProposal(
            UUID organizationId,
            UUID proposedByUserId,
            String toolName,
            String argumentsJson,
            String actionSummary,
            Instant expiresAt) {
        this.organizationId = organizationId;
        this.proposedByUserId = proposedByUserId;
        this.toolName = toolName;
        this.argumentsJson = argumentsJson;
        this.actionSummary = actionSummary;
        this.expiresAt = expiresAt;
        this.status = ProposalStatus.PENDING;
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

    public UUID getProposedByUserId() {
        return proposedByUserId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getArgumentsJson() {
        return argumentsJson;
    }

    public String getActionSummary() {
        return actionSummary;
    }

    public ProposalStatus getStatus() {
        return status;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void markExecuted(String summary) {
        this.status = ProposalStatus.EXECUTED;
        this.resultSummary = truncate(summary, 500);
        this.resolvedAt = Instant.now();
    }

    public void markCancelled() {
        this.status = ProposalStatus.CANCELLED;
        this.resultSummary = "Cancelled by user.";
        this.resolvedAt = Instant.now();
    }

    public void markExpired() {
        this.status = ProposalStatus.EXPIRED;
        this.resultSummary = "Proposal expired before confirmation.";
        this.resolvedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = ProposalStatus.FAILED;
        this.resultSummary = truncate(reason, 500);
        this.resolvedAt = Instant.now();
    }

    private static String truncate(String val, int maxLen) {
        if (val == null) {
            return null;
        }
        return val.length() > maxLen ? val.substring(0, maxLen) : val;
    }
}
