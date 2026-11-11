package com.nexusops.platform.crm;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "leads")
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "contact_name", nullable = false, length = 255)
    private String contactName;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "source", length = 100)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LeadStatus status = LeadStatus.NEW;

    @Column(name = "estimated_value", precision = 14, scale = 2)
    private BigDecimal estimatedValue;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "converted_customer_id")
    private UUID convertedCustomerId;

    @Column(name = "converted_at")
    private Instant convertedAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Lead() {
    }

    public Lead(UUID organizationId, String title, String contactName, UUID createdBy) {
        this.organizationId = organizationId;
        this.title = title;
        this.contactName = contactName;
        this.createdBy = createdBy;
        this.status = LeadStatus.NEW;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LeadStatus getStatus() {
        return status;
    }

    public void setStatus(LeadStatus status) {
        this.status = status;
    }

    public BigDecimal getEstimatedValue() {
        return estimatedValue;
    }

    public void setEstimatedValue(BigDecimal estimatedValue) {
        this.estimatedValue = estimatedValue;
    }

    public LocalDate getFollowUpDate() {
        return followUpDate;
    }

    public void setFollowUpDate(LocalDate followUpDate) {
        this.followUpDate = followUpDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public UUID getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(UUID assignedTo) {
        this.assignedTo = assignedTo;
    }

    public UUID getConvertedCustomerId() {
        return convertedCustomerId;
    }

    public void markConverted(UUID customerId, Instant convertedAt) {
        this.status = LeadStatus.CONVERTED;
        this.convertedCustomerId = customerId;
        this.convertedAt = convertedAt;
    }

    public Instant getConvertedAt() {
        return convertedAt;
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

