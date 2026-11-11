package com.nexusops.platform.hr.dto;

import com.nexusops.platform.hr.AttendanceStatus;
import com.nexusops.platform.hr.EmploymentStatus;
import com.nexusops.platform.hr.EmploymentType;
import com.nexusops.platform.hr.LeaveRequestStatus;
import com.nexusops.platform.hr.LeaveType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class HrDtos {

    private HrDtos() {
    }

    // ------------------------------------------------------------------ Departments

    public record CreateDepartmentRequest(
            @NotBlank(message = "Department name is required.")
            @Size(max = 150, message = "Department name must not exceed 150 characters.")
            String name,
            @Size(max = 2000, message = "Description must not exceed 2000 characters.")
            String description,
            UUID headEmployeeId
    ) {
    }

    public record UpdateDepartmentRequest(
            @Size(max = 150, message = "Department name must not exceed 150 characters.")
            String name,
            @Size(max = 2000, message = "Description must not exceed 2000 characters.")
            String description,
            UUID headEmployeeId,
            Boolean clearHeadEmployee
    ) {
    }

    public record HrDepartmentResponse(
            UUID id,
            String name,
            String description,
            UUID headEmployeeId,
            String headEmployeeName,
            long employeeCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    // ------------------------------------------------------------------ Employees

    public record CreateEmployeeRequest(
            @NotBlank(message = "First name is required.")
            @Size(max = 120, message = "First name must not exceed 120 characters.")
            String firstName,
            @NotBlank(message = "Last name is required.")
            @Size(max = 120, message = "Last name must not exceed 120 characters.")
            String lastName,
            @NotBlank(message = "Email is required.")
            @Email(message = "Please enter a valid email address.")
            @Size(max = 320, message = "Email must not exceed 320 characters.")
            String email,
            @NotBlank(message = "Phone number is required.")
            @Size(max = 50, message = "Phone number must not exceed 50 characters.")
            String phone,
            @NotNull(message = "Department is required.")
            UUID departmentId,
            @NotBlank(message = "Designation is required.")
            @Size(max = 150, message = "Designation must not exceed 150 characters.")
            String designation,
            @NotNull(message = "Employment type is required.")
            EmploymentType employmentType,
            @NotNull(message = "Date of joining is required.")
            LocalDate dateOfJoining,
            UUID reportingManagerId,
            @Size(max = 1000, message = "Address must not exceed 1000 characters.")
            String address,
            UUID userId
    ) {
    }

    public record UpdateEmployeeRequest(
            @Size(max = 120, message = "First name must not exceed 120 characters.")
            String firstName,
            @Size(max = 120, message = "Last name must not exceed 120 characters.")
            String lastName,
            @Email(message = "Please enter a valid email address.")
            @Size(max = 320, message = "Email must not exceed 320 characters.")
            String email,
            @Size(max = 50, message = "Phone number must not exceed 50 characters.")
            String phone,
            UUID departmentId,
            @Size(max = 150, message = "Designation must not exceed 150 characters.")
            String designation,
            EmploymentType employmentType,
            LocalDate dateOfJoining,
            EmploymentStatus employmentStatus,
            UUID reportingManagerId,
            Boolean clearReportingManager,
            @Size(max = 1000, message = "Address must not exceed 1000 characters.")
            String address,
            UUID userId,
            Boolean clearUserId
    ) {
    }

    public record HrEmployeeResponse(
            UUID id,
            String employeeCode,
            UUID userId,
            String firstName,
            String lastName,
            String fullName,
            String email,
            String phone,
            UUID departmentId,
            String departmentName,
            String designation,
            EmploymentType employmentType,
            LocalDate dateOfJoining,
            EmploymentStatus employmentStatus,
            UUID reportingManagerId,
            String reportingManagerName,
            String address,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    // ------------------------------------------------------------------ Attendance

    public record MarkAttendanceRequest(
            @NotNull(message = "Employee is required.")
            UUID employeeId,
            @NotNull(message = "Attendance date is required.")
            LocalDate attendanceDate,
            @NotNull(message = "Attendance status is required.")
            AttendanceStatus status,
            @Size(max = 500, message = "Notes must not exceed 500 characters.")
            String notes
    ) {
    }

    public record UpdateAttendanceRequest(
            @NotNull(message = "Attendance status is required.")
            AttendanceStatus status,
            @Size(max = 500, message = "Notes must not exceed 500 characters.")
            String notes
    ) {
    }

    public record HrAttendanceResponse(
            UUID id,
            UUID employeeId,
            String employeeCode,
            String employeeName,
            String departmentName,
            LocalDate attendanceDate,
            AttendanceStatus status,
            String notes,
            String recordedByName,
            String updatedByName,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    // ------------------------------------------------------------------ Leave

    public record CreateLeaveRequest(
            @NotNull(message = "Employee is required.")
            UUID employeeId,
            @NotNull(message = "Leave type is required.")
            LeaveType leaveType,
            @NotNull(message = "Start date is required.")
            LocalDate startDate,
            @NotNull(message = "End date is required.")
            LocalDate endDate,
            @NotBlank(message = "Reason is required.")
            @Size(max = 1000, message = "Reason must not exceed 1000 characters.")
            String reason
    ) {
    }

    public record ReviewLeaveRequest(
            @Size(max = 1000, message = "Review comments must not exceed 1000 characters.")
            String comments
    ) {
    }

    public record HrLeaveAuditLogResponse(
            UUID id,
            LeaveRequestStatus fromStatus,
            LeaveRequestStatus toStatus,
            String comment,
            String actorName,
            Instant createdAt
    ) {
    }

    public record HrLeaveRequestResponse(
            UUID id,
            UUID employeeId,
            String employeeCode,
            String employeeName,
            String departmentName,
            LeaveType leaveType,
            LocalDate startDate,
            LocalDate endDate,
            int totalDays,
            String reason,
            LeaveRequestStatus status,
            Instant submittedAt,
            UUID reviewerUserId,
            String reviewerName,
            String reviewComments,
            Instant reviewedAt,
            List<HrLeaveAuditLogResponse> auditTrail
    ) {
    }

    // ------------------------------------------------------------------ Dashboard

    public record DepartmentDistributionItem(
            UUID departmentId,
            String departmentName,
            long employeeCount
    ) {
    }

    public record HrDashboardResponse(
            LocalDate date,
            boolean selfServiceOnly,
            HrEmployeeResponse linkedEmployee,
            long totalEmployees,
            long activeEmployees,
            long inactiveEmployees,
            long presentToday,
            long halfDayToday,
            long absentToday,
            long onLeaveToday,
            long pendingLeaveRequests,
            List<DepartmentDistributionItem> departmentDistribution,
            List<HrEmployeeResponse> recentEmployees,
            List<HrLeaveRequestResponse> recentLeaveRequests
    ) {
    }
}

