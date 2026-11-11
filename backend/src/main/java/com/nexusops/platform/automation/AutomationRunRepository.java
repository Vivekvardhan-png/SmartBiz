package com.nexusops.platform.automation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AutomationRunRepository extends JpaRepository<AutomationRun, UUID> {

    Page<AutomationRun> findAllByOrganizationIdOrderByStartedAtDesc(
            UUID organizationId,
            Pageable pageable
    );
}
