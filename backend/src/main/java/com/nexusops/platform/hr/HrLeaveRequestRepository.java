package com.nexusops.platform.hr;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrLeaveRequestRepository extends JpaRepository<HrLeaveRequest, UUID>, JpaSpecificationExecutor<HrLeaveRequest> {

    Optional<HrLeaveRequest> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("""
            SELECT COUNT(r) > 0 FROM HrLeaveRequest r
            WHERE r.organizationId = :orgId
              AND r.employeeId = :employeeId
              AND r.status IN ('PENDING', 'APPROVED')
              AND r.startDate <= :endDate
              AND r.endDate >= :startDate
            """)
    boolean existsOverlappingActiveLeave(
            @Param("orgId") UUID organizationId,
            @Param("employeeId") UUID employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    long countByOrganizationIdAndStatus(UUID organizationId, LeaveRequestStatus status);

    long countByOrganizationIdAndStatusAndEmployeeIdIn(
            UUID organizationId, LeaveRequestStatus status, Collection<UUID> employeeIds);

    List<HrLeaveRequest> findAllByOrganizationIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID organizationId, LeaveRequestStatus status, LocalDate date1, LocalDate date2);

    List<HrLeaveRequest> findTop5ByOrganizationIdOrderBySubmittedAtDesc(UUID organizationId);

    List<HrLeaveRequest> findTop5ByOrganizationIdAndEmployeeIdInOrderBySubmittedAtDesc(
            UUID organizationId, Collection<UUID> employeeIds);
}

