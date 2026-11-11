package com.nexusops.platform.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "invoice_sequences")
public class InvoiceSequence {

    @Id
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "next_number", nullable = false)
    private long nextNumber = 1L;

    protected InvoiceSequence() {
    }

    public InvoiceSequence(UUID organizationId, long nextNumber) {
        this.organizationId = organizationId;
        this.nextNumber = nextNumber;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public long getAndIncrement() {
        long current = this.nextNumber;
        this.nextNumber = current + 1L;
        return current;
    }
}

