package com.nexusops.platform.hr;

import com.nexusops.platform.common.exception.AccessDeniedException;
import com.nexusops.platform.identity.OrganizationRole;
import com.nexusops.platform.identity.Permission;
import com.nexusops.platform.security.TenantPrincipal;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves organization-scoped HR visibility and review boundaries based on role and permissions.
 */
@Service
public class HrScopeService {

    private final HrEmployeeRepository employeeRepository;
    private final HrDepartmentRepository departmentRepository;

    public HrScopeService(
            HrEmployeeRepository employeeRepository,
            HrDepartmentRepository departmentRepository) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
    }

    public boolean hasUnrestrictedHrAccess(TenantPrincipal principal) {
        return principal.hasPermission(Permission.HR_WRITE)
                || principal.role() == OrganizationRole.OWNER
                || principal.role() == OrganizationRole.ADMIN;
    }

    public Optional<HrEmployee> resolveOwnEmployee(TenantPrincipal principal) {
        UUID orgId = principal.organizationId();
        Optional<HrEmployee> byUser = employeeRepository.findByOrganizationIdAndUserId(orgId, principal.userId());
        if (byUser.isPresent()) {
            return byUser;
        }
        Optional<HrEmployee> byEmail = employeeRepository.findByOrganizationIdAndEmailIgnoreCase(orgId, principal.email());
        if (byEmail.isPresent() && byEmail.get().getUserId() == null) {
            return byEmail;
        }
        return Optional.empty();
    }

    /**
     * Returns {@link Optional#empty()} when the principal has unrestricted org-wide HR read access,
     * or {@link Optional#of(Set)} containing the exact employee IDs visible to the principal.
     */
    public Optional<Set<UUID>> resolvePermittedEmployeeIds(TenantPrincipal principal) {
        if (hasUnrestrictedHrAccess(principal)) {
            return Optional.empty();
        }

        Optional<HrEmployee> ownOpt = resolveOwnEmployee(principal);
        if (ownOpt.isEmpty()) {
            return Optional.of(Set.of());
        }
        HrEmployee own = ownOpt.get();

        if (principal.role() == OrganizationRole.MANAGER && principal.hasPermission(Permission.HR_READ)) {
            Set<UUID> scopedIds = new LinkedHashSet<>();
            scopedIds.add(own.getId());
            scopedIds.addAll(collectManagedEmployeeIds(principal.organizationId(), own.getId()));
            return Optional.of(Set.copyOf(scopedIds));
        }

        return Optional.of(Set.of(own.getId()));
    }

    /**
     * Returns {@link Optional#empty()} when the principal can review leave for any employee in the org,
     * or {@link Optional#of(Set)} containing the employee IDs whose leave the principal may approve/reject.
     */
    public Optional<Set<UUID>> resolveReviewableEmployeeIds(TenantPrincipal principal) {
        if (!principal.hasPermission(Permission.HR_LEAVE_REVIEW)) {
            throw new AccessDeniedException("Missing required permission: HR_LEAVE_REVIEW");
        }
        if (hasUnrestrictedHrAccess(principal)) {
            return Optional.empty();
        }

        Optional<HrEmployee> ownOpt = resolveOwnEmployee(principal);
        if (ownOpt.isEmpty()) {
            return Optional.of(Set.of());
        }
        HrEmployee own = ownOpt.get();
        Set<UUID> managed = collectManagedEmployeeIds(principal.organizationId(), own.getId());
        // Managers cannot approve or reject their own leave requests.
        managed.remove(own.getId());
        return Optional.of(Set.copyOf(managed));
    }

    public void assertCanAccessEmployee(TenantPrincipal principal, UUID employeeId) {
        Optional<Set<UUID>> permitted = resolvePermittedEmployeeIds(principal);
        if (permitted.isPresent() && !permitted.get().contains(employeeId)) {
            throw new AccessDeniedException("You do not have access to this employee record.");
        }
    }

    public void assertCanReviewEmployeeLeave(TenantPrincipal principal, UUID employeeId) {
        Optional<Set<UUID>> reviewable = resolveReviewableEmployeeIds(principal);
        if (reviewable.isPresent() && !reviewable.get().contains(employeeId)) {
            throw new AccessDeniedException(
                    "You are not authorized to approve or reject leave requests for this employee.");
        }
    }

    private Set<UUID> collectManagedEmployeeIds(UUID orgId, UUID managerEmployeeId) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (HrEmployee directReport : employeeRepository
                .findAllByOrganizationIdAndReportingManagerId(orgId, managerEmployeeId)) {
            ids.add(directReport.getId());
        }
        List<UUID> headedDepartmentIds = departmentRepository
                .findAllByOrganizationIdAndHeadEmployeeId(orgId, managerEmployeeId)
                .stream()
                .map(HrDepartment::getId)
                .toList();
        if (!headedDepartmentIds.isEmpty()) {
            for (HrEmployee deptEmployee : employeeRepository
                    .findAllByOrganizationIdAndDepartmentIdIn(orgId, headedDepartmentIds)) {
                ids.add(deptEmployee.getId());
            }
        }
        return ids;
    }

    public static String resolveActorName(TenantPrincipal principal) {
        if (principal.displayName() != null && !principal.displayName().isBlank()) {
            return principal.displayName().trim();
        }
        return principal.email();
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

