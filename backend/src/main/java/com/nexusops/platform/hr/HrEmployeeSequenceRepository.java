package com.nexusops.platform.hr;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface HrEmployeeSequenceRepository extends JpaRepository<HrEmployeeSequence, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM HrEmployeeSequence s WHERE s.organizationId = :organizationId")
    Optional<HrEmployeeSequence> findForUpdateByOrganizationId(@Param("organizationId") UUID organizationId);
}

