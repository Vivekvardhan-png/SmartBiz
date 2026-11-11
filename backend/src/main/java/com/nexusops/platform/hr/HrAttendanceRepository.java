package com.nexusops.platform.hr;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrAttendanceRepository extends JpaRepository<HrAttendance, UUID>, JpaSpecificationExecutor<HrAttendance> {

    Optional<HrAttendance> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndEmployeeIdAndAttendanceDate(UUID organizationId, UUID employeeId, LocalDate date);

    List<HrAttendance> findAllByOrganizationIdAndAttendanceDate(UUID organizationId, LocalDate date);

    List<HrAttendance> findAllByOrganizationIdAndAttendanceDateAndEmployeeIdIn(
            UUID organizationId, LocalDate date, Collection<UUID> employeeIds);
}

