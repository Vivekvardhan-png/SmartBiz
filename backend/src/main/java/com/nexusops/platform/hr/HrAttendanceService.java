package com.nexusops.platform.hr;

import com.nexusops.platform.common.api.PagedResponse;
import com.nexusops.platform.common.exception.BusinessRuleException;
import com.nexusops.platform.common.exception.ConflictException;
import com.nexusops.platform.common.exception.ResourceNotFoundException;
import com.nexusops.platform.hr.dto.HrDtos.HrAttendanceResponse;
import com.nexusops.platform.hr.dto.HrDtos.MarkAttendanceRequest;
import com.nexusops.platform.hr.dto.HrDtos.UpdateAttendanceRequest;
import com.nexusops.platform.security.TenantPrincipal;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class HrAttendanceService {

    private static final int MAX_PAGE_SIZE = 100;

    private final HrAttendanceRepository attendanceRepository;
    private final HrEmployeeRepository employeeRepository;
    private final HrDepartmentRepository departmentRepository;
    private final HrScopeService scopeService;

    public HrAttendanceService(
            HrAttendanceRepository attendanceRepository,
            HrEmployeeRepository employeeRepository,
            HrDepartmentRepository departmentRepository,
            HrScopeService scopeService) {
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.scopeService = scopeService;
    }

    @Transactional(readOnly = true)
    public PagedResponse<HrAttendanceResponse> listAttendance(
            TenantPrincipal principal,
            UUID employeeId,
            LocalDate date,
            LocalDate fromDate,
            LocalDate toDate,
            AttendanceStatus status,
            int page,
            int pageSize) {

        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        UUID orgId = principal.organizationId();

        if (employeeId != null) {
            scopeService.assertCanAccessEmployee(principal, employeeId);
        }

        Optional<Set<UUID>> permittedIds = scopeService.resolvePermittedEmployeeIds(principal);
        if (permittedIds.isPresent() && permittedIds.get().isEmpty()) {
            return PagedResponse.of(List.of(), safePage, safeSize, 0);
        }

        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new BusinessRuleException("Attendance filter 'toDate' cannot precede 'fromDate'.");
        }

        Specification<HrAttendance> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), orgId));
            if (permittedIds.isPresent()) {
                predicates.add(root.get("employeeId").in(permittedIds.get()));
            }
            if (employeeId != null) {
                predicates.add(cb.equal(root.get("employeeId"), employeeId));
            }
            if (date != null) {
                predicates.add(cb.equal(root.get("attendanceDate"), date));
            }
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("attendanceDate"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("attendanceDate"), toDate));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<HrAttendance> result = attendanceRepository.findAll(
                spec,
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "attendanceDate", "createdAt"))
        );

        return PagedResponse.of(
                mapResponses(orgId, result.getContent()),
                safePage,
                safeSize,
                result.getTotalElements());
    }

    @Transactional
    public HrAttendanceResponse markAttendance(TenantPrincipal principal, MarkAttendanceRequest request) {
        UUID orgId = principal.organizationId();
        HrEmployee employee = employeeRepository.findByIdAndOrganizationId(request.employeeId(), orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", request.employeeId()));

        if (employee.getEmploymentStatus() != EmploymentStatus.ACTIVE) {
            throw new BusinessRuleException("Attendance cannot be marked for an inactive employee.");
        }

        if (attendanceRepository.existsByOrganizationIdAndEmployeeIdAndAttendanceDate(
                orgId, employee.getId(), request.attendanceDate())) {
            throw new ConflictException(
                    "Attendance for " + employee.fullName() + " on " + request.attendanceDate() + " already exists.");
        }

        HrAttendance record = new HrAttendance(
                orgId,
                employee.getId(),
                request.attendanceDate(),
                request.status(),
                HrScopeService.trimToNull(request.notes()),
                principal.userId(),
                HrScopeService.resolveActorName(principal));

        HrAttendance saved = attendanceRepository.save(record);
        return mapResponses(orgId, List.of(saved)).getFirst();
    }

    @Transactional
    public HrAttendanceResponse updateAttendance(
            TenantPrincipal principal,
            UUID attendanceId,
            UpdateAttendanceRequest request) {
        UUID orgId = principal.organizationId();
        HrAttendance record = attendanceRepository.findByIdAndOrganizationId(attendanceId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance", attendanceId));

        record.setStatus(request.status());
        if (request.notes() != null) {
            record.setNotes(HrScopeService.trimToNull(request.notes()));
        }
        record.setUpdatedBy(principal.userId());
        record.setUpdatedByName(HrScopeService.resolveActorName(principal));

        HrAttendance saved = attendanceRepository.save(record);
        return mapResponses(orgId, List.of(saved)).getFirst();
    }

    private List<HrAttendanceResponse> mapResponses(UUID orgId, List<HrAttendance> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Set<UUID> employeeIds = records.stream().map(HrAttendance::getEmployeeId).collect(Collectors.toSet());
        Map<UUID, HrEmployee> employeesById = employeeRepository
                .findAllByOrganizationIdAndIdIn(orgId, employeeIds).stream()
                .collect(Collectors.toMap(HrEmployee::getId, e -> e));
        Map<UUID, String> departmentNames = departmentRepository
                .findAllByOrganizationIdOrderByNameAsc(orgId).stream()
                .collect(Collectors.toMap(HrDepartment::getId, HrDepartment::getName));

        return records.stream().map(rec -> {
            HrEmployee emp = employeesById.get(rec.getEmployeeId());
            String deptName = emp != null
                    ? departmentNames.getOrDefault(emp.getDepartmentId(), "Department")
                    : "Department";
            return new HrAttendanceResponse(
                    rec.getId(),
                    rec.getEmployeeId(),
                    emp != null ? emp.getEmployeeCode() : "—",
                    emp != null ? emp.fullName() : "Employee",
                    deptName,
                    rec.getAttendanceDate(),
                    rec.getStatus(),
                    rec.getNotes(),
                    rec.getRecordedByName(),
                    rec.getUpdatedByName(),
                    rec.getCreatedAt(),
                    rec.getUpdatedAt()
            );
        }).toList();
    }
}

