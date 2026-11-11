package com.nexusops.platform.hr;

import com.nexusops.platform.common.api.ApiResponse;
import com.nexusops.platform.common.api.PagedResponse;
import com.nexusops.platform.hr.HrEmployeeService.MyEmployeeProfileResponse;
import com.nexusops.platform.hr.dto.HrDtos.CreateDepartmentRequest;
import com.nexusops.platform.hr.dto.HrDtos.CreateEmployeeRequest;
import com.nexusops.platform.hr.dto.HrDtos.CreateLeaveRequest;
import com.nexusops.platform.hr.dto.HrDtos.HrAttendanceResponse;
import com.nexusops.platform.hr.dto.HrDtos.HrDashboardResponse;
import com.nexusops.platform.hr.dto.HrDtos.HrDepartmentResponse;
import com.nexusops.platform.hr.dto.HrDtos.HrEmployeeResponse;
import com.nexusops.platform.hr.dto.HrDtos.HrLeaveRequestResponse;
import com.nexusops.platform.hr.dto.HrDtos.MarkAttendanceRequest;
import com.nexusops.platform.hr.dto.HrDtos.ReviewLeaveRequest;
import com.nexusops.platform.hr.dto.HrDtos.UpdateAttendanceRequest;
import com.nexusops.platform.hr.dto.HrDtos.UpdateDepartmentRequest;
import com.nexusops.platform.hr.dto.HrDtos.UpdateEmployeeRequest;
import com.nexusops.platform.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hr")
@Tag(name = "Human Resources", description = "HR dashboard, employees, departments, attendance, and leave management.")
public class HrController {

    private final HrDashboardService dashboardService;
    private final HrDepartmentService departmentService;
    private final HrEmployeeService employeeService;
    private final HrAttendanceService attendanceService;
    private final HrLeaveService leaveService;
    private final TenantContext tenantContext;

    public HrController(
            HrDashboardService dashboardService,
            HrDepartmentService departmentService,
            HrEmployeeService employeeService,
            HrAttendanceService attendanceService,
            HrLeaveService leaveService,
            TenantContext tenantContext) {
        this.dashboardService = dashboardService;
        this.departmentService = departmentService;
        this.employeeService = employeeService;
        this.attendanceService = attendanceService;
        this.leaveService = leaveService;
        this.tenantContext = tenantContext;
    }

