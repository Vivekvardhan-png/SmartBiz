package com.nexusops.platform.crm;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadRepository
        extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {

    Optional<Lead> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lead l WHERE l.id = :id AND l.organizationId = :organizationId")
    Optional<Lead> findForUpdateByIdAndOrganizationId(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId
    );

    long countByOrganizationIdAndStatusIn(UUID organizationId, Collection<LeadStatus> statuses);

    @Query("""
            SELECT l.status AS status, COUNT(l.id) AS total
            FROM Lead l
            WHERE l.organizationId = :organizationId
            GROUP BY l.status
            """)
    List<LeadStageCount> countByStageForOrganization(@Param("organizationId") UUID organizationId);

    interface LeadStageCount {
        LeadStatus getStatus();
        long getTotal();
    }
}

