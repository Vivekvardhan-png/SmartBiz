package com.nexusops.platform.crm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CrmNoteRepository extends JpaRepository<CrmNote, UUID> {

    List<CrmNote> findAllByOrganizationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            UUID organizationId,
            CrmNoteEntityType entityType,
            UUID entityId
    );
}

