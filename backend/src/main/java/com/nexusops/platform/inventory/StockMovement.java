package com.nexusops.platform.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_movements")
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", updatable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false, length = 40)
    private StockMovementType movementType;

    @Column(name = "quantity_delta", nullable = false, updatable = false, precision = 14, scale = 3)
    private BigDecimal quantityDelta;

    @Column(name = "quantity_before", nullable = false, updatable = false, precision = 14, scale = 3)
    private BigDecimal quantityBefore;

    @Column(name = "quantity_after", nullable = false, updatable = false, precision = 14, scale = 3)
    private BigDecimal quantityAfter;

    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "reference", updatable = false, length = 255)
    private String reference;

    @Column(name = "idempotency_key", updatable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "actor_name", updatable = false, length = 255)
    private String actorName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockMovement() {
    }

    public StockMovement(
            UUID organizationId,
            Product product,
            Supplier supplier,
            StockMovementType movementType,
            BigDecimal quantityDelta,
            BigDecimal quantityBefore,
            BigDecimal quantityAfter,
            String reason,
            String reference,
            String idempotencyKey,
            UUID actorId,
            String actorName) {
        this.organizationId = organizationId;
        this.product = product;
        this.supplier = supplier;
        this.movementType = movementType;
        this.quantityDelta = quantityDelta;
        this.quantityBefore = quantityBefore;
        this.quantityAfter = quantityAfter;
        this.reason = reason;
        this.reference = reference;
        this.idempotencyKey = idempotencyKey;
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

    public Product getProduct() {
        return product;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public StockMovementType getMovementType() {
        return movementType;
    }

    public BigDecimal getQuantityDelta() {
        return quantityDelta;
    }

    public BigDecimal getQuantityBefore() {
        return quantityBefore;
    }

    public BigDecimal getQuantityAfter() {
        return quantityAfter;
    }

    public String getReason() {
        return reason;
    }

    public String getReference() {
        return reference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
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

