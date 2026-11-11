package com.nexusops.platform.hr;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrEmployeeRepository extends JpaRepository<HrEmployee, UUID>, JpaSpecificationExecutor<HrEmployee> {

    Optional<HrEmployee> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<HrEmployee> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    Optional<HrEmployee> findByOrganizationIdAndEmailIgnoreCase(UUID organizationId, String email);

    boolean existsByOrganizationIdAndEmailIgnoreCase(UUID organizationId, String email);

    boolean existsByOrganizationIdAndEmailIgnoreCaseAndIdNot(UUID organizationId, String email, UUID id);

    boolean existsByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    boolean existsByOrganizationIdAndUserIdAndIdNot(UUID organizationId, UUID userId, UUID id);

    boolean existsByOrganizationIdAndDepartmentId(UUID organizationId, UUID departmentId);

    long countByOrganizationIdAndDepartmentId(UUID organizationId, UUID departmentId);

    long countByOrganizationId(UUID organizationId);

    long countByOrganizationIdAndEmploymentStatus(UUID organizationId, EmploymentStatus status);

    List<HrEmployee> findAllByOrganizationIdOrderByFirstNameAscLastNameAsc(UUID organizationId);

    List<HrEmployee> findTop5ByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    List<HrEmployee> findTop5ByOrganizationIdAndIdInOrderByCreatedAtDesc(UUID organizationId, Collection<UUID> ids);

    List<HrEmployee> findAllByOrganizationIdAndReportingManagerId(UUID organizationId, UUID reportingManagerId);

    List<HrEmployee> findAllByOrganizationIdAndDepartmentIdIn(UUID organizationId, Collection<UUID> departmentIds);

    List<HrEmployee> findAllByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);
}

