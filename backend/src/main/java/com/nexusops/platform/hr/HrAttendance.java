package com.nexusops.platform.hr;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "hr_attendance",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_hr_attendance_emp_date",
                columnNames = {"organization_id", "employee_id", "attendance_date"}
        )
)
public class HrAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AttendanceStatus status;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "recorded_by", updatable = false)
    private UUID recordedBy;

    @Column(name = "recorded_by_name", length = 255, updatable = false)
    private String recordedByName;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_by_name", length = 255)
    private String updatedByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HrAttendance() {
    }

    public HrAttendance(
            UUID organizationId,
            UUID employeeId,
            LocalDate attendanceDate,
            AttendanceStatus status,
            String notes,
            UUID recordedBy,
            String recordedByName) {
        this.organizationId = organizationId;
        this.employeeId = employeeId;
        this.attendanceDate = attendanceDate;
        this.status = status;
        this.notes = notes;
        this.recordedBy = recordedBy;
        this.recordedByName = recordedByName;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public void setStatus(AttendanceStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }

    public String getRecordedByName() {
        return recordedByName;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getUpdatedByName() {
        return updatedByName;
    }

    public void setUpdatedByName(String updatedByName) {
        this.updatedByName = updatedByName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

