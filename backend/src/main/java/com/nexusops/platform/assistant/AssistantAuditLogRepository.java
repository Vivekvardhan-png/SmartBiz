package com.nexusops.platform.assistant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AssistantAuditLogRepository extends JpaRepository<AssistantAuditLog, UUID> {

    Page<AssistantAuditLog> findAllByOrganizationIdOrderByCreatedAtDesc(
            UUID organizationId,
            Pageable pageable
    );
}
