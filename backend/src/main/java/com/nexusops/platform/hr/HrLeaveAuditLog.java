package com.nexusops.platform.hr;

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
@Table(name = "hr_leave_audit_logs")
public class HrLeaveAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "leave_request_id", nullable = false, updatable = false)
    private UUID leaveRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private LeaveRequestStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private LeaveRequestStatus toStatus;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_name", length = 255)
    private String actorName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HrLeaveAuditLog() {
    }

    public HrLeaveAuditLog(
            UUID organizationId,
            UUID leaveRequestId,
            LeaveRequestStatus fromStatus,
            LeaveRequestStatus toStatus,
            String comment,
            UUID actorId,
            String actorName) {
        this.organizationId = organizationId;
        this.leaveRequestId = leaveRequestId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.comment = comment;
        this.actorId = actorId;
        this.actorName = actorName;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getLeaveRequestId() {
        return leaveRequestId;
    }

    public LeaveRequestStatus getFromStatus() {
        return fromStatus;
    }

    public LeaveRequestStatus getToStatus() {
        return toStatus;
    }

    public String getComment() {
        return comment;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

