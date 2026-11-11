package com.nexusops.platform.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
@DisplayName("Authentication & Multi-Tenant Authorization")
class AuthAndTenancyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
        sessionRepository.deleteAll();
        memberRepository.deleteAll();
        organizationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Protected endpoints reject unauthenticated requests with 401 JSON ErrorResponse")
    void protectedEndpointsRejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(get("/api/v1/organizations/current/members"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Registration hashes password with BCrypt, creates org + OWNER, and rejects duplicates/weak passwords")
    void registrationAndValidationWork() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "weak@acme.test",
                                  "password": "short",
                                  "displayName": "Weak Pass",
                                  "organizationName": "Acme Corp"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));

        String regBody = """
                {
                  "email": "Owner@Acme.test",
                  "password": "StrongPass123",
                  "displayName": "Alice Owner",
                  "organizationName": "Acme Corp"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("owner@acme.test"))
                .andExpect(jsonPath("$.data.activeOrganization.role").value("OWNER"))
                .andReturn();

        User savedUser = userRepository.findByEmailIgnoreCase("owner@acme.test").orElseThrow();
        assertThat(savedUser.getPasswordHash()).startsWith("$2");
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("StrongPass123");

        String token = extractToken(result);
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeOrganization.name").value("Acme Corp"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Login rejects wrong password and logout immediately revokes token")
    void loginAndLogoutRevocation() throws Exception {
        registerOrg("owner@alpha.test", "AlphaPass123", "Alpha Owner", "Alpha LLC");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "owner@alpha.test",
                                  "password": "WrongPassword1"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "owner@alpha.test",
                                  "password": "AlphaPass123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String token = extractToken(loginResult);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Users cannot access or mutate another organization's members or switch into non-member orgs")
    void crossOrganizationIsolationIsEnforced() throws Exception {
        JsonNode alpha = registerOrg("owner@alpha.test", "AlphaPass123", "Alpha Owner", "Alpha LLC");
        JsonNode beta = registerOrg("owner@beta.test", "BetaPass123", "Beta Owner", "Beta LLC");

        String alphaToken = alpha.at("/data/accessToken").asText();
        String betaToken = beta.at("/data/accessToken").asText();
        String betaOrgId = beta.at("/data/activeOrganization/id").asText();

        // Alpha owner cannot switch into Beta org
        mockMvc.perform(post("/api/v1/auth/switch-organization")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + betaOrgId + "\"}"))
                .andExpect(status().isForbidden());

        // Beta adds an employee
        MvcResult betaMemberResult = mockMvc.perform(post("/api/v1/organizations/current/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "staff@beta.test",
                                  "displayName": "Beta Staff",
                                  "password": "StaffPass123",
                                  "role": "EMPLOYEE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String betaMemberId = objectMapper.readTree(betaMemberResult.getResponse().getContentAsString())
                .at("/data/id").asText();

        // Alpha owner listing members only sees Alpha (1 member)
        mockMvc.perform(get("/api/v1/organizations/current/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].email").value("owner@alpha.test"));

        // Alpha owner cannot modify or delete Beta's member by ID
        mockMvc.perform(patch("/api/v1/organizations/current/members/" + betaMemberId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/organizations/current/members/" + betaMemberId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Role restrictions block EMPLOYEE from member management and revoke sessions on removal")
    void roleRestrictionsAndMemberRemovalSessionRevocation() throws Exception {
        JsonNode orgData = registerOrg("owner@gamma.test", "GammaPass123", "Gamma Owner", "Gamma Inc");
        String ownerToken = orgData.at("/data/accessToken").asText();

        MvcResult addEmp = mockMvc.perform(post("/api/v1/organizations/current/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "emp@gamma.test",
                                  "displayName": "Gamma Employee",
                                  "password": "EmployeePass123",
                                  "role": "EMPLOYEE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String empMemberId = objectMapper.readTree(addEmp.getResponse().getContentAsString())
                .at("/data/id").asText();

        MvcResult empLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "emp@gamma.test",
                                  "password": "EmployeePass123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String empToken = extractToken(empLogin);

        // Employee cannot list or add members
        mockMvc.perform(get("/api/v1/organizations/current/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + empToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/organizations/current/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "other@gamma.test",
                                  "password": "EmployeePass123",
                                  "role": "EMPLOYEE"
                                }
                                """))
                .andExpect(status().isForbidden());

        // Owner cannot demote themselves if they are the sole OWNER
        String ownerMemberId = memberRepository.findAllWithUserByOrganizationId(
                        java.util.UUID.fromString(orgData.at("/data/activeOrganization/id").asText()))
                .stream()
                .filter(m -> m.getRole() == OrganizationRole.OWNER)
                .findFirst()
                .orElseThrow()
                .getId()
                .toString();

        mockMvc.perform(patch("/api/v1/organizations/current/members/" + ownerMemberId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isUnprocessableEntity());

        // Removing the employee immediately revokes their active session
        mockMvc.perform(delete("/api/v1/organizations/current/members/" + empMemberId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + empToken))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode registerOrg(String email, String password, String displayName, String orgName)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "displayName": "%s",
                                  "organizationName": "%s"
                                }
                                """.formatted(email, password, displayName, orgName)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String extractToken(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/accessToken")
                .asText();
    }
}

