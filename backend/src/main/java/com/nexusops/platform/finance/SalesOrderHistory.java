package com.nexusops.platform.finance;

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
@Table(name = "sales_order_history")
public class SalesOrderHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "sales_order_id", nullable = false, updatable = false)
    private UUID salesOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private SalesOrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private SalesOrderStatus toStatus;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "actor_name", length = 255)
    private String actorName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SalesOrderHistory() {
    }

    public SalesOrderHistory(
            UUID organizationId,
            UUID salesOrderId,
            SalesOrderStatus fromStatus,
            SalesOrderStatus toStatus,
            String note,
            UUID actorId,
            String actorName) {
        this.organizationId = organizationId;
        this.salesOrderId = salesOrderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.note = note;
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

    public SalesOrderStatus getFromStatus() {
        return fromStatus;
    }

    public SalesOrderStatus getToStatus() {
        return toStatus;
    }

    public String getNote() {
        return note;
    }

    public String getActorName() {
        return actorName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

