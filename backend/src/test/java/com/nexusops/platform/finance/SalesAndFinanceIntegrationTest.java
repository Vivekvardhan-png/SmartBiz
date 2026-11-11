package com.nexusops.platform.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.platform.crm.CrmNoteRepository;
import com.nexusops.platform.crm.CustomerRepository;
import com.nexusops.platform.crm.LeadRepository;
import com.nexusops.platform.identity.AuthSessionRepository;
import com.nexusops.platform.identity.OrganizationMemberRepository;
import com.nexusops.platform.identity.OrganizationRepository;
import com.nexusops.platform.identity.UserRepository;
import com.nexusops.platform.inventory.Product;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Sales Orders, Invoices & Payments Integration")
class SalesAndFinanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceSequenceRepository sequenceRepository;

    @Autowired
    private SalesOrderHistoryRepository orderHistoryRepository;

    @Autowired
    private SalesOrderRepository orderRepository;

    @Autowired
    private StockMovementRepository movementRepository;

    @Autowired
    private SupplierProductRepository supplierProductRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SupplierRepository supplierRepository;

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
    @DisplayName("Core Lead -> Customer -> Order -> Stock Deduction -> Invoice -> Payments -> Dashboard journey")
    void endToEndOrderToInvoiceToPaymentJourney() throws Exception {
        String token = registerAndGetToken("owner@acme.test", "Acme Corp");

        // 1. Lead -> Customer
        String leadId = createLead(token, "Annual Hardware Order", "Maya Lin", "maya@client.test");
        MvcResult convRes = mockMvc.perform(post("/api/v1/crm/leads/" + leadId + "/convert")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String customerId = objectMapper.readTree(convRes.getResponse().getContentAsString())
                .at("/data/customer/id").asText();

        // 2. Product with initial stock 10 and unitPrice 100.55
        String productId = createProduct(token, "PRO-HUB", "Pro Hub", "100.55", "10");

        // 3. Create DRAFT order: qty 2 = 201.10 subtotal, 10.00 discount, 10% tax => taxable 191.10, tax 19.11, total 210.21
        MvcResult orderRes = mockMvc.perform(post("/api/v1/finance/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "%s",
                                  "discountAmount": 10.00,
                                  "taxRatePercent": 10.00,
                                  "items": [
                                    { "productId": "%s", "quantity": 2 }
                                  ]
                                }
                                """.formatted(customerId, productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.subtotalAmount").value(201.10))
                .andExpect(jsonPath("$.data.taxAmount").value(19.11))
                .andExpect(jsonPath("$.data.totalAmount").value(210.21))
                .andExpect(jsonPath("$.data.stockDeducted").value(false))
                .andReturn();
        String orderId = extractId(orderRes);

        // Cannot generate invoice for DRAFT order
        mockMvc.perform(post("/api/v1/finance/orders/" + orderId + "/invoice")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());

        // 4. Confirm order -> deducts 2 from stock (10 -> 8)
        mockMvc.perform(post("/api/v1/finance/orders/" + orderId + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.stockDeducted").value(true));

        // Confirm retry does NOT double-deduct stock
        mockMvc.perform(post("/api/v1/finance/orders/" + orderId + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        Product productAfterConfirm = productRepository.findById(UUID.fromString(productId)).orElseThrow();
        assertThat(productAfterConfirm.getStockQuantity()).isEqualByComparingTo("8.000");

        // 5. Generate invoice (and verify retry returns same invoice without duplicate)
        MvcResult inv1 = mockMvc.perform(post("/api/v1/finance/orders/" + orderId + "/invoice")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceNumber").value("INV-000001"))
                .andExpect(jsonPath("$.data.status").value("ISSUED"))
                .andExpect(jsonPath("$.data.totalAmount").value(210.21))
                .andExpect(jsonPath("$.data.outstandingBalance").value(210.21))
                .andExpect(jsonPath("$.data.alreadyExisted").value(false))
                .andReturn();
        String invoiceId = extractId(inv1);

        mockMvc.perform(post("/api/v1/finance/orders/" + orderId + "/invoice")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceNumber").value("INV-000001"))
                .andExpect(jsonPath("$.data.alreadyExisted").value(true));

        assertThat(invoiceRepository.count()).isEqualTo(1L);

        // Cannot cancel an invoiced order
        mockMvc.perform(post("/api/v1/finance/orders/" + orderId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());

        // 6. Partial payment ($100.00) + idempotent replay check
        String partialPayBody = """
                {
                  "entryType": "PAYMENT",
                  "amount": 100.00,
                  "method": "BANK_TRANSFER",
                  "reference": "WIRE-101",
                  "idempotencyKey": "pay-101"
                }
                """;
        mockMvc.perform(post("/api/v1/finance/invoices/" + invoiceId + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(partialPayBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.idempotentReplay").value(false));

        mockMvc.perform(post("/api/v1/finance/invoices/" + invoiceId + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(partialPayBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.idempotentReplay").value(true));

        mockMvc.perform(get("/api/v1/finance/invoices/" + invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.data.paidAmount").value(100.00))
                .andExpect(jsonPath("$.data.outstandingBalance").value(110.21));

        // Overpayment rejected
        mockMvc.perform(post("/api/v1/finance/invoices/" + invoiceId + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entryType": "PAYMENT",
                                  "amount": 120.00,
                                  "method": "CARD"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity());

        // Over-refund rejected
        mockMvc.perform(post("/api/v1/finance/invoices/" + invoiceId + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entryType": "REFUND",
                                  "amount": 150.00,
                                  "method": "BANK_TRANSFER"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity());

        // 7. Dashboard reflects partial balance, then final payment brings outstanding to 0
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.periodRevenue.moduleAvailable").value(true))
                .andExpect(jsonPath("$.data.periodRevenue.value").value(210.21))
                .andExpect(jsonPath("$.data.outstandingInvoiceBalance.value").value(110.21))
                .andExpect(jsonPath("$.data.salesOrdersByStatus.value.CONFIRMED").value(1));

        mockMvc.perform(post("/api/v1/finance/invoices/" + invoiceId + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entryType": "PAYMENT",
                                  "amount": 110.21,
                                  "method": "CARD",
                                  "reference": "AUTH-202"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/finance/invoices/" + invoiceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.outstandingBalance").value(0.00))
                .andExpect(jsonPath("$.data.payments.length()").value(2));
    }

    @Test
    @DisplayName("Order cancellation restores deducted stock and rejects insufficient stock atomically")
    void cancellationRestoresStockAndInsufficientStockRollsBack() throws Exception {
        String token = registerAndGetToken("owner@cancel.test", "Cancel Co");
        String customerId = createCustomer(token, "Client A", "a@client.test");
        String productA = createProduct(token, "ITEM-A", "Item A", "20.00", "5");
        String productB = createProduct(token, "ITEM-B", "Item B", "30.00", "2");

        // Order requiring 3 of A and 5 of B (B only has 2) fails atomically without deducting A
        mockMvc.perform(post("/api/v1/finance/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "%s",
                                  "confirmImmediately": true,
                                  "items": [
                                    { "productId": "%s", "quantity": 3 },
                                    { "productId": "%s", "quantity": 5 }
                                  ]
                                }
                                """.formatted(customerId, productA, productB)))
                .andExpect(status().isUnprocessableEntity());

        assertThat(productRepository.findById(UUID.fromString(productA)).orElseThrow().getStockQuantity())
                .isEqualByComparingTo("5.000");

        // Confirm valid order of 3 of A (5 -> 2), then cancel (2 -> 5)
        MvcResult okOrder = mockMvc.perform(post("/api/v1/finance/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "%s",
                                  "confirmImmediately": true,
                                  "items": [
                                    { "productId": "%s", "quantity": 3 }
                                  ]
                                }
                                """.formatted(customerId, productA)))
                .andExpect(status().isCreated())
                .andReturn();
        String orderId = extractId(okOrder);

        assertThat(productRepository.findById(UUID.fromString(productA)).orElseThrow().getStockQuantity())
                .isEqualByComparingTo("2.000");

        mockMvc.perform(post("/api/v1/finance/orders/" + orderId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.stockDeducted").value(false));

        assertThat(productRepository.findById(UUID.fromString(productA)).orElseThrow().getStockQuantity())
                .isEqualByComparingTo("5.000");
    }

    @Test
    @DisplayName("Cross-organization finance access returns 404 and EMPLOYEE role returns 403")
    void crossOrganizationAndRoleAuthorizationBoundaries() throws Exception {
        String alphaToken = registerAndGetToken("owner@alpha.test", "Alpha Co");
        String betaToken = registerAndGetToken("owner@beta.test", "Beta Co");

        // Add EMPLOYEE to Alpha
        mockMvc.perform(post("/api/v1/organizations/current/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "emp@alpha.test",
                                  "password": "EmployeePass123",
                                  "role": "EMPLOYEE"
                                }
                                """))
                .andExpect(status().isCreated());

        MvcResult empLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"emp@alpha.test\",\"password\":\"EmployeePass123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String empToken = objectMapper.readTree(empLogin.getResponse().getContentAsString())
                .at("/data/accessToken").asText();

        mockMvc.perform(get("/api/v1/finance/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + empToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/finance/invoices")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + empToken))
                .andExpect(status().isForbidden());

        String customerId = createCustomer(alphaToken, "Alpha Client", "ac@alpha.test");
        String productId = createProduct(alphaToken, "ALF-1", "Alpha Item", "15.00", "10");
        MvcResult orderRes = mockMvc.perform(post("/api/v1/finance/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "%s",
                                  "confirmImmediately": true,
                                  "items": [{ "productId": "%s", "quantity": 1 }]
                                }
                                """.formatted(customerId, productId)))
                .andExpect(status().isCreated())
                .andReturn();
        String alphaOrderId = extractId(orderRes);

        MvcResult invRes = mockMvc.perform(post("/api/v1/finance/orders/" + alphaOrderId + "/invoice")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken))
                .andExpect(status().isOk())
                .andReturn();
        String alphaInvoiceId = extractId(invRes);

        // Beta cannot view or mutate Alpha's order or invoice
        mockMvc.perform(get("/api/v1/finance/orders/" + alphaOrderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/finance/invoices/" + alphaInvoiceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/finance/invoices/" + alphaInvoiceId + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryType\":\"PAYMENT\",\"amount\":5.00,\"method\":\"CASH\"}"))
                .andExpect(status().isNotFound());
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

    private String createLead(String token, String title, String contact, String email) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/crm/leads")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "contactName": "%s",
                                  "email": "%s"
                                }
                                """.formatted(title, contact, email)))
                .andExpect(status().isCreated())
                .andReturn();
        return extractId(res);
    }

    private String createCustomer(String token, String name, String email) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/crm/customers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "email": "%s"
                                }
                                """.formatted(name, email)))
                .andExpect(status().isCreated())
                .andReturn();
        return extractId(res);
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

