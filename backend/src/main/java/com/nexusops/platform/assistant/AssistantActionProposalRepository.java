package com.nexusops.platform.assistant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssistantActionProposalRepository
        extends JpaRepository<AssistantActionProposal, UUID> {

    Optional<AssistantActionProposal> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<AssistantActionProposal> findAllByOrganizationIdAndProposedByUserIdOrderByCreatedAtDesc(
            UUID organizationId,
            UUID proposedByUserId,
            Pageable pageable
    );
}
