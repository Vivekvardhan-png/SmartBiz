package com.nexusops.platform.hr;

import com.nexusops.platform.hr.dto.HrDtos.DepartmentDistributionItem;
import com.nexusops.platform.hr.dto.HrDtos.HrDashboardResponse;
import com.nexusops.platform.hr.dto.HrDtos.HrEmployeeResponse;
import com.nexusops.platform.hr.dto.HrDtos.HrLeaveRequestResponse;
import com.nexusops.platform.identity.Permission;
import com.nexusops.platform.security.TenantPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class HrDashboardService {

    private final HrEmployeeRepository employeeRepository;
    private final HrDepartmentRepository departmentRepository;
    private final HrAttendanceRepository attendanceRepository;
    private final HrLeaveRequestRepository leaveRequestRepository;
    private final HrEmployeeService employeeService;
    private final HrLeaveService leaveService;
    private final HrScopeService scopeService;

    public HrDashboardService(
            HrEmployeeRepository employeeRepository,
            HrDepartmentRepository departmentRepository,
            HrAttendanceRepository attendanceRepository,
            HrLeaveRequestRepository leaveRequestRepository,
            HrEmployeeService employeeService,
            HrLeaveService leaveService,
            HrScopeService scopeService) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeService = employeeService;
        this.leaveService = leaveService;
        this.scopeService = scopeService;
    }

    @Transactional(readOnly = true)
    public HrDashboardResponse getDashboard(TenantPrincipal principal, LocalDate requestedDate) {
        UUID orgId = principal.organizationId();
        LocalDate targetDate = requestedDate != null ? requestedDate : LocalDate.now(ZoneOffset.UTC);

        Optional<HrEmployee> ownOpt = scopeService.resolveOwnEmployee(principal);
        HrEmployeeResponse linkedEmployee = ownOpt
                .map(emp -> employeeService.mapResponses(orgId, List.of(emp)).getFirst())
                .orElse(null);

        boolean selfServiceOnly = !principal.hasPermission(Permission.HR_READ);
        if (selfServiceOnly) {
            if (ownOpt.isEmpty()) {
                return new HrDashboardResponse(
                        targetDate,
                        true,
                        null,
                        0, 0, 0,
                        0, 0, 0, 0,
                        0,
                        List.of(),
                        List.of(),
                        List.of());
            }
            Set<UUID> ownSet = Set.of(ownOpt.get().getId());
            List<HrAttendance> ownAttendance = attendanceRepository
                    .findAllByOrganizationIdAndAttendanceDateAndEmployeeIdIn(orgId, targetDate, ownSet);
            long presentToday = countStatus(ownAttendance, AttendanceStatus.PRESENT);
            long halfDayToday = countStatus(ownAttendance, AttendanceStatus.HALF_DAY);
            long absentToday = countStatus(ownAttendance, AttendanceStatus.ABSENT);
            long onLeaveToday = countOnLeave(orgId, targetDate, ownAttendance, ownSet);
            long pendingLeaves = leaveRequestRepository.countByOrganizationIdAndStatusAndEmployeeIdIn(
                    orgId, LeaveRequestStatus.PENDING, ownSet);
            List<HrLeaveRequestResponse> recentLeaves = leaveService.mapResponses(
                    orgId,
                    leaveRequestRepository.findTop5ByOrganizationIdAndEmployeeIdInOrderBySubmittedAtDesc(orgId, ownSet),
                    false);

            return new HrDashboardResponse(
                    targetDate,
                    true,
                    linkedEmployee,
                    1,
                    ownOpt.get().getEmploymentStatus() == EmploymentStatus.ACTIVE ? 1 : 0,
                    ownOpt.get().getEmploymentStatus() == EmploymentStatus.INACTIVE ? 1 : 0,
                    presentToday,
                    halfDayToday,
                    absentToday,
                    onLeaveToday,
                    pendingLeaves,
                    List.of(),
                    List.of(),
                    recentLeaves);
        }

        Optional<Set<UUID>> permittedIds = scopeService.resolvePermittedEmployeeIds(principal);
        if (permittedIds.isPresent()) {
            Set<UUID> scoped = permittedIds.get();
            if (scoped.isEmpty()) {
                return new HrDashboardResponse(
                        targetDate,
                        false,
                        linkedEmployee,
                        0, 0, 0,
                        0, 0, 0, 0,
                        0,
                        List.of(),
                        List.of(),
                        List.of());
            }
            List<HrEmployee> scopedEmployees = employeeRepository.findAllByOrganizationIdAndIdIn(orgId, scoped);
            long activeCount = scopedEmployees.stream()
                    .filter(e -> e.getEmploymentStatus() == EmploymentStatus.ACTIVE)
                    .count();
            long inactiveCount = scopedEmployees.size() - activeCount;

            List<HrAttendance> attendance = attendanceRepository
                    .findAllByOrganizationIdAndAttendanceDateAndEmployeeIdIn(orgId, targetDate, scoped);
            long presentToday = countStatus(attendance, AttendanceStatus.PRESENT);
            long halfDayToday = countStatus(attendance, AttendanceStatus.HALF_DAY);
            long absentToday = countStatus(attendance, AttendanceStatus.ABSENT);
            long onLeaveToday = countOnLeave(orgId, targetDate, attendance, scoped);
            long pendingLeaves = leaveRequestRepository.countByOrganizationIdAndStatusAndEmployeeIdIn(
                    orgId, LeaveRequestStatus.PENDING, scoped);

            List<DepartmentDistributionItem> distribution = buildDistribution(orgId, scopedEmployees);
            List<HrEmployeeResponse> recentEmployees = employeeService.mapResponses(
                    orgId,
                    employeeRepository.findTop5ByOrganizationIdAndIdInOrderByCreatedAtDesc(orgId, scoped));
            List<HrLeaveRequestResponse> recentLeaves = leaveService.mapResponses(
                    orgId,
                    leaveRequestRepository.findTop5ByOrganizationIdAndEmployeeIdInOrderBySubmittedAtDesc(orgId, scoped),
                    false);

            return new HrDashboardResponse(
                    targetDate,
                    false,
                    linkedEmployee,
                    scopedEmployees.size(),
                    activeCount,
                    inactiveCount,
                    presentToday,
                    halfDayToday,
                    absentToday,
                    onLeaveToday,
                    pendingLeaves,
                    distribution,
                    recentEmployees,
                    recentLeaves);
        }

        long totalEmployees = employeeRepository.countByOrganizationId(orgId);
        long activeEmployees = employeeRepository.countByOrganizationIdAndEmploymentStatus(orgId, EmploymentStatus.ACTIVE);
        long inactiveEmployees = employeeRepository.countByOrganizationIdAndEmploymentStatus(orgId, EmploymentStatus.INACTIVE);

        List<HrAttendance> todayAttendance = attendanceRepository.findAllByOrganizationIdAndAttendanceDate(orgId, targetDate);
        long presentToday = countStatus(todayAttendance, AttendanceStatus.PRESENT);
        long halfDayToday = countStatus(todayAttendance, AttendanceStatus.HALF_DAY);
        long absentToday = countStatus(todayAttendance, AttendanceStatus.ABSENT);
        long onLeaveToday = countOnLeave(orgId, targetDate, todayAttendance, null);
        long pendingLeaveRequests = leaveRequestRepository.countByOrganizationIdAndStatus(orgId, LeaveRequestStatus.PENDING);

        List<HrEmployee> allEmployees = employeeRepository.findAllByOrganizationIdOrderByFirstNameAscLastNameAsc(orgId);
        List<DepartmentDistributionItem> distribution = buildDistribution(orgId, allEmployees);
        List<HrEmployeeResponse> recentEmployees = employeeService.mapResponses(
                orgId,
                employeeRepository.findTop5ByOrganizationIdOrderByCreatedAtDesc(orgId));
        List<HrLeaveRequestResponse> recentLeaves = leaveService.mapResponses(
                orgId,
                leaveRequestRepository.findTop5ByOrganizationIdOrderBySubmittedAtDesc(orgId),
                false);

        return new HrDashboardResponse(
                targetDate,
                false,
                linkedEmployee,
                totalEmployees,
                activeEmployees,
                inactiveEmployees,
                presentToday,
                halfDayToday,
                absentToday,
                onLeaveToday,
                pendingLeaveRequests,
                distribution,
                recentEmployees,
                recentLeaves);
    }

    private long countStatus(List<HrAttendance> records, AttendanceStatus status) {
        return records.stream().filter(r -> r.getStatus() == status).count();
    }

    private long countOnLeave(
            UUID orgId,
            LocalDate date,
            List<HrAttendance> attendance,
            Set<UUID> scopedEmployeeIds) {
        Set<UUID> employeeIdsOnLeave = new HashSet<>();
        for (HrAttendance rec : attendance) {
            if (rec.getStatus() == AttendanceStatus.ON_LEAVE) {
                employeeIdsOnLeave.add(rec.getEmployeeId());
            }
        }
        List<HrLeaveRequest> approvedLeaves = leaveRequestRepository
                .findAllByOrganizationIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        orgId, LeaveRequestStatus.APPROVED, date, date);
        for (HrLeaveRequest leave : approvedLeaves) {
            if (scopedEmployeeIds == null || scopedEmployeeIds.contains(leave.getEmployeeId())) {
                employeeIdsOnLeave.add(leave.getEmployeeId());
            }
        }
        return employeeIdsOnLeave.size();
    }

    private List<DepartmentDistributionItem> buildDistribution(UUID orgId, List<HrEmployee> employees) {
        Map<UUID, Long> counts = employees.stream()
                .collect(Collectors.groupingBy(HrEmployee::getDepartmentId, Collectors.counting()));
        return departmentRepository.findAllByOrganizationIdOrderByNameAsc(orgId).stream()
                .map(dept -> new DepartmentDistributionItem(
                        dept.getId(),
                        dept.getName(),
                        counts.getOrDefault(dept.getId(), 0L)))
                .toList();
    }
}

