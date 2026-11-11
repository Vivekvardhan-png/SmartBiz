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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false, length = 20)
    private PaymentEntryType entryType;

    @Column(name = "amount", nullable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false, updatable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, updatable = false, length = 30)
    private PaymentMethod method;

    @Column(name = "reference", updatable = false, length = 255)
    private String reference;

    @Column(name = "notes", updatable = false, length = 1000)
    private String notes;

    @Column(name = "idempotency_key", updatable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "recorded_by", updatable = false)
    private UUID recordedBy;

    @Column(name = "recorded_by_name", updatable = false, length = 255)
    private String recordedByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Payment() {
    }

    public Payment(
            UUID organizationId,
            UUID invoiceId,
            PaymentEntryType entryType,
            BigDecimal amount,
            LocalDate paymentDate,
            PaymentMethod method,
            String reference,
            String notes,
            String idempotencyKey,
            UUID recordedBy,
            String recordedByName) {
        this.organizationId = organizationId;
        this.invoiceId = invoiceId;
        this.entryType = entryType;
        this.amount = amount;
        this.paymentDate = paymentDate;
        this.method = method;
        this.reference = reference;
        this.notes = notes;
        this.idempotencyKey = idempotencyKey;
        this.recordedBy = recordedBy;
        this.recordedByName = recordedByName;
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

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public PaymentEntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public String getReference() {
        return reference;
    }

    public String getNotes() {
        return notes;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRecordedByName() {
        return recordedByName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

