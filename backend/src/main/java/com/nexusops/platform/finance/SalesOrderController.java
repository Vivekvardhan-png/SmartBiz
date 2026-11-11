package com.nexusops.platform.finance;

import com.nexusops.platform.common.api.ApiResponse;
import com.nexusops.platform.common.api.PagedResponse;
import com.nexusops.platform.finance.dto.FinanceDtos.CreateSalesOrderRequest;
import com.nexusops.platform.finance.dto.FinanceDtos.GenerateInvoiceRequest;
import com.nexusops.platform.finance.dto.FinanceDtos.InvoiceDetailResponse;
import com.nexusops.platform.finance.dto.FinanceDtos.OrderStatusNoteRequest;
import com.nexusops.platform.finance.dto.FinanceDtos.SalesOrderDetailResponse;
import com.nexusops.platform.finance.dto.FinanceDtos.SalesOrderSummaryResponse;
import com.nexusops.platform.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/finance/orders")
@Tag(name = "Sales Orders", description = "Organization-scoped sales order lifecycle and invoice generation.")
public class SalesOrderController {

    private final SalesOrderService orderService;
    private final InvoiceService invoiceService;
    private final TenantContext tenantContext;

    public SalesOrderController(
            SalesOrderService orderService,
            InvoiceService invoiceService,
            TenantContext tenantContext) {
        this.orderService = orderService;
        this.invoiceService = invoiceService;
        this.tenantContext = tenantContext;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_FINANCE_READ')")
    @Operation(summary = "List and filter sales orders")
    public ResponseEntity<PagedResponse<SalesOrderSummaryResponse>> listOrders(
            @RequestParam(required = false) SalesOrderStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResponseEntity.ok(orderService.listOrders(
                tenantContext.requirePrincipal(), status, customerId, page, pageSize));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAuthority('PERM_FINANCE_READ')")
    @Operation(summary = "Get sales order details and status history")
    public ResponseEntity<ApiResponse<SalesOrderDetailResponse>> getOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.of(
                orderService.getOrder(tenantContext.requirePrincipal(), orderId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_FINANCE_WRITE')")
    @Operation(summary = "Create a sales order (DRAFT or immediately CONFIRMED)")
    public ResponseEntity<ApiResponse<SalesOrderDetailResponse>> createOrder(
            @Valid @RequestBody CreateSalesOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(
                        orderService.createOrder(tenantContext.requirePrincipal(), request)));
    }

    @PostMapping("/{orderId}/confirm")
    @PreAuthorize("hasAuthority('PERM_FINANCE_WRITE')")
    @Operation(summary = "Confirm order and deduct inventory atomically")
    public ResponseEntity<ApiResponse<SalesOrderDetailResponse>> confirmOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody(required = false) OrderStatusNoteRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                orderService.confirmOrder(
                        tenantContext.requirePrincipal(),
                        orderId,
                        request != null ? request.note() : null)));
    }

    @PostMapping("/{orderId}/fulfill")
    @PreAuthorize("hasAuthority('PERM_FINANCE_WRITE')")
    @Operation(summary = "Mark a confirmed order as fulfilled")
    public ResponseEntity<ApiResponse<SalesOrderDetailResponse>> fulfillOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody(required = false) OrderStatusNoteRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                orderService.fulfillOrder(
                        tenantContext.requirePrincipal(),
                        orderId,
                        request != null ? request.note() : null)));
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasAuthority('PERM_FINANCE_WRITE')")
    @Operation(summary = "Cancel an uninvoiced order and restore any deducted stock")
    public ResponseEntity<ApiResponse<SalesOrderDetailResponse>> cancelOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody(required = false) OrderStatusNoteRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                orderService.cancelOrder(
                        tenantContext.requirePrincipal(),
                        orderId,
                        request != null ? request.note() : null)));
    }

    @PostMapping("/{orderId}/invoice")
    @PreAuthorize("hasAuthority('PERM_FINANCE_WRITE')")
    @Operation(summary = "Generate an invoice for an eligible order (idempotent on retries)")
    public ResponseEntity<ApiResponse<InvoiceDetailResponse>> generateInvoice(
            @PathVariable UUID orderId,
            @Valid @RequestBody(required = false) GenerateInvoiceRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                invoiceService.generateFromOrder(
                        tenantContext.requirePrincipal(), orderId, request)));
    }
}

