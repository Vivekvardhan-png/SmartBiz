package com.nexusops.platform.finance;

import com.nexusops.platform.common.api.ApiResponse;
import com.nexusops.platform.common.api.PagedResponse;
import com.nexusops.platform.finance.dto.FinanceDtos.InvoiceDetailResponse;
import com.nexusops.platform.finance.dto.FinanceDtos.InvoiceSummaryResponse;
import com.nexusops.platform.finance.dto.FinanceDtos.PaymentResponse;
import com.nexusops.platform.finance.dto.FinanceDtos.RecordPaymentRequest;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/finance/invoices")
@Tag(name = "Invoices & Payments", description = "Organization-scoped invoices and append-only payment/refund ledger.")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final TenantContext tenantContext;

    public InvoiceController(
            InvoiceService invoiceService,
            PaymentService paymentService,
            TenantContext tenantContext) {
        this.invoiceService = invoiceService;
        this.paymentService = paymentService;
        this.tenantContext = tenantContext;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_FINANCE_READ')")
    @Operation(summary = "List and filter invoices")
    public ResponseEntity<PagedResponse<InvoiceSummaryResponse>> listInvoices(
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResponseEntity.ok(invoiceService.listInvoices(
                tenantContext.requirePrincipal(), status, page, pageSize));
    }

    @GetMapping("/{invoiceId}")
    @PreAuthorize("hasAuthority('PERM_FINANCE_READ')")
    @Operation(summary = "Get readable invoice details and payment history")
    public ResponseEntity<ApiResponse<InvoiceDetailResponse>> getInvoice(@PathVariable UUID invoiceId) {
        return ResponseEntity.ok(ApiResponse.of(
                invoiceService.getInvoice(tenantContext.requirePrincipal(), invoiceId)));
    }

    @GetMapping("/{invoiceId}/payments")
    @PreAuthorize("hasAuthority('PERM_FINANCE_READ')")
    @Operation(summary = "List payment and refund ledger entries for an invoice")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> listPayments(@PathVariable UUID invoiceId) {
        return ResponseEntity.ok(ApiResponse.of(
                paymentService.listPayments(tenantContext.requirePrincipal(), invoiceId)));
    }

    @PostMapping("/{invoiceId}/payments")
    @PreAuthorize("hasAuthority('PERM_FINANCE_WRITE')")
    @Operation(summary = "Record a manually confirmed payment or refund on an invoice")
    public ResponseEntity<ApiResponse<PaymentResponse>> recordPayment(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody RecordPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(
                        paymentService.recordEntry(tenantContext.requirePrincipal(), invoiceId, request)));
    }
}

