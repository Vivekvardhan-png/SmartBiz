package com.nexusops.platform.crm;

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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "crm_notes")
public class CrmNote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false, length = 20)
    private CrmNoteEntityType entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "author_id", updatable = false)
    private UUID authorId;

    @Column(name = "author_name", length = 255)
    private String authorName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CrmNote() {
    }

    public CrmNote(
            UUID organizationId,
            CrmNoteEntityType entityType,
            UUID entityId,
            String content,
            LocalDate followUpDate,
            UUID authorId,
            String authorName) {
        this.organizationId = organizationId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.content = content;
        this.followUpDate = followUpDate;
        this.authorId = authorId;
        this.authorName = authorName;
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

    public CrmNoteEntityType getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getContent() {
        return content;
    }

    public LocalDate getFollowUpDate() {
        return followUpDate;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

