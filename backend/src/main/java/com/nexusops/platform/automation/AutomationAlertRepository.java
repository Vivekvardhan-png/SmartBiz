package com.nexusops.platform.automation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AutomationAlertRepository extends JpaRepository<AutomationAlert, UUID> {

    Optional<AutomationAlert> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndDedupeKeyAndStatus(
            UUID organizationId,
            String dedupeKey,
            AutomationAlertStatus status
    );

    boolean existsByOrganizationIdAndDedupeKey(UUID organizationId, String dedupeKey);

    Page<AutomationAlert> findAllByOrganizationIdOrderByCreatedAtDesc(
            UUID organizationId,
            Pageable pageable
    );

    Page<AutomationAlert> findAllByOrganizationIdAndStatusOrderByCreatedAtDesc(
            UUID organizationId,
            AutomationAlertStatus status,
            Pageable pageable
    );
}