    // ------------------------------------------------------------------ Dashboard & Self-Service Profile

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyAuthority('PERM_HR_READ', 'PERM_HR_SELF_SERVICE')")
    @Operation(summary = "Get HR dashboard metrics scoped to the caller's organization and role")
    public ResponseEntity<ApiResponse<HrDashboardResponse>> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.of(
                dashboardService.getDashboard(tenantContext.requirePrincipal(), date)));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('PERM_HR_SELF_SERVICE')")
    @Operation(summary = "Resolve the authenticated user's linked employee profile in the active organization")
    public ResponseEntity<ApiResponse<MyEmployeeProfileResponse>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.of(
                employeeService.getMyProfile(tenantContext.requirePrincipal())));
    }

    // ------------------------------------------------------------------ Departments

    @GetMapping("/departments")
    @PreAuthorize("hasAuthority('PERM_HR_READ')")
    @Operation(summary = "List departments with employee counts")
    public ResponseEntity<ApiResponse<List<HrDepartmentResponse>>> listDepartments() {
        return ResponseEntity.ok(ApiResponse.of(
                departmentService.listDepartments(tenantContext.requirePrincipal())));
    }

    @GetMapping("/departments/{departmentId}")
    @PreAuthorize("hasAuthority('PERM_HR_READ')")
    @Operation(summary = "Get a department by ID")
    public ResponseEntity<ApiResponse<HrDepartmentResponse>> getDepartment(@PathVariable UUID departmentId) {
        return ResponseEntity.ok(ApiResponse.of(
                departmentService.getDepartment(tenantContext.requirePrincipal(), departmentId)));
    }

    @PostMapping("/departments")
    @PreAuthorize("hasAuthority('PERM_HR_WRITE')")
    @Operation(summary = "Create a department")
    public ResponseEntity<ApiResponse<HrDepartmentResponse>> createDepartment(
            @Valid @RequestBody CreateDepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(
                        departmentService.createDepartment(tenantContext.requirePrincipal(), request)));
    }

    @PatchMapping("/departments/{departmentId}")
    @PreAuthorize("hasAuthority('PERM_HR_WRITE')")
    @Operation(summary = "Update a department")
    public ResponseEntity<ApiResponse<HrDepartmentResponse>> updateDepartment(
            @PathVariable UUID departmentId,
            @Valid @RequestBody UpdateDepartmentRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                departmentService.updateDepartment(tenantContext.requirePrincipal(), departmentId, request)));
    }

    @DeleteMapping("/departments/{departmentId}")
    @PreAuthorize("hasAuthority('PERM_HR_WRITE')")
    @Operation(summary = "Delete an empty department")
    public ResponseEntity<Void> deleteDepartment(@PathVariable UUID departmentId) {
        departmentService.deleteDepartment(tenantContext.requirePrincipal(), departmentId);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ Employees

    @GetMapping("/employees")
    @PreAuthorize("hasAnyAuthority('PERM_HR_READ', 'PERM_HR_SELF_SERVICE')")
    @Operation(summary = "List, search, filter, and paginate permitted employees")
    public ResponseEntity<PagedResponse<HrEmployeeResponse>> listEmployees(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String designation,
            @RequestParam(required = false) EmploymentStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResponseEntity.ok(employeeService.listEmployees(
                tenantContext.requirePrincipal(),
                search,
                departmentId,
                designation,
                status,
                page,
                pageSize));
    }

    @GetMapping("/employees/{employeeId}")
    @PreAuthorize("hasAnyAuthority('PERM_HR_READ', 'PERM_HR_SELF_SERVICE')")
    @Operation(summary = "Get employee profile details")
    public ResponseEntity<ApiResponse<HrEmployeeResponse>> getEmployee(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(ApiResponse.of(
                employeeService.getEmployee(tenantContext.requirePrincipal(), employeeId)));
    }

    @PostMapping("/employees")
    @PreAuthorize("hasAuthority('PERM_HR_WRITE')")
    @Operation(summary = "Create a new employee record")
    public ResponseEntity<ApiResponse<HrEmployeeResponse>> createEmployee(
            @Valid @RequestBody CreateEmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(
                        employeeService.createEmployee(tenantContext.requirePrincipal(), request)));
    }

    @PatchMapping("/employees/{employeeId}")
    @PreAuthorize("hasAuthority('PERM_HR_WRITE')")
    @Operation(summary = "Update an employee record")
    public ResponseEntity<ApiResponse<HrEmployeeResponse>> updateEmployee(
            @PathVariable UUID employeeId,
            @Valid @RequestBody UpdateEmployeeRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                employeeService.updateEmployee(tenantContext.requirePrincipal(), employeeId, request)));
    }

    @PostMapping("/employees/{employeeId}/deactivate")
    @PreAuthorize("hasAuthority('PERM_HR_WRITE')")
    @Operation(summary = "Deactivate an employee")
    public ResponseEntity<ApiResponse<HrEmployeeResponse>> deactivateEmployee(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(ApiResponse.of(
                employeeService.deactivateEmployee(tenantContext.requirePrincipal(), employeeId)));
    }

    // ------------------------------------------------------------------ Attendance

    @GetMapping("/attendance")
    @PreAuthorize("hasAnyAuthority('PERM_HR_READ', 'PERM_HR_SELF_SERVICE')")
    @Operation(summary = "List attendance records filtered by employee, date, date range, or status")
    public ResponseEntity<PagedResponse<HrAttendanceResponse>> listAttendance(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) AttendanceStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int pageSize) {
        return ResponseEntity.ok(attendanceService.listAttendance(
                tenantContext.requirePrincipal(),
                employeeId,
                date,
                fromDate,
                toDate,
                status,
                page,
                pageSize));
    }

    @PostMapping("/attendance")
    @PreAuthorize("hasAuthority('PERM_HR_WRITE')")
    @Operation(summary = "Mark attendance for an employee on a specific date")
    public ResponseEntity<ApiResponse<HrAttendanceResponse>> markAttendance(
            @Valid @RequestBody MarkAttendanceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(
                        attendanceService.markAttendance(tenantContext.requirePrincipal(), request)));
    }

    @PatchMapping("/attendance/{attendanceId}")
    @PreAuthorize("hasAuthority('PERM_HR_WRITE')")
    @Operation(summary = "Correct an existing attendance record")
    public ResponseEntity<ApiResponse<HrAttendanceResponse>> updateAttendance(
            @PathVariable UUID attendanceId,
            @Valid @RequestBody UpdateAttendanceRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                attendanceService.updateAttendance(tenantContext.requirePrincipal(), attendanceId, request)));
    }

    // ------------------------------------------------------------------ Leave Management

    @GetMapping("/leave-requests")
    @PreAuthorize("hasAnyAuthority('PERM_HR_READ', 'PERM_HR_SELF_SERVICE')")
    @Operation(summary = "List leave requests within authorized scope")
    public ResponseEntity<PagedResponse<HrLeaveRequestResponse>> listLeaveRequests(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) LeaveRequestStatus status,
            @RequestParam(required = false) LeaveType leaveType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int pageSize) {
        return ResponseEntity.ok(leaveService.listLeaveRequests(
                tenantContext.requirePrincipal(),
                employeeId,
                status,
                leaveType,
                page,
                pageSize));
    }

    @GetMapping("/leave-requests/{leaveRequestId}")
    @PreAuthorize("hasAnyAuthority('PERM_HR_READ', 'PERM_HR_SELF_SERVICE')")
    @Operation(summary = "Get leave request details and audit trail")
    public ResponseEntity<ApiResponse<HrLeaveRequestResponse>> getLeaveRequest(@PathVariable UUID leaveRequestId) {
        return ResponseEntity.ok(ApiResponse.of(
                leaveService.getLeaveRequest(tenantContext.requirePrincipal(), leaveRequestId)));
    }

    @PostMapping("/leave-requests")
    @PreAuthorize("hasAnyAuthority('PERM_HR_WRITE', 'PERM_HR_SELF_SERVICE')")
    @Operation(summary = "Submit a new leave request")
    public ResponseEntity<ApiResponse<HrLeaveRequestResponse>> submitLeaveRequest(
            @Valid @RequestBody CreateLeaveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(
                        leaveService.submitLeaveRequest(tenantContext.requirePrincipal(), request)));
    }

    @PostMapping("/leave-requests/{leaveRequestId}/approve")
    @PreAuthorize("hasAuthority('PERM_HR_LEAVE_REVIEW')")
    @Operation(summary = "Approve a pending leave request")
    public ResponseEntity<ApiResponse<HrLeaveRequestResponse>> approveLeaveRequest(
            @PathVariable UUID leaveRequestId,
            @Valid @RequestBody(required = false) ReviewLeaveRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                leaveService.approveLeaveRequest(tenantContext.requirePrincipal(), leaveRequestId, request)));
    }

    @PostMapping("/leave-requests/{leaveRequestId}/reject")
    @PreAuthorize("hasAuthority('PERM_HR_LEAVE_REVIEW')")
    @Operation(summary = "Reject a pending leave request")
    public ResponseEntity<ApiResponse<HrLeaveRequestResponse>> rejectLeaveRequest(
            @PathVariable UUID leaveRequestId,
            @Valid @RequestBody(required = false) ReviewLeaveRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                leaveService.rejectLeaveRequest(tenantContext.requirePrincipal(), leaveRequestId, request)));
    }

    @PostMapping("/leave-requests/{leaveRequestId}/cancel")
    @PreAuthorize("hasAnyAuthority('PERM_HR_WRITE', 'PERM_HR_SELF_SERVICE')")
    @Operation(summary = "Cancel a pending leave request")
    public ResponseEntity<ApiResponse<HrLeaveRequestResponse>> cancelLeaveRequest(
            @PathVariable UUID leaveRequestId,
            @Valid @RequestBody(required = false) ReviewLeaveRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                leaveService.cancelLeaveRequest(tenantContext.requirePrincipal(), leaveRequestId, request)));
    }
}

