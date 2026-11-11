package com.nexusops.platform.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrDepartmentRepository extends JpaRepository<HrDepartment, UUID> {

    List<HrDepartment> findAllByOrganizationIdOrderByNameAsc(UUID organizationId);

    Optional<HrDepartment> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);

    boolean existsByOrganizationIdAndNameIgnoreCaseAndIdNot(UUID organizationId, String name, UUID id);

    List<HrDepartment> findAllByOrganizationIdAndHeadEmployeeId(UUID organizationId, UUID headEmployeeId);
}

