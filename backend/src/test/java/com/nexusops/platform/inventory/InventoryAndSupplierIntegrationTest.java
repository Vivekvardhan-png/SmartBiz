package com.nexusops.platform.inventory;

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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Inventory & Suppliers Integration")
class InventoryAndSupplierIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StockMovementRepository movementRepository;

    @Autowired
    private SupplierProductRepository supplierProductRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SupplierRepository supplierRepository;

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
        movementRepository.deleteAll();
        supplierProductRepository.deleteAll();
        productRepository.deleteAll();
        supplierRepository.deleteAll();
        sessionRepository.deleteAll();
        memberRepository.deleteAll();
        organizationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Unauthenticated requests to inventory endpoints return 401")
    void unauthenticatedRequestsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/products")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/inventory/suppliers")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/inventory/movements")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Product creation, SKU uniqueness, supplier linking, movements, idempotency, and reconciliation")
    void productSupplierMovementsAndReconciliation() throws Exception {
        String token = registerAndGetToken("owner@supply.test", "Supply Co");

        // Create supplier
        MvcResult supRes = mockMvc.perform(post("/api/v1/inventory/suppliers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Apex Hardware Supply",
                                  "contactName": "Dana Reed",
                                  "email": "orders@apex.test"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String supplierId = extractId(supRes);

        // Create product with initial stock 15 and threshold 10
        MvcResult prodRes = mockMvc.perform(post("/api/v1/inventory/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sku": "cable-usb-c",
                                  "name": "USB-C Braided Cable",
                                  "category": "Accessories",
                                  "unitOfMeasure": "pcs",
                                  "unitPrice": 19.99,
                                  "costPrice": 7.50,
                                  "initialStock": 15,
                                  "lowStockThreshold": 10
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sku").value("CABLE-USB-C"))
                .andExpect(jsonPath("$.data.stockQuantity").value(15))
                .andExpect(jsonPath("$.data.lowStock").value(false))
                .andReturn();
        String productId = extractId(prodRes);

        // Duplicate SKU rejected
        mockMvc.perform(post("/api/v1/inventory/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sku": "CABLE-USB-C",
                                  "name": "Duplicate Cable",
                                  "unitOfMeasure": "PCS",
                                  "unitPrice": 10.00,
                                  "lowStockThreshold": 5
                                }
                                """))
                .andExpect(status().isConflict());

        // Link supplier to product
        mockMvc.perform(post("/api/v1/inventory/products/" + productId + "/suppliers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId": "%s",
                                  "supplierSku": "APX-99",
                                  "unitCost": 7.25,
                                  "leadTimeDays": 4
                                }
                                """.formatted(supplierId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supplierName").value("Apex Hardware Supply"));

        // Receipt with idempotency key (+5 -> stock 20)
        String receiptPayload = """
                {
                  "movementType": "RECEIPT",
                  "quantity": 5,
                  "reason": "Restock PO-104",
                  "reference": "PO-104",
                  "supplierId": "%s",
                  "idempotencyKey": "rcpt-po-104"
                }
                """.formatted(supplierId);

        mockMvc.perform(post("/api/v1/inventory/products/" + productId + "/movements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.quantityAfter").value(20))
                .andExpect(jsonPath("$.data.idempotentReplay").value(false));

        // Replaying the same idempotencyKey does NOT double-credit stock
        mockMvc.perform(post("/api/v1/inventory/products/" + productId + "/movements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.quantityAfter").value(20))
                .andExpect(jsonPath("$.data.idempotentReplay").value(true));

        // Reject negative stock deduction (-25 on 20) and preserve stock = 20
        mockMvc.perform(post("/api/v1/inventory/products/" + productId + "/movements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "movementType": "ADJUSTMENT_OUT",
                                  "quantity": 25,
                                  "reason": "Invalid write-off"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity());

        // Reject manual ORDER_DEDUCTION via REST endpoint
        mockMvc.perform(post("/api/v1/inventory/products/" + productId + "/movements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "movementType": "ORDER_DEDUCTION",
                                  "quantity": 2,
                                  "reason": "Not allowed manually"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity());

        // Deduct 12 -> stock 8 (below threshold 10 => lowStock = true)
        mockMvc.perform(post("/api/v1/inventory/products/" + productId + "/movements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "movementType": "ADJUSTMENT_OUT",
                                  "quantity": 12,
                                  "reason": "Cycle count variance"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.quantityAfter").value(8));

        // Verify product stock reconciles with SUM(movements) and triggers low-stock dashboard count
        Product product = productRepository.findById(UUID.fromString(productId)).orElseThrow();
        BigDecimal sumDeltas = movementRepository.sumQuantityDeltaByOrganizationIdAndProductId(
                product.getOrganizationId(), product.getId());
        assertThat(product.getStockQuantity()).isEqualByComparingTo("8.000");
        assertThat(sumDeltas).isEqualByComparingTo("8.000");

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lowStockProductsCount.moduleAvailable").value(true))
                .andExpect(jsonPath("$.data.lowStockProductsCount.value").value(1));

        // Verify SKU search & lowStockOnly filter
        mockMvc.perform(get("/api/v1/inventory/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("search", "CABLE")
                        .param("lowStockOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    @DisplayName("Concurrent stock deductions serialize cleanly and never produce negative stock")
    void concurrentStockDeductionsCannotOversell() throws Exception {
        String token = registerAndGetToken("owner@race.test", "Race Co");
        MvcResult prodRes = mockMvc.perform(post("/api/v1/inventory/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sku": "RACE-ITEM-1",
                                  "name": "Concurrent Item",
                                  "unitOfMeasure": "PCS",
                                  "unitPrice": 50.00,
                                  "initialStock": 10,
                                  "lowStockThreshold": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String productId = extractId(prodRes);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(post("/api/v1/inventory/products/" + productId + "/movements")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "movementType": "ADJUSTMENT_OUT",
                                          "quantity": 7,
                                          "reason": "Concurrent deduction %d"
                                        }
                                        """.formatted(idx)))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            }));
        }

        ready.await();
        start.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) {
            statuses.add(f.get());
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(201, 422);

        Product updated = productRepository.findById(UUID.fromString(productId)).orElseThrow();
        assertThat(updated.getStockQuantity()).isEqualByComparingTo("3.000");
        assertThat(movementRepository.sumQuantityDeltaByOrganizationIdAndProductId(
                updated.getOrganizationId(), updated.getId())).isEqualByComparingTo("3.000");
    }

    @Test
    @DisplayName("Inventory products, suppliers, and movements enforce organization isolation")
    void crossOrganizationIsolationIsEnforced() throws Exception {
        String alphaToken = registerAndGetToken("owner@alpha.test", "Alpha Co");
        String betaToken = registerAndGetToken("owner@beta.test", "Beta Co");

        MvcResult alphaProd = mockMvc.perform(post("/api/v1/inventory/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sku": "ALPHA-01",
                                  "name": "Alpha Widget",
                                  "unitOfMeasure": "PCS",
                                  "unitPrice": 25.00,
                                  "initialStock": 5,
                                  "lowStockThreshold": 10
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String alphaProductId = extractId(alphaProd);

        mockMvc.perform(get("/api/v1/inventory/products/" + alphaProductId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/inventory/products/" + alphaProductId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tampered\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/inventory/products/" + alphaProductId + "/movements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"movementType\":\"RECEIPT\",\"quantity\":10,\"reason\":\"Hijack\"}"))
                .andExpect(status().isNotFound());

        // Beta dashboard does not count Alpha's low-stock product
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lowStockProductsCount.value").value(0));
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

    private String extractId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/id")
                .asText();
    }
}

