package com.nexusops.platform.hr;

import com.nexusops.platform.common.api.PagedResponse;
import com.nexusops.platform.common.exception.AccessDeniedException;
import com.nexusops.platform.common.exception.BusinessRuleException;
import com.nexusops.platform.common.exception.ConflictException;
import com.nexusops.platform.common.exception.ResourceNotFoundException;
import com.nexusops.platform.hr.dto.HrDtos.CreateLeaveRequest;
import com.nexusops.platform.hr.dto.HrDtos.HrLeaveAuditLogResponse;
import com.nexusops.platform.hr.dto.HrDtos.HrLeaveRequestResponse;
import com.nexusops.platform.hr.dto.HrDtos.ReviewLeaveRequest;
import com.nexusops.platform.identity.Permission;
import com.nexusops.platform.security.TenantPrincipal;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class HrLeaveService {

    private static final int MAX_PAGE_SIZE = 100;

    private final HrLeaveRequestRepository leaveRequestRepository;
    private final HrLeaveAuditLogRepository auditLogRepository;
    private final HrEmployeeRepository employeeRepository;
    private final HrDepartmentRepository departmentRepository;
    private final HrScopeService scopeService;

    public HrLeaveService(
            HrLeaveRequestRepository leaveRequestRepository,
            HrLeaveAuditLogRepository auditLogRepository,
            HrEmployeeRepository employeeRepository,
            HrDepartmentRepository departmentRepository,
            HrScopeService scopeService) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.auditLogRepository = auditLogRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.scopeService = scopeService;
    }

    @Transactional(readOnly = true)
    public PagedResponse<HrLeaveRequestResponse> listLeaveRequests(
            TenantPrincipal principal,
            UUID employeeId,
            LeaveRequestStatus status,
            LeaveType leaveType,
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

        Specification<HrLeaveRequest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), orgId));
            if (permittedIds.isPresent()) {
                predicates.add(root.get("employeeId").in(permittedIds.get()));
            }
            if (employeeId != null) {
                predicates.add(cb.equal(root.get("employeeId"), employeeId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (leaveType != null) {
                predicates.add(cb.equal(root.get("leaveType"), leaveType));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<HrLeaveRequest> result = leaveRequestRepository.findAll(
                spec,
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "submittedAt"))
        );

        return PagedResponse.of(
                mapResponses(orgId, result.getContent(), false),
                safePage,
                safeSize,
                result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public HrLeaveRequestResponse getLeaveRequest(TenantPrincipal principal, UUID leaveRequestId) {
        UUID orgId = principal.organizationId();
        HrLeaveRequest leave = leaveRequestRepository.findByIdAndOrganizationId(leaveRequestId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", leaveRequestId));
        scopeService.assertCanAccessEmployee(principal, leave.getEmployeeId());
        return mapResponses(orgId, List.of(leave), true).getFirst();
    }

    @Transactional
    public HrLeaveRequestResponse submitLeaveRequest(TenantPrincipal principal, CreateLeaveRequest request) {
        UUID orgId = principal.organizationId();
        HrEmployee employee = employeeRepository.findByIdAndOrganizationId(request.employeeId(), orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", request.employeeId()));

        if (!principal.hasPermission(Permission.HR_WRITE)) {
            HrEmployee own = scopeService.resolveOwnEmployee(principal)
                    .orElseThrow(() -> new AccessDeniedException(
                            "No linked employee profile was found for your user account."));
            if (!own.getId().equals(employee.getId())) {
                throw new AccessDeniedException("You may only submit leave requests for your own employee profile.");
            }
        }

        if (employee.getEmploymentStatus() != EmploymentStatus.ACTIVE) {
            throw new BusinessRuleException("Inactive employees cannot submit leave requests.");
        }

        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessRuleException("Leave end date cannot precede start date.");
        }

        if (leaveRequestRepository.existsOverlappingActiveLeave(
                orgId, employee.getId(), request.startDate(), request.endDate())) {
            throw new ConflictException(
                    "An overlapping pending or approved leave request already exists for this employee.");
        }

        int totalDays = (int) ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
        HrLeaveRequest leave = new HrLeaveRequest(
                orgId,
                employee.getId(),
                request.leaveType(),
                request.startDate(),
                request.endDate(),
                totalDays,
                request.reason().trim(),
                principal.userId());

        HrLeaveRequest saved = leaveRequestRepository.save(leave);
        recordAudit(
                orgId,
                saved.getId(),
                null,
                LeaveRequestStatus.PENDING,
                saved.getReason(),
                principal);
        return mapResponses(orgId, List.of(saved), true).getFirst();
    }

    @Transactional
    public HrLeaveRequestResponse approveLeaveRequest(
            TenantPrincipal principal,
            UUID leaveRequestId,
            ReviewLeaveRequest request) {
        return reviewLeaveRequest(principal, leaveRequestId, LeaveRequestStatus.APPROVED, request);
    }

    @Transactional
    public HrLeaveRequestResponse rejectLeaveRequest(
            TenantPrincipal principal,
            UUID leaveRequestId,
            ReviewLeaveRequest request) {
        return reviewLeaveRequest(principal, leaveRequestId, LeaveRequestStatus.REJECTED, request);
    }

    @Transactional
    public HrLeaveRequestResponse cancelLeaveRequest(
            TenantPrincipal principal,
            UUID leaveRequestId,
            ReviewLeaveRequest request) {
        UUID orgId = principal.organizationId();
        HrLeaveRequest leave = leaveRequestRepository.findByIdAndOrganizationId(leaveRequestId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", leaveRequestId));

        if (!principal.hasPermission(Permission.HR_WRITE)) {
            HrEmployee own = scopeService.resolveOwnEmployee(principal)
                    .orElseThrow(() -> new AccessDeniedException(
                            "No linked employee profile was found for your user account."));
            if (!own.getId().equals(leave.getEmployeeId())) {
                throw new AccessDeniedException("You may only cancel your own pending leave requests.");
            }
        }

        if (leave.getStatus() != LeaveRequestStatus.PENDING) {
            throw new BusinessRuleException(
                    "Only PENDING leave requests can be cancelled (current status: " + leave.getStatus() + ").");
        }

        String comment = request != null ? HrScopeService.trimToNull(request.comments()) : null;
        leave.setStatus(LeaveRequestStatus.CANCELLED);
        leave.setReviewerUserId(principal.userId());
        leave.setReviewerName(HrScopeService.resolveActorName(principal));
        leave.setReviewComments(comment);
        leave.setReviewedAt(Instant.now());

        HrLeaveRequest saved = leaveRequestRepository.save(leave);
        recordAudit(
                orgId,
                saved.getId(),
                LeaveRequestStatus.PENDING,
                LeaveRequestStatus.CANCELLED,
                comment != null ? comment : "Leave request cancelled.",
                principal);
        return mapResponses(orgId, List.of(saved), true).getFirst();
    }

    private HrLeaveRequestResponse reviewLeaveRequest(
            TenantPrincipal principal,
            UUID leaveRequestId,
            LeaveRequestStatus targetStatus,
            ReviewLeaveRequest request) {
        UUID orgId = principal.organizationId();
        HrLeaveRequest leave = leaveRequestRepository.findByIdAndOrganizationId(leaveRequestId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", leaveRequestId));

        scopeService.assertCanReviewEmployeeLeave(principal, leave.getEmployeeId());

        if (leave.getStatus() != LeaveRequestStatus.PENDING) {
            throw new BusinessRuleException(
                    "Only PENDING leave requests can be reviewed (current status: " + leave.getStatus() + ").");
        }

        String comments = request != null ? HrScopeService.trimToNull(request.comments()) : null;
        if (targetStatus == LeaveRequestStatus.REJECTED && comments == null) {
            throw new BusinessRuleException("Please provide a review comment when rejecting a leave request.");
        }

        leave.setStatus(targetStatus);
        leave.setReviewerUserId(principal.userId());
        leave.setReviewerName(HrScopeService.resolveActorName(principal));
        leave.setReviewComments(comments);
        leave.setReviewedAt(Instant.now());

        HrLeaveRequest saved = leaveRequestRepository.save(leave);
        recordAudit(
                orgId,
                saved.getId(),
                LeaveRequestStatus.PENDING,
                targetStatus,
                comments,
                principal);
        return mapResponses(orgId, List.of(saved), true).getFirst();
    }

    private void recordAudit(
            UUID orgId,
            UUID leaveRequestId,
            LeaveRequestStatus fromStatus,
            LeaveRequestStatus toStatus,
            String comment,
            TenantPrincipal principal) {
        auditLogRepository.save(new HrLeaveAuditLog(
                orgId,
                leaveRequestId,
                fromStatus,
                toStatus,
                comment,
                principal.userId(),
                HrScopeService.resolveActorName(principal)));
    }

    public List<HrLeaveRequestResponse> mapResponses(
            UUID orgId,
            List<HrLeaveRequest> requests,
            boolean includeAuditTrail) {
        if (requests.isEmpty()) {
            return List.of();
        }
        Set<UUID> employeeIds = requests.stream().map(HrLeaveRequest::getEmployeeId).collect(Collectors.toSet());
        Map<UUID, HrEmployee> employeesById = employeeRepository
                .findAllByOrganizationIdAndIdIn(orgId, employeeIds).stream()
                .collect(Collectors.toMap(HrEmployee::getId, e -> e));
        Map<UUID, String> departmentNames = departmentRepository
                .findAllByOrganizationIdOrderByNameAsc(orgId).stream()
                .collect(Collectors.toMap(HrDepartment::getId, HrDepartment::getName));

        return requests.stream().map(req -> {
            HrEmployee emp = employeesById.get(req.getEmployeeId());
            String deptName = emp != null
                    ? departmentNames.getOrDefault(emp.getDepartmentId(), "Department")
                    : "Department";
            List<HrLeaveAuditLogResponse> audit = includeAuditTrail
                    ? auditLogRepository.findAllByOrganizationIdAndLeaveRequestIdOrderByCreatedAtAsc(orgId, req.getId())
                            .stream()
                            .map(log -> new HrLeaveAuditLogResponse(
                                    log.getId(),
                                    log.getFromStatus(),
                                    log.getToStatus(),
                                    log.getComment(),
                                    log.getActorName(),
                                    log.getCreatedAt()))
                            .toList()
                    : List.of();
            return new HrLeaveRequestResponse(
                    req.getId(),
                    req.getEmployeeId(),
                    emp != null ? emp.getEmployeeCode() : "—",
                    emp != null ? emp.fullName() : "Employee",
                    deptName,
                    req.getLeaveType(),
                    req.getStartDate(),
                    req.getEndDate(),
                    req.getTotalDays(),
                    req.getReason(),
                    req.getStatus(),
                    req.getSubmittedAt(),
                    req.getReviewerUserId(),
                    req.getReviewerName(),
                    req.getReviewComments(),
                    req.getReviewedAt(),
                    audit
            );
        }).toList();
    }
}

