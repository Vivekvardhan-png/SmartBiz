package com.nexusops.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.platform.assistant.AssistantActionProposalRepository;
import com.nexusops.platform.assistant.AssistantAuditLogRepository;
import com.nexusops.platform.automation.AutomationAlertRepository;
import com.nexusops.platform.automation.AutomationRunRepository;
import com.nexusops.platform.crm.CrmNoteRepository;
import com.nexusops.platform.crm.CustomerRepository;
import com.nexusops.platform.crm.LeadRepository;
import com.nexusops.platform.finance.InvoiceRepository;
import com.nexusops.platform.finance.InvoiceSequenceRepository;
import com.nexusops.platform.finance.PaymentRepository;
import com.nexusops.platform.finance.SalesOrderHistoryRepository;
import com.nexusops.platform.finance.SalesOrderRepository;
import com.nexusops.platform.identity.AuthSessionRepository;
import com.nexusops.platform.identity.OrganizationMemberRepository;
import com.nexusops.platform.identity.OrganizationRepository;
import com.nexusops.platform.identity.UserRepository;
import com.nexusops.platform.inventory.ProductRepository;
import com.nexusops.platform.inventory.StockMovementRepository;
import com.nexusops.platform.inventory.SupplierProductRepository;
import com.nexusops.platform.inventory.SupplierRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Full-Stack Business Integrity & Security Release Audit")
class ReleaseAuditEndToEndTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private AutomationAlertRepository automationAlertRepository;
    @Autowired private AutomationRunRepository automationRunRepository;
    @Autowired private AssistantAuditLogRepository auditLogRepository;
    @Autowired private AssistantActionProposalRepository proposalRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private InvoiceSequenceRepository sequenceRepository;
    @Autowired private SalesOrderHistoryRepository orderHistoryRepository;
    @Autowired private SalesOrderRepository orderRepository;
    @Autowired private StockMovementRepository movementRepository;
    @Autowired private SupplierProductRepository supplierProductRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private CrmNoteRepository noteRepository;
    @Autowired private LeadRepository leadRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AuthSessionRepository sessionRepository;
    @Autowired private OrganizationMemberRepository memberRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        automationAlertRepository.deleteAll();
        automationRunRepository.deleteAll();
        auditLogRepository.deleteAll();
        proposalRepository.deleteAll();
        paymentRepository.deleteAll();
        invoiceRepository.deleteAll();
        sequenceRepository.deleteAll();
        orderHistoryRepository.deleteAll();
        orderRepository.deleteAll();
        movementRepository.deleteAll();
        supplierProductRepository.deleteAll();
        productRepository.deleteAll();
        supplierRepository.deleteAll();
        noteRepository.deleteAll();
        leadRepository.deleteAll();
        customerRepository.deleteAll();
        sessionRepository.deleteAll();
        memberRepository.deleteAll();
        organizationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Complete Lead -> Customer -> Order -> Stock -> Invoice -> Payment -> Dashboard/Reports flow and security headers")
    void completeBusinessLifecycleAndSecurityAudit() throws Exception {
        // 1. Register Organization + Owner & provision an Employee
        MvcResult regRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "owner@audit.test",
                                  "password": "StrongPass123",
                                  "displayName": "Audit Owner",
                                  "organizationName": "Audit Corp"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andReturn();
        String ownerToken = objectMapper.readTree(regRes.getResponse().getContentAsString())
                .at("/data/accessToken").asText();

        mockMvc.perform(post("/api/v1/organizations/current/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "emp@audit.test",
                                  "displayName": "Audit Employee",
                                  "password": "StrongPass123",
                                  "role": "EMPLOYEE"
                                }
                                """))
                .andExpect(status().isCreated());

        MvcResult empLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "emp@audit.test",
                                  "password": "StrongPass123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String empToken = objectMapper.readTree(empLogin.getResponse().getContentAsString())
                .at("/data/accessToken").asText();

        // Unknown route returns clean 404 (not 500)
        mockMvc.perform(get("/api/v1/does-not-exist-audit-route")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // 2. Lead -> Customer conversion
        MvcResult leadRes = mockMvc.perform(post("/api/v1/crm/leads")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Enterprise Kit Deal",
                                  "contactName": "Dana Smith",
                                  "companyName": "Northwind LLC",
                                  "email": "dana@northwind.test",
                                  "estimatedValue": 500.00
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String leadId = objectMapper.readTree(leadRes.getResponse().getContentAsString())
                .at("/data/id").asText();

        MvcResult convertRes = mockMvc.perform(post("/api/v1/crm/leads/" + leadId + "/convert")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lead.status").value("CONVERTED"))
                .andReturn();
        String customerId = objectMapper.readTree(convertRes.getResponse().getContentAsString())
                .at("/data/customer/id").asText();

        // 3. Products + atomic rollback check when second item lacks stock at confirmation time
        MvcResult p1Res = mockMvc.perform(post("/api/v1/inventory/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sku": "AUD-1",
                                  "name": "Audit Sensor",
                                  "unitOfMeasure": "PCS",
                                  "unitPrice": 100.00,
                                  "lowStockThreshold": 5.000,
                                  "initialStock": 6.000
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String p1Id = objectMapper.readTree(p1Res.getResponse().getContentAsString())
                .at("/data/id").asText();

        // 4. Draft order (2 units * 100 = 200, discount 20, tax 10% on 180 = 18 -> total 198.00), confirm & invoice
        MvcResult draftRes = mockMvc.perform(post("/api/v1/finance/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "%s",
                                  "discountAmount": 20.00,
                                  "taxRatePercent": 10.00,
                                  "confirmImmediately": false,
                                  "items": [
                                    { "productId": "%s", "quantity": 2 }
                                  ]
                                }
                                """.formatted(customerId, p1Id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalAmount").value(198.00))
                .andReturn();
        String orderId = objectMapper.readTree(draftRes.getResponse().getContentAsString())
                .at("/data/id").asText();

        // Confirm deducts stock cleanly (6 -> 4, now <= lowStockThreshold 5)
        mockMvc.perform(post("/api/v1/finance/orders/" + orderId + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.stockDeducted").value(true));

        mockMvc.perform(get("/api/v1/inventory/products/" + p1Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockQuantity").value(4.000))
                .andExpect(jsonPath("$.data.lowStock").value(true));

        // 5. Generate invoice (idempotent on retry) and record partial payment + idempotent replay
        MvcResult invRes = mockMvc.perform(post("/api/v1/finance/orders/" + orderId + "/invoice")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(198.00))
                .andReturn();
        JsonNode invData = objectMapper.readTree(invRes.getResponse().getContentAsString()).at("/data");
        String invoiceId = invData.get("id").asText();

        mockMvc.perform(post("/api/v1/finance/invoices/" + invoiceId + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entryType": "PAYMENT",
                                  "amount": 98.00,
                                  "method": "CARD",
                                  "idempotencyKey": "pay-audit-1"
                                }
                                """))
                .andExpect(status().isCreated());

        // Idempotent replay with same key must NOT double-pay
        mockMvc.perform(post("/api/v1/finance/invoices/" + invoiceId + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entryType": "PAYMENT",
                                  "amount": 98.00,
                                  "method": "CARD",
                                  "idempotencyKey": "pay-audit-1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.idempotentReplay").value(true));

        // 6. Owner Dashboard & Insights Reports reconcile exactly (198 revenue, 100 outstanding, 1 low stock)
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCustomers").value(1))
                .andExpect(jsonPath("$.data.lowStockProductsCount.value").value(1))
                .andExpect(jsonPath("$.data.periodRevenue.moduleAvailable").value(true))
                .andExpect(jsonPath("$.data.periodRevenue.value").value(198.00))
                .andExpect(jsonPath("$.data.outstandingInvoiceBalance.value").value(100.00));

        // Employee Dashboard MUST hide finance KPIs (no FINANCE_READ permission)
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + empToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCustomers").value(1))
                .andExpect(jsonPath("$.data.lowStockProductsCount.moduleAvailable").value(true))
                .andExpect(jsonPath("$.data.periodRevenue.moduleAvailable").value(false))
                .andExpect(jsonPath("$.data.periodRevenue.value").doesNotExist())
                .andExpect(jsonPath("$.data.outstandingInvoiceBalance.moduleAvailable").value(false))
                .andExpect(jsonPath("$.data.outstandingInvoiceBalance.value").doesNotExist())
                .andExpect(jsonPath("$.data.salesOrdersByStatus.moduleAvailable").value(false));

        // Owner Insights report reconciles payment + balance
        mockMvc.perform(get("/api/v1/insights/reports/receivables")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outstandingTotal").value(100.00))
                .andExpect(jsonPath("$.data.totalPaymentsInPeriod").value(98.00));

        // Automation picks up the low-stock item from order deduction
        mockMvc.perform(post("/api/v1/automation/workflows/run")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workflowType\":\"LOW_STOCK_ALERT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runs[0].alertsCreated").value(1));
    }
}

