package com.nexusops.platform.insights;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Business Reports, CSV Exports & Forecasting Resilience")
class InsightsAndForecastingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    @DisplayName("Reports reconcile with business records without double-counting and neutralize CSV formula injection")
    void reportsReconcileAndCsvPreventsFormulaInjection() throws Exception {
        String alphaToken = registerAndGetToken("owner@alpha.test", "Alpha Co");
        String betaToken = registerAndGetToken("owner@beta.test", "Beta Co");

        // Customer with potential spreadsheet injection payload in name
        MvcResult custRes = mockMvc.perform(post("/api/v1/crm/customers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "=CMD|' /C calc'!A0",
                                  "email": "risk@alpha.test"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String customerId = extractId(custRes);

        // Two products
        String prod1 = createProduct(alphaToken, "SKU-1", "+Dangerous Item", "50.00", "10");
        String prod2 = createProduct(alphaToken, "SKU-2", "Safe Item", "25.00", "4");

        // Multi-line order: 2 * 50.00 + 2 * 25.00 = 150.00 (verifies multi-item joins do NOT double-count revenue!)
        MvcResult ordRes = mockMvc.perform(post("/api/v1/finance/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "%s",
                                  "confirmImmediately": true,
                                  "items": [
                                    { "productId": "%s", "quantity": 2 },
                                    { "productId": "%s", "quantity": 2 }
                                  ]
                                }
                                """.formatted(customerId, prod1, prod2)))
                .andExpect(status().isCreated())
                .andReturn();
        String orderId = extractId(ordRes);

        MvcResult invRes = mockMvc.perform(post("/api/v1/finance/orders/" + orderId + "/invoice")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken))
                .andExpect(status().isOk())
                .andReturn();
        String invoiceId = extractId(invRes);

        // Two partial payments (40.00 + 30.00 = 70.00; outstanding = 80.00)
        mockMvc.perform(post("/api/v1/finance/invoices/" + invoiceId + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryType\":\"PAYMENT\",\"amount\":40.00,\"method\":\"CASH\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/finance/invoices/" + invoiceId + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryType\":\"PAYMENT\",\"amount\":30.00,\"method\":\"CARD\"}"))
                .andExpect(status().isCreated());

        // Sales over time must equal 150.00 (1 invoice), NOT 300.00
        mockMvc.perform(get("/api/v1/insights/reports/sales-over-time")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRevenue").value(150.00))
                .andExpect(jsonPath("$.data.totalInvoices").value(1));

        // Receivables report
        mockMvc.perform(get("/api/v1/insights/reports/receivables")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outstandingTotal").value(80.00))
                .andExpect(jsonPath("$.data.totalPaymentsInPeriod").value(70.00))
                .andExpect(jsonPath("$.data.paymentsInPeriod.length()").value(2));

        // Inventory stock report (SKU-1: 8 * 50 = 400; SKU-2: 2 * 25 = 50 -> total 450.00, 1 low stock)
        mockMvc.perform(get("/api/v1/insights/reports/inventory-stock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSkus").value(2))
                .andExpect(jsonPath("$.data.lowStockCount").value(1))
                .andExpect(jsonPath("$.data.totalStockValue").value(450.00));

        // CSV exports neutralize leading = and + with leading apostrophe
        MvcResult csvInv = mockMvc.perform(get("/api/v1/insights/reports/export/outstanding-invoices")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn();
        assertThat(csvInv.getResponse().getContentAsString()).contains("'=CMD|' /C calc'!A0");

        MvcResult csvStock = mockMvc.perform(get("/api/v1/insights/reports/export/inventory-stock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(csvStock.getResponse().getContentAsString()).contains("'+Dangerous Item");

        // Beta organization sees zero Alpha records in reports and exports
        mockMvc.perform(get("/api/v1/insights/reports/sales-over-time")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRevenue").value(0));

        mockMvc.perform(get("/api/v1/insights/forecast/inventory/" + prod1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Insights endpoints enforce permissions and degrade gracefully when forecasting is unavailable")
    void permissionsAndForecastingFallbackWork() throws Exception {
        String ownerToken = registerAndGetToken("owner@gamma.test", "Gamma Co");

        // Add EMPLOYEE
        mockMvc.perform(post("/api/v1/organizations/current/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "emp@gamma.test",
                                  "password": "EmployeePass123",
                                  "role": "EMPLOYEE"
                                }
                                """))
                .andExpect(status().isCreated());

        MvcResult empLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"emp@gamma.test\",\"password\":\"EmployeePass123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String empToken = objectMapper.readTree(empLogin.getResponse().getContentAsString())
                .at("/data/accessToken").asText();

        mockMvc.perform(get("/api/v1/insights/reports/sales-over-time")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + empToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/insights/reports/export/sales-over-time")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + empToken))
                .andExpect(status().isForbidden());

        // Forecasting disabled/unavailable returns explicit UNAVAILABLE status
        mockMvc.perform(get("/api/v1/insights/forecast/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .param("horizonDays", "14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.serviceAvailable").value(false));

        // Invalid horizon rejected
        mockMvc.perform(get("/api/v1/insights/forecast/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .param("horizonDays", "90"))
                .andExpect(status().isUnprocessableEntity());
    }

    private String registerAndGetToken(String email, String orgName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "StrongPass123",
                                  "displayName": "Owner",
                                  "organizationName": "%s"
                                }
                                """.formatted(email, orgName)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.at("/data/accessToken").asText();
    }

    private String createProduct(
            String token,
            String sku,
            String name,
            String unitPrice,
            String initialStock) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/inventory/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sku": "%s",
                                  "name": "%s",
                                  "unitOfMeasure": "PCS",
                                  "unitPrice": %s,
                                  "initialStock": %s,
                                  "lowStockThreshold": 2
                                }
                                """.formatted(sku, name, unitPrice, initialStock)))
                .andExpect(status().isCreated())
                .andReturn();
        return extractId(res);
    }

    private String extractId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/id")
                .asText();
    }
}

