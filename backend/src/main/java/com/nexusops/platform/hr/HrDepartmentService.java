package com.nexusops.platform.hr;

import com.nexusops.platform.common.exception.BusinessRuleException;
import com.nexusops.platform.common.exception.ConflictException;
import com.nexusops.platform.common.exception.ResourceNotFoundException;
import com.nexusops.platform.hr.dto.HrDtos.CreateDepartmentRequest;
import com.nexusops.platform.hr.dto.HrDtos.HrDepartmentResponse;
import com.nexusops.platform.hr.dto.HrDtos.UpdateDepartmentRequest;
import com.nexusops.platform.security.TenantPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class HrDepartmentService {

    private final HrDepartmentRepository departmentRepository;
    private final HrEmployeeRepository employeeRepository;

    public HrDepartmentService(
            HrDepartmentRepository departmentRepository,
            HrEmployeeRepository employeeRepository) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public List<HrDepartmentResponse> listDepartments(TenantPrincipal principal) {
        UUID orgId = principal.organizationId();
        List<HrDepartment> departments = departmentRepository.findAllByOrganizationIdOrderByNameAsc(orgId);
        List<HrEmployee> allEmployees = employeeRepository.findAllByOrganizationIdOrderByFirstNameAscLastNameAsc(orgId);

        Map<UUID, Long> countsByDept = allEmployees.stream()
                .collect(Collectors.groupingBy(HrEmployee::getDepartmentId, Collectors.counting()));
        Map<UUID, String> employeeNamesById = allEmployees.stream()
                .collect(Collectors.toMap(HrEmployee::getId, HrEmployee::fullName));

        return departments.stream()
                .map(dept -> toResponse(
                        dept,
                        dept.getHeadEmployeeId() != null ? employeeNamesById.get(dept.getHeadEmployeeId()) : null,
                        countsByDept.getOrDefault(dept.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public HrDepartmentResponse getDepartment(TenantPrincipal principal, UUID departmentId) {
        UUID orgId = principal.organizationId();
        HrDepartment dept = departmentRepository.findByIdAndOrganizationId(departmentId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));
        return buildSingleResponse(dept, orgId);
    }

    @Transactional
    public HrDepartmentResponse createDepartment(TenantPrincipal principal, CreateDepartmentRequest request) {
        UUID orgId = principal.organizationId();
        String name = request.name().trim();
        if (departmentRepository.existsByOrganizationIdAndNameIgnoreCase(orgId, name)) {
            throw new ConflictException("A department named '" + name + "' already exists in this organization.");
        }

        HrDepartment department = new HrDepartment(
                orgId,
                name,
                HrScopeService.trimToNull(request.description()),
                principal.userId());

        if (request.headEmployeeId() != null) {
            HrEmployee head = employeeRepository.findByIdAndOrganizationId(request.headEmployeeId(), orgId)
                    .orElseThrow(() -> new ResourceNotFoundException("Employee", request.headEmployeeId()));
            department.setHeadEmployeeId(head.getId());
        }

        HrDepartment saved = departmentRepository.save(department);
        return buildSingleResponse(saved, orgId);
    }

    @Transactional
    public HrDepartmentResponse updateDepartment(
            TenantPrincipal principal,
            UUID departmentId,
            UpdateDepartmentRequest request) {
        UUID orgId = principal.organizationId();
        HrDepartment department = departmentRepository.findByIdAndOrganizationId(departmentId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));

        if (request.name() != null) {
            String trimmed = request.name().trim();
            if (trimmed.isEmpty()) {
                throw new BusinessRuleException("Department name cannot be blank.");
            }
            if (departmentRepository.existsByOrganizationIdAndNameIgnoreCaseAndIdNot(orgId, trimmed, department.getId())) {
                throw new ConflictException("A department named '" + trimmed + "' already exists in this organization.");
            }
            department.setName(trimmed);
        }

        if (request.description() != null) {
            department.setDescription(HrScopeService.trimToNull(request.description()));
        }

        if (Boolean.TRUE.equals(request.clearHeadEmployee())) {
            department.setHeadEmployeeId(null);
        } else if (request.headEmployeeId() != null) {
            HrEmployee head = employeeRepository.findByIdAndOrganizationId(request.headEmployeeId(), orgId)
                    .orElseThrow(() -> new ResourceNotFoundException("Employee", request.headEmployeeId()));
            department.setHeadEmployeeId(head.getId());
        }

        HrDepartment saved = departmentRepository.save(department);
        return buildSingleResponse(saved, orgId);
    }

    @Transactional
    public void deleteDepartment(TenantPrincipal principal, UUID departmentId) {
        UUID orgId = principal.organizationId();
        HrDepartment department = departmentRepository.findByIdAndOrganizationId(departmentId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));

        if (employeeRepository.existsByOrganizationIdAndDepartmentId(orgId, department.getId())) {
            throw new BusinessRuleException(
                    "Cannot delete department '" + department.getName()
                            + "' because employees are still assigned to it. Reassign employees first.");
        }

        departmentRepository.delete(department);
    }

    private HrDepartmentResponse buildSingleResponse(HrDepartment dept, UUID orgId) {
        String headName = null;
        if (dept.getHeadEmployeeId() != null) {
            headName = employeeRepository.findByIdAndOrganizationId(dept.getHeadEmployeeId(), orgId)
                    .map(HrEmployee::fullName)
                    .orElse(null);
        }
        long count = employeeRepository.countByOrganizationIdAndDepartmentId(orgId, dept.getId());
        return toResponse(dept, headName, count);
    }

    private static HrDepartmentResponse toResponse(HrDepartment dept, String headName, long count) {
        return new HrDepartmentResponse(
                dept.getId(),
                dept.getName(),
                dept.getDescription(),
                dept.getHeadEmployeeId(),
                headName,
                count,
                dept.getCreatedAt(),
                dept.getUpdatedAt()
        );
    }
}

