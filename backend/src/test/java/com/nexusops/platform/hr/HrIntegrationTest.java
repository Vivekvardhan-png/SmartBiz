package com.nexusops.platform.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.platform.identity.AuthSessionRepository;
import com.nexusops.platform.identity.OrganizationMemberRepository;
import com.nexusops.platform.identity.OrganizationRepository;
import com.nexusops.platform.identity.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("HR Management Module Integration")
class HrIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HrLeaveAuditLogRepository leaveAuditLogRepository;

    @Autowired
    private HrLeaveRequestRepository leaveRequestRepository;

    @Autowired
    private HrAttendanceRepository attendanceRepository;

    @Autowired
    private HrDepartmentRepository departmentRepository;

    @Autowired
    private HrEmployeeRepository employeeRepository;

    @Autowired
    private HrEmployeeSequenceRepository sequenceRepository;

    @Autowired
    private AuthSessionRepository sessionRepository;

    @Autowired
    private OrganizationMemberRepository memberRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        leaveAuditLogRepository.deleteAll();
        leaveRequestRepository.deleteAll();
        attendanceRepository.deleteAll();
        for (HrDepartment dept : departmentRepository.findAll()) {
            dept.setHeadEmployeeId(null);
            departmentRepository.save(dept);
        }
        employeeRepository.deleteAll();
        departmentRepository.deleteAll();
        sequenceRepository.deleteAll();
        sessionRepository.deleteAll();
        memberRepository.deleteAll();
        organizationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Unauthenticated requests to HR endpoints return 401")
    void unauthenticatedRequestsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/hr/dashboard")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/hr/employees")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/hr/departments")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/hr/attendance")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/hr/leave-requests")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Department CRUD, employee counts, and non-empty delete protection")
    void departmentCrudAndDeleteProtection() throws Exception {
        String ownerToken = registerAndGetToken("hr-owner@acme.test", "Acme HR");

        MvcResult engRes = mockMvc.perform(post("/api/v1/hr/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Engineering",
                                  "description": "Platform and product engineering"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Engineering"))
                .andExpect(jsonPath("$.data.employeeCount").value(0))
                .andReturn();
        String engId = extractId(engRes);

        MvcResult opsRes = mockMvc.perform(post("/api/v1/hr/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Operations",
                                  "description": "Logistics"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String opsId = extractId(opsRes);

        // Duplicate department name rejected (case-insensitive)
        mockMvc.perform(post("/api/v1/hr/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "engineering" }
                                """))
                .andExpect(status().isConflict());

        // Create employee in Engineering
        MvcResult empRes = mockMvc.perform(post("/api/v1/hr/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Maya",
                                  "lastName": "Lin",
                                  "email": "maya.lin@acme.test",
                                  "phone": "+1-555-0101",
                                  "departmentId": "%s",
                                  "designation": "Staff Engineer",
                                  "employmentType": "FULL_TIME",
                                  "dateOfJoining": "2026-01-15"
                                }
                                """.formatted(engId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.employeeCode").value("EMP-0001"))
                .andReturn();
        String empId = extractId(empRes);

        // Cannot delete Engineering while Maya is assigned
        mockMvc.perform(delete("/api/v1/hr/departments/" + engId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isUnprocessableEntity());

        // Update department and assign head
        mockMvc.perform(patch("/api/v1/hr/departments/" + engId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Product Engineering",
                                  "headEmployeeId": "%s"
                                }
                                """.formatted(empId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Product Engineering"))
                .andExpect(jsonPath("$.data.headEmployeeName").value("Maya Lin"))
                .andExpect(jsonPath("$.data.employeeCount").value(1));

        // Reassign Maya to Operations, then Engineering can be deleted
        mockMvc.perform(patch("/api/v1/hr/employees/" + empId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "departmentId": "%s", "clearReportingManager": true }
                                """.formatted(opsId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.departmentName").value("Operations"));

        mockMvc.perform(delete("/api/v1/hr/departments/" + engId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Employee CRUD, filtering, deactivation, duplicate checks, and no auto-login user creation")
    void employeeCrudAndValidation() throws Exception {
        String ownerToken = registerAndGetToken("owner@people.test", "People Org");
        long initialUsers = userRepository.count();

        String deptId = createDepartment(ownerToken, "Finance");

        // Validation error on missing required fields
        mockMvc.perform(post("/api/v1/hr/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "", "email": "not-an-email" }
                                """))
                .andExpect(status().isBadRequest());

        MvcResult emp1Res = mockMvc.perform(post("/api/v1/hr/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Noah",
                                  "lastName": "Patel",
                                  "email": "noah@people.test",
                                  "phone": "+1-555-0200",
                                  "departmentId": "%s",
                                  "designation": "Controller",
                                  "employmentType": "FULL_TIME",
                                  "dateOfJoining": "2026-02-01",
                                  "address": "100 Market St"
                                }
                                """.formatted(deptId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.employeeCode").value("EMP-0001"))
                .andReturn();
        String emp1Id = extractId(emp1Res);

        // Creating an employee must NOT automatically create a user login account
        assertThat(userRepository.count()).isEqualTo(initialUsers);

        // Duplicate email rejected
        mockMvc.perform(post("/api/v1/hr/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Noah",
                                  "lastName": "Duplicate",
                                  "email": "NOAH@people.test",
                                  "phone": "+1-555-0201",
                                  "departmentId": "%s",
                                  "designation": "Analyst",
                                  "employmentType": "CONTRACT",
                                  "dateOfJoining": "2026-02-02"
                                }
                                """.formatted(deptId)))
                .andExpect(status().isConflict());

        // Self reporting manager rejected
        mockMvc.perform(patch("/api/v1/hr/employees/" + emp1Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reportingManagerId": "%s" }
                                """.formatted(emp1Id)))
                .andExpect(status().isUnprocessableEntity());

        // Deactivate employee
        mockMvc.perform(post("/api/v1/hr/employees/" + emp1Id + "/deactivate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employmentStatus").value("INACTIVE"));

        // Filter by status INACTIVE
        mockMvc.perform(get("/api/v1/hr/employees?status=INACTIVE&search=Noah")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.data[0].employeeCode").value("EMP-0001"));
    }

    @Test
    @DisplayName("Attendance marking, duplicate prevention, correction, leave workflows, and dashboard metrics")
    void attendanceLeaveAndDashboardWorkflows() throws Exception {
        String ownerToken = registerAndGetToken("admin@workflow.test", "Workflow Org");
        String deptId = createDepartment(ownerToken, "Engineering");
        String empId = createEmployee(ownerToken, deptId, "Elena", "Gomez", "elena@workflow.test", null, null);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        // Mark attendance
        MvcResult attRes = mockMvc.perform(post("/api/v1/hr/attendance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeId": "%s",
                                  "attendanceDate": "%s",
                                  "status": "PRESENT",
                                  "notes": "Checked in on time"
                                }
                                """.formatted(empId, today)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PRESENT"))
                .andReturn();
        String attendanceId = extractId(attRes);

        // Duplicate attendance for same employee and date rejected
        mockMvc.perform(post("/api/v1/hr/attendance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeId": "%s",
                                  "attendanceDate": "%s",
                                  "status": "HALF_DAY"
                                }
                                """.formatted(empId, today)))
                .andExpect(status().isConflict());

        // Correct attendance
        mockMvc.perform(patch("/api/v1/hr/attendance/" + attendanceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "HALF_DAY",
                                  "notes": "Left early for appointment"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HALF_DAY"));

        // Invalid leave dates (end before start)
        mockMvc.perform(post("/api/v1/hr/leave-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeId": "%s",
                                  "leaveType": "CASUAL_LEAVE",
                                  "startDate": "%s",
                                  "endDate": "%s",
                                  "reason": "Family trip"
                                }
                                """.formatted(empId, today.plusDays(5), today.plusDays(3))))
                .andExpect(status().isUnprocessableEntity());

        // Valid leave request covering today
        MvcResult leaveRes = mockMvc.perform(post("/api/v1/hr/leave-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeId": "%s",
                                  "leaveType": "SICK_LEAVE",
                                  "startDate": "%s",
                                  "endDate": "%s",
                                  "reason": "Medical recovery"
                                }
                                """.formatted(empId, today, today.plusDays(1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalDays").value(2))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        String leaveId = extractId(leaveRes);

        // Overlapping leave rejected
        mockMvc.perform(post("/api/v1/hr/leave-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeId": "%s",
                                  "leaveType": "EARNED_LEAVE",
                                  "startDate": "%s",
                                  "endDate": "%s",
                                  "reason": "Overlap check"
                                }
                                """.formatted(empId, today.plusDays(1), today.plusDays(2))))
                .andExpect(status().isConflict());

        // Dashboard before approval shows 1 pending leave
        mockMvc.perform(get("/api/v1/hr/dashboard?date=" + today)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEmployees").value(1))
                .andExpect(jsonPath("$.data.activeEmployees").value(1))
                .andExpect(jsonPath("$.data.halfDayToday").value(1))
                .andExpect(jsonPath("$.data.pendingLeaveRequests").value(1));

        // Approve leave
        mockMvc.perform(post("/api/v1/hr/leave-requests/" + leaveId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "comments": "Get well soon" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.auditTrail.length()").value(2));

        // Re-approving an already approved request is rejected
        mockMvc.perform(post("/api/v1/hr/leave-requests/" + leaveId + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "comments": "Too late" }
                                """))
                .andExpect(status().isUnprocessableEntity());

        // Dashboard after approval shows 1 on leave today, 0 pending
        mockMvc.perform(get("/api/v1/hr/dashboard?date=" + today)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onLeaveToday").value(1))
                .andExpect(jsonPath("$.data.pendingLeaveRequests").value(0));
    }

    @Test
    @DisplayName("Role-based authorization and cross-tenant isolation enforced on every HR endpoint")
    void roleAuthorizationAndTenantIsolation() throws Exception {
        String orgAOwnerToken = registerAndGetToken("owner-a@org-a.test", "Org A");
        String orgBOwnerToken = registerAndGetToken("owner-b@org-b.test", "Org B");

        // Create MANAGER and EMPLOYEE users in Org A
        String managerUserId = createMember(orgAOwnerToken, "manager@org-a.test", "Mgr OrgA", "MANAGER");
        String empUserId = createMember(orgAOwnerToken, "emp@org-a.test", "Emp OrgA", "EMPLOYEE");
        String managerToken = loginAndGetToken("manager@org-a.test", "StrongPass!123");
        String employeeToken = loginAndGetToken("emp@org-a.test", "StrongPass!123");

        String engDeptId = createDepartment(orgAOwnerToken, "Engineering");
        String salesDeptId = createDepartment(orgAOwnerToken, "Sales");

        // Manager employee profile
        String mgrEmpId = createEmployee(
                orgAOwnerToken, engDeptId, "Marcus", "Vance", "manager@org-a.test", null, managerUserId);
        // Direct report under manager
        String directReportId = createEmployee(
                orgAOwnerToken, engDeptId, "Alice", "Kim", "emp@org-a.test", mgrEmpId, empUserId);
        // Unrelated employee in Sales (NOT under Marcus)
        String salesEmpId = createEmployee(
                orgAOwnerToken, salesDeptId, "Bob", "Stone", "bob@org-a.test", null, null);

        // Employee submits own leave (allowed)
        LocalDate start = LocalDate.now(ZoneOffset.UTC).plusDays(10);
        MvcResult ownLeaveRes = mockMvc.perform(post("/api/v1/hr/leave-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeId": "%s",
                                  "leaveType": "CASUAL_LEAVE",
                                  "startDate": "%s",
                                  "endDate": "%s",
                                  "reason": "Personal day"
                                }
                                """.formatted(directReportId, start, start)))
                .andExpect(status().isCreated())
                .andReturn();
        String ownLeaveId = extractId(ownLeaveRes);

        // Employee cannot submit leave for someone else
        mockMvc.perform(post("/api/v1/hr/leave-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeId": "%s",
                                  "leaveType": "CASUAL_LEAVE",
                                  "startDate": "%s",
                                  "endDate": "%s",
                                  "reason": "Impersonation"
                                }
                                """.formatted(salesEmpId, start, start)))
                .andExpect(status().isForbidden());

        // Employee cannot view Bob's profile, departments, or approve own leave
        mockMvc.perform(get("/api/v1/hr/employees/" + salesEmpId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/hr/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/hr/leave-requests/" + ownLeaveId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isForbidden());

        // Employee list for EMPLOYEE returns only self (1 item)
        mockMvc.perform(get("/api/v1/hr/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.data[0].id").value(directReportId));

        // Manager creates a leave request for Sales employee (via Owner) and own leave (via Manager)
        MvcResult salesLeaveRes = mockMvc.perform(post("/api/v1/hr/leave-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAOwnerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeId": "%s",
                                  "leaveType": "EARNED_LEAVE",
                                  "startDate": "%s",
                                  "endDate": "%s",
                                  "reason": "Conference"
                                }
                                """.formatted(salesEmpId, start, start)))
                .andExpect(status().isCreated())
                .andReturn();
        String salesLeaveId = extractId(salesLeaveRes);

        MvcResult mgrLeaveRes = mockMvc.perform(post("/api/v1/hr/leave-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeId": "%s",
                                  "leaveType": "CASUAL_LEAVE",
                                  "startDate": "%s",
                                  "endDate": "%s",
                                  "reason": "Offsite"
                                }
                                """.formatted(mgrEmpId, start.plusDays(2), start.plusDays(2))))
                .andExpect(status().isCreated())
                .andReturn();
        String mgrLeaveId = extractId(mgrLeaveRes);

        // Manager cannot approve out-of-scope Sales employee leave or own leave
        mockMvc.perform(post("/api/v1/hr/leave-requests/" + salesLeaveId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/hr/leave-requests/" + mgrLeaveId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken))
                .andExpect(status().isForbidden());

        // Manager CAN approve direct report Alice's leave
        mockMvc.perform(post("/api/v1/hr/leave-requests/" + ownLeaveId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "comments": "Approved by manager" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // Manager cannot write HR employees or departments
        mockMvc.perform(post("/api/v1/hr/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Unauthorized Dept" }
                                """))
                .andExpect(status().isForbidden());

        // Cross-tenant isolation: Org B cannot read or mutate Org A's HR resources
        mockMvc.perform(get("/api/v1/hr/employees/" + directReportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBOwnerToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/hr/departments/" + engDeptId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBOwnerToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/hr/leave-requests/" + ownLeaveId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBOwnerToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/hr/leave-requests/" + salesLeaveId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgBOwnerToken))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ Helpers

    private String registerAndGetToken(String email, String orgName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "StrongPass!123",
                                  "displayName": "Test Owner",
                                  "organizationName": "%s"
                                }
                                """.formatted(email, orgName)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("accessToken").asText();
    }

    private String createMember(String ownerToken, String email, String displayName, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/organizations/current/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "%s",
                                  "password": "StrongPass!123",
                                  "role": "%s"
                                }
                                """.formatted(email, displayName, role)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("userId").asText();
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("accessToken").asText();
    }

    private String createDepartment(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/hr/departments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "%s" }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return extractId(result);
    }

    private String createEmployee(
            String token,
            String departmentId,
            String firstName,
            String lastName,
            String email,
            String reportingManagerId,
            String userId) throws Exception {
        String managerField = reportingManagerId != null ? "\"" + reportingManagerId + "\"" : "null";
        String userField = userId != null ? "\"" + userId + "\"" : "null";
        MvcResult result = mockMvc.perform(post("/api/v1/hr/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "%s",
                                  "lastName": "%s",
                                  "email": "%s",
                                  "phone": "+1-555-0199",
                                  "departmentId": "%s",
                                  "designation": "Specialist",
                                  "employmentType": "FULL_TIME",
                                  "dateOfJoining": "2026-03-01",
                                  "reportingManagerId": %s,
                                  "userId": %s
                                }
                                """.formatted(firstName, lastName, email, departmentId, managerField, userField)))
                .andExpect(status().isCreated())
                .andReturn();
        return extractId(result);
    }

    private String extractId(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID id = UUID.fromString(root.path("data").path("id").asText());
        return id.toString();
    }
}

