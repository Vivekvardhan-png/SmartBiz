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
        name = "hr_employees",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_hr_employees_org_code", columnNames = {"organization_id", "employee_code"}),
                @UniqueConstraint(name = "uq_hr_employees_org_email", columnNames = {"organization_id", "email"})
        }
)
public class HrEmployee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "employee_code", nullable = false, length = 40, updatable = false)
    private String employeeCode;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "first_name", nullable = false, length = 120)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 120)
    private String lastName;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "phone", nullable = false, length = 50)
    private String phone;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "designation", nullable = false, length = 150)
    private String designation;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 30)
    private EmploymentType employmentType;

    @Column(name = "date_of_joining", nullable = false)
    private LocalDate dateOfJoining;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status", nullable = false, length = 30)
    private EmploymentStatus employmentStatus = EmploymentStatus.ACTIVE;

    @Column(name = "reporting_manager_id")
    private UUID reportingManagerId;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HrEmployee() {
    }

    public HrEmployee(
            UUID organizationId,
            String employeeCode,
            String firstName,
            String lastName,
            String email,
            String phone,
            UUID departmentId,
            String designation,
            EmploymentType employmentType,
            LocalDate dateOfJoining,
            UUID createdBy) {
        this.organizationId = organizationId;
        this.employeeCode = employeeCode;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.departmentId = departmentId;
        this.designation = designation;
        this.employmentType = employmentType;
        this.dateOfJoining = dateOfJoining;
        this.employmentStatus = EmploymentStatus.ACTIVE;
        this.createdBy = createdBy;
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

    public String fullName() {
        return firstName + " " + lastName;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(EmploymentType employmentType) {
        this.employmentType = employmentType;
    }

    public LocalDate getDateOfJoining() {
        return dateOfJoining;
    }

    public void setDateOfJoining(LocalDate dateOfJoining) {
        this.dateOfJoining = dateOfJoining;
    }

    public EmploymentStatus getEmploymentStatus() {
        return employmentStatus;
    }

    public void setEmploymentStatus(EmploymentStatus employmentStatus) {
        this.employmentStatus = employmentStatus;
    }

    public UUID getReportingManagerId() {
        return reportingManagerId;
    }

    public void setReportingManagerId(UUID reportingManagerId) {
        this.reportingManagerId = reportingManagerId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

