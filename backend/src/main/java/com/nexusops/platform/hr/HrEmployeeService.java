package com.nexusops.platform.hr;

import com.nexusops.platform.common.api.PagedResponse;
import com.nexusops.platform.common.exception.AccessDeniedException;
import com.nexusops.platform.common.exception.BusinessRuleException;
import com.nexusops.platform.common.exception.ConflictException;
import com.nexusops.platform.common.exception.ResourceNotFoundException;
import com.nexusops.platform.hr.dto.HrDtos.CreateEmployeeRequest;
import com.nexusops.platform.hr.dto.HrDtos.HrEmployeeResponse;
import com.nexusops.platform.hr.dto.HrDtos.UpdateEmployeeRequest;
import com.nexusops.platform.identity.OrganizationMemberRepository;
import com.nexusops.platform.security.TenantPrincipal;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class HrEmployeeService {

    private static final int MAX_PAGE_SIZE = 100;

    private final HrEmployeeRepository employeeRepository;
    private final HrDepartmentRepository departmentRepository;
    private final HrEmployeeSequenceRepository sequenceRepository;
    private final OrganizationMemberRepository memberRepository;
    private final HrScopeService scopeService;

    public HrEmployeeService(
            HrEmployeeRepository employeeRepository,
            HrDepartmentRepository departmentRepository,
            HrEmployeeSequenceRepository sequenceRepository,
            OrganizationMemberRepository memberRepository,
            HrScopeService scopeService) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.sequenceRepository = sequenceRepository;
        this.memberRepository = memberRepository;
        this.scopeService = scopeService;
    }

    public record MyEmployeeProfileResponse(boolean linked, HrEmployeeResponse employee) {
    }

    @Transactional(readOnly = true)
    public PagedResponse<HrEmployeeResponse> listEmployees(
            TenantPrincipal principal,
            String search,
            UUID departmentId,
            String designation,
            EmploymentStatus status,
            int page,
            int pageSize) {

        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        UUID orgId = principal.organizationId();

        Optional<Set<UUID>> permittedIds = scopeService.resolvePermittedEmployeeIds(principal);
        if (permittedIds.isPresent() && permittedIds.get().isEmpty()) {
            return PagedResponse.of(List.of(), safePage, safeSize, 0);
        }

        Specification<HrEmployee> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), orgId));
            if (permittedIds.isPresent()) {
                predicates.add(root.get("id").in(permittedIds.get()));
            }
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("departmentId"), departmentId));
            }
            if (designation != null && !designation.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("designation")), designation.trim().toLowerCase(Locale.ROOT)));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("employmentStatus"), status));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("employeeCode")), pattern),
                        cb.like(cb.lower(root.get("firstName")), pattern),
                        cb.like(cb.lower(root.get("lastName")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(cb.lower(root.get("designation")), pattern)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<HrEmployee> result = employeeRepository.findAll(
                spec,
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.ASC, "firstName", "lastName"))
        );

        List<HrEmployeeResponse> items = mapResponses(orgId, result.getContent());
        return PagedResponse.of(items, safePage, safeSize, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public HrEmployeeResponse getEmployee(TenantPrincipal principal, UUID employeeId) {
        UUID orgId = principal.organizationId();
        HrEmployee employee = employeeRepository.findByIdAndOrganizationId(employeeId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
        scopeService.assertCanAccessEmployee(principal, employee.getId());
        return mapResponses(orgId, List.of(employee)).getFirst();
    }

    @Transactional(readOnly = true)
    public MyEmployeeProfileResponse getMyProfile(TenantPrincipal principal) {
        Optional<HrEmployee> ownOpt = scopeService.resolveOwnEmployee(principal);
        if (ownOpt.isEmpty()) {
            return new MyEmployeeProfileResponse(false, null);
        }
        HrEmployeeResponse response = mapResponses(principal.organizationId(), List.of(ownOpt.get())).getFirst();
        return new MyEmployeeProfileResponse(true, response);
    }

    @Transactional
    public HrEmployeeResponse createEmployee(TenantPrincipal principal, CreateEmployeeRequest request) {
        UUID orgId = principal.organizationId();
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (employeeRepository.existsByOrganizationIdAndEmailIgnoreCase(orgId, normalizedEmail)) {
            throw new ConflictException("An employee with email '" + normalizedEmail + "' already exists in this organization.");
        }

        HrDepartment department = departmentRepository.findByIdAndOrganizationId(request.departmentId(), orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", request.departmentId()));

        UUID managerId = null;
        if (request.reportingManagerId() != null) {
            HrEmployee manager = employeeRepository.findByIdAndOrganizationId(request.reportingManagerId(), orgId)
                    .orElseThrow(() -> new ResourceNotFoundException("Reporting manager", request.reportingManagerId()));
            managerId = manager.getId();
        }

        UUID userId = validateLinkedUser(orgId, request.userId(), null);

        String employeeCode = generateEmployeeCode(orgId);
        HrEmployee employee = new HrEmployee(
                orgId,
                employeeCode,
                request.firstName().trim(),
                request.lastName().trim(),
                normalizedEmail,
                request.phone().trim(),
                department.getId(),
                request.designation().trim(),
                request.employmentType(),
                request.dateOfJoining(),
                principal.userId());
        employee.setReportingManagerId(managerId);
        employee.setAddress(HrScopeService.trimToNull(request.address()));
        employee.setUserId(userId);

        HrEmployee saved = employeeRepository.save(employee);
        return mapResponses(orgId, List.of(saved)).getFirst();
    }

    @Transactional
    public HrEmployeeResponse updateEmployee(
            TenantPrincipal principal,
            UUID employeeId,
            UpdateEmployeeRequest request) {
        UUID orgId = principal.organizationId();
        HrEmployee employee = employeeRepository.findByIdAndOrganizationId(employeeId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));

        if (request.firstName() != null) {
            String trimmed = request.firstName().trim();
            if (trimmed.isEmpty()) {
                throw new BusinessRuleException("First name cannot be blank.");
            }
            employee.setFirstName(trimmed);
        }
        if (request.lastName() != null) {
            String trimmed = request.lastName().trim();
            if (trimmed.isEmpty()) {
                throw new BusinessRuleException("Last name cannot be blank.");
            }
            employee.setLastName(trimmed);
        }
        if (request.email() != null) {
            String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
            if (normalizedEmail.isEmpty()) {
                throw new BusinessRuleException("Email cannot be blank.");
            }
            if (employeeRepository.existsByOrganizationIdAndEmailIgnoreCaseAndIdNot(
                    orgId, normalizedEmail, employee.getId())) {
                throw new ConflictException(
                        "An employee with email '" + normalizedEmail + "' already exists in this organization.");
            }
            employee.setEmail(normalizedEmail);
        }
        if (request.phone() != null) {
            String trimmed = request.phone().trim();
            if (trimmed.isEmpty()) {
                throw new BusinessRuleException("Phone number cannot be blank.");
            }
            employee.setPhone(trimmed);
        }
        if (request.departmentId() != null) {
            HrDepartment dept = departmentRepository.findByIdAndOrganizationId(request.departmentId(), orgId)
                    .orElseThrow(() -> new ResourceNotFoundException("Department", request.departmentId()));
            employee.setDepartmentId(dept.getId());
        }
        if (request.designation() != null) {
            String trimmed = request.designation().trim();
            if (trimmed.isEmpty()) {
                throw new BusinessRuleException("Designation cannot be blank.");
            }
            employee.setDesignation(trimmed);
        }
        if (request.employmentType() != null) {
            employee.setEmploymentType(request.employmentType());
        }
        if (request.dateOfJoining() != null) {
            employee.setDateOfJoining(request.dateOfJoining());
        }
        if (request.employmentStatus() != null) {
            employee.setEmploymentStatus(request.employmentStatus());
        }
        if (Boolean.TRUE.equals(request.clearReportingManager())) {
            employee.setReportingManagerId(null);
        } else if (request.reportingManagerId() != null) {
            validateNoManagerCycle(orgId, employee.getId(), request.reportingManagerId());
            employee.setReportingManagerId(request.reportingManagerId());
        }
        if (request.address() != null) {
            employee.setAddress(HrScopeService.trimToNull(request.address()));
        }
        if (Boolean.TRUE.equals(request.clearUserId())) {
            employee.setUserId(null);
        } else if (request.userId() != null) {
            employee.setUserId(validateLinkedUser(orgId, request.userId(), employee.getId()));
        }

        HrEmployee saved = employeeRepository.save(employee);
        return mapResponses(orgId, List.of(saved)).getFirst();
    }

    @Transactional
    public HrEmployeeResponse deactivateEmployee(TenantPrincipal principal, UUID employeeId) {
        UUID orgId = principal.organizationId();
        HrEmployee employee = employeeRepository.findByIdAndOrganizationId(employeeId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
        employee.setEmploymentStatus(EmploymentStatus.INACTIVE);
        HrEmployee saved = employeeRepository.save(employee);
        return mapResponses(orgId, List.of(saved)).getFirst();
    }

    public List<HrEmployeeResponse> mapResponses(UUID orgId, List<HrEmployee> employees) {
        if (employees.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> departmentNames = departmentRepository.findAllByOrganizationIdOrderByNameAsc(orgId).stream()
                .collect(Collectors.toMap(HrDepartment::getId, HrDepartment::getName));
        Set<UUID> managerIds = employees.stream()
                .map(HrEmployee::getReportingManagerId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<UUID, String> managerNames = managerIds.isEmpty()
                ? Map.of()
                : employeeRepository.findAllByOrganizationIdAndIdIn(orgId, managerIds).stream()
                        .collect(Collectors.toMap(HrEmployee::getId, HrEmployee::fullName));

        return employees.stream()
                .map(emp -> new HrEmployeeResponse(
                        emp.getId(),
                        emp.getEmployeeCode(),
                        emp.getUserId(),
                        emp.getFirstName(),
                        emp.getLastName(),
                        emp.fullName(),
                        emp.getEmail(),
                        emp.getPhone(),
                        emp.getDepartmentId(),
                        departmentNames.getOrDefault(emp.getDepartmentId(), "Department"),
                        emp.getDesignation(),
                        emp.getEmploymentType(),
                        emp.getDateOfJoining(),
                        emp.getEmploymentStatus(),
                        emp.getReportingManagerId(),
                        emp.getReportingManagerId() != null ? managerNames.get(emp.getReportingManagerId()) : null,
                        emp.getAddress(),
                        emp.getCreatedAt(),
                        emp.getUpdatedAt()
                ))
                .toList();
    }

    private UUID validateLinkedUser(UUID orgId, UUID userId, UUID excludeEmployeeId) {
        if (userId == null) {
            return null;
        }
        if (!memberRepository.existsByOrganizationIdAndUserId(orgId, userId)) {
            throw new AccessDeniedException("The linked user account must belong to the active organization.");
        }
        boolean duplicate = excludeEmployeeId == null
                ? employeeRepository.existsByOrganizationIdAndUserId(orgId, userId)
                : employeeRepository.existsByOrganizationIdAndUserIdAndIdNot(orgId, userId, excludeEmployeeId);
        if (duplicate) {
            throw new ConflictException("This user account is already linked to another employee in the organization.");
        }
        return userId;
    }

    private void validateNoManagerCycle(UUID orgId, UUID employeeId, UUID candidateManagerId) {
        if (employeeId.equals(candidateManagerId)) {
            throw new BusinessRuleException("An employee cannot be their own reporting manager.");
        }
        HrEmployee current = employeeRepository.findByIdAndOrganizationId(candidateManagerId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Reporting manager", candidateManagerId));
        Set<UUID> visited = new HashSet<>();
        visited.add(employeeId);
        while (current != null) {
            if (!visited.add(current.getId())) {
                throw new BusinessRuleException("Reporting manager assignment would create a circular reporting chain.");
            }
            UUID nextManagerId = current.getReportingManagerId();
            if (nextManagerId == null) {
                break;
            }
            current = employeeRepository.findByIdAndOrganizationId(nextManagerId, orgId).orElse(null);
        }
    }

    private String generateEmployeeCode(UUID orgId) {
        HrEmployeeSequence sequence = sequenceRepository.findForUpdateByOrganizationId(orgId)
                .orElseGet(() -> sequenceRepository.save(new HrEmployeeSequence(orgId, 1L)));
        long number = sequence.getAndIncrement();
        sequenceRepository.save(sequence);
        return "EMP-%04d".formatted(number);
    }
}
