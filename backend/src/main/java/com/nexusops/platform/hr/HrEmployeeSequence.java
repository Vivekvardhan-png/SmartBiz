package com.nexusops.platform.hr;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "hr_employee_sequences")
public class HrEmployeeSequence {

    @Id
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "next_number", nullable = false)
    private long nextNumber = 1L;

    protected HrEmployeeSequence() {
    }

    public HrEmployeeSequence(UUID organizationId, long nextNumber) {
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

