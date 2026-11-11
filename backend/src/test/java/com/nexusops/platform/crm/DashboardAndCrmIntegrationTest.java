package com.nexusops.platform.crm;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Dashboard & CRM Integration")
class DashboardAndCrmIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CrmNoteRepository noteRepository;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private CustomerRepository customerRepository;

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
        noteRepository.deleteAll();
        leadRepository.deleteAll();
        customerRepository.deleteAll();
        sessionRepository.deleteAll();
        memberRepository.deleteAll();
        organizationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Unauthenticated requests to CRM and Dashboard are rejected with 401")
    void unauthenticatedRequestsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/crm/leads")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/crm/customers")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Lead search, filtering, pagination, validation, and status transitions work")
    void leadSearchFilterPaginationAndTransitions() throws Exception {
        String token = registerAndGetToken("owner@acme.test", "Acme Corp");

        // Validation failure: negative estimated value and blank title
        mockMvc.perform(post("/api/v1/crm/leads")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "contactName": "Sam",
                                  "estimatedValue": -50
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));

        String lead1Id = createLead(token, "Cloud Migration Deal", "Alice Smith", "Northstar", "alice@northstar.test", "12000.00");
        createLead(token, "Hardware Refresh", "Bob Jones", "Beacon", "bob@beacon.test", "4500.00");
        String lead3Id = createLead(token, "Annual Support Contract", "Cara Lee", "Solstice", "cara@solstice.test", "8000.00");

        // Transition lead1 NEW -> CONTACTED -> QUALIFIED
        mockMvc.perform(patch("/api/v1/crm/leads/" + lead1Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONTACTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONTACTED"));

        mockMvc.perform(patch("/api/v1/crm/leads/" + lead1Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"QUALIFIED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUALIFIED"));

        // Reject direct PATCH to CONVERTED
        mockMvc.perform(patch("/api/v1/crm/leads/" + lead1Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONVERTED\"}"))
                .andExpect(status().isUnprocessableEntity());

        // Mark lead3 LOST and confirm converting LOST fails
        mockMvc.perform(patch("/api/v1/crm/leads/" + lead3Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"LOST\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/crm/leads/" + lead3Id + "/convert")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());

        // Search and status filter
        mockMvc.perform(get("/api/v1/crm/leads")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("search", "Northstar")
                        .param("status", "QUALIFIED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.data[0].id").value(lead1Id));

        // Pagination
        mockMvc.perform(get("/api/v1/crm/leads")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("page", "1")
                        .param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("Lead conversion is idempotent on retry and guards against duplicate customer emails")
    void leadConversionIdempotencyAndDuplicateHandling() throws Exception {
        String token = registerAndGetToken("owner@delta.test", "Delta Corp");
        String leadId = createLead(token, "ERP Onboarding", "Maya Lin", "Horizon", "Maya@Horizon.test", "15000.00");

        // First conversion creates customer
        MvcResult first = mockMvc.perform(post("/api/v1/crm/leads/" + leadId + "/convert")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyConverted").value(false))
                .andExpect(jsonPath("$.data.lead.status").value("CONVERTED"))
                .andExpect(jsonPath("$.data.customer.email").value("maya@horizon.test"))
                .andReturn();

        String customerId = objectMapper.readTree(first.getResponse().getContentAsString())
                .at("/data/customer/id").asText();

        // Retry returns same customer with alreadyConverted = true
        mockMvc.perform(post("/api/v1/crm/leads/" + leadId + "/convert")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyConverted").value(true))
                .andExpect(jsonPath("$.data.customer.id").value(customerId));

        assertThat(customerRepository.count()).isEqualTo(1L);

        // Cannot manually transition CONVERTED lead back to open
        mockMvc.perform(patch("/api/v1/crm/leads/" + leadId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"QUALIFIED\"}"))
                .andExpect(status().isUnprocessableEntity());

        // Second lead with same email conflicts unless linkExistingCustomer=true
        String secondLeadId = createLead(token, "Expansion Deal", "Maya Lin", "Horizon", "maya@horizon.test", "5000.00");
        mockMvc.perform(post("/api/v1/crm/leads/" + secondLeadId + "/convert")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/crm/leads/" + secondLeadId + "/convert")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linkExistingCustomer\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyConverted").value(false))
                .andExpect(jsonPath("$.data.customer.id").value(customerId));

        assertThat(customerRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Follow-up notes, duplicate customer email protection, tenant isolation, and dashboard metrics work")
    void notesIsolationAndDashboardSummary() throws Exception {
        String alphaToken = registerAndGetToken("owner@alpha.test", "Alpha Co");
        String betaToken = registerAndGetToken("owner@beta.test", "Beta Co");

        // Alpha creates customer and lead + adds follow-up note
        MvcResult custRes = mockMvc.perform(post("/api/v1/crm/customers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Evan Wright",
                                  "companyName": "Wright Logistics",
                                  "email": "evan@wright.test",
                                  "phone": "+1-555-0101"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String alphaCustomerId = objectMapper.readTree(custRes.getResponse().getContentAsString())
                .at("/data/id").asText();

        // Duplicate email in Alpha rejected
        mockMvc.perform(post("/api/v1/crm/customers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Duplicate Evan",
                                  "email": "EVAN@WRIGHT.TEST"
                                }
                                """))
                .andExpect(status().isConflict());

        // Same email in Beta is allowed (org-scoped uniqueness)
        mockMvc.perform(post("/api/v1/crm/customers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Evan Beta",
                                  "email": "evan@wright.test"
                                }
                                """))
                .andExpect(status().isCreated());

        // Add follow-up note to Alpha customer
        mockMvc.perform(post("/api/v1/crm/customers/" + alphaCustomerId + "/notes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Scheduled quarterly contract review.",
                                  "followUpDate": "2026-12-01"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.followUpDate").value("2026-12-01"));

        mockMvc.perform(get("/api/v1/crm/customers/" + alphaCustomerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followUpDate").value("2026-12-01"));

        // Beta cannot access or mutate Alpha's customer
        mockMvc.perform(get("/api/v1/crm/customers/" + alphaCustomerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/crm/customers/" + alphaCustomerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tampered\"}"))
                .andExpect(status().isNotFound());

        // Alpha creates 2 leads (1 NEW, 1 LOST)
        createLead(alphaToken, "Open Deal", "Noah", "Noah Co", "noah@noah.test", "2000.00");
        String lostLeadId = createLead(alphaToken, "Lost Deal", "Liam", "Liam Co", "liam@liam.test", "1000.00");
        mockMvc.perform(patch("/api/v1/crm/leads/" + lostLeadId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"LOST\"}"))
                .andExpect(status().isOk());

        // Dashboard reflects only Alpha's records
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCustomers").value(1))
                .andExpect(jsonPath("$.data.openLeads").value(1))
                .andExpect(jsonPath("$.data.leadsByStage.NEW").value(1))
                .andExpect(jsonPath("$.data.leadsByStage.LOST").value(1))
                .andExpect(jsonPath("$.data.periodRevenue.moduleAvailable").value(true))
                .andExpect(jsonPath("$.data.lowStockProductsCount.moduleAvailable").value(true))
                .andExpect(jsonPath("$.data.lowStockProductsCount.value").value(0));

        // Invalid date range rejected
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .param("from", "2026-12-10")
                        .param("to", "2026-12-01"))
                .andExpect(status().isUnprocessableEntity());
    }

    private String registerAndGetToken(String email, String orgName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "StrongPass123",
                                  "displayName": "Test Owner",
                                  "organizationName": "%s"
                                }
                                """.formatted(email, orgName)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.at("/data/accessToken").asText();
    }

    private String createLead(
            String token,
            String title,
            String contactName,
            String companyName,
            String email,
            String estimatedValue) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/crm/leads")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "contactName": "%s",
                                  "companyName": "%s",
                                  "email": "%s",
                                  "estimatedValue": %s
                                }
                                """.formatted(title, contactName, companyName, email, estimatedValue)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .at("/data/id").asText();
    }
}

