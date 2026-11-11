package com.nexusops.platform.insights;

import com.nexusops.platform.common.api.ApiResponse;
import com.nexusops.platform.common.api.PagedResponse;
import com.nexusops.platform.insights.dto.InsightsDtos.ForecastResponse;
import com.nexusops.platform.insights.dto.InsightsDtos.InventoryStockReport;
import com.nexusops.platform.insights.dto.InsightsDtos.LeadPipelineReport;
import com.nexusops.platform.insights.dto.InsightsDtos.OrdersByStatusReport;
import com.nexusops.platform.insights.dto.InsightsDtos.ReceivablesReport;
import com.nexusops.platform.insights.dto.InsightsDtos.SalesOverTimeReport;
import com.nexusops.platform.inventory.dto.InventoryDtos.StockMovementResponse;
import com.nexusops.platform.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/insights")
@Tag(name = "Insights & Forecasting", description = "Organization-scoped reports, safe CSV exports, and demand forecasting.")
public class InsightsController {

    private final ReportingService reportingService;
    private final ForecastingService forecastingService;
    private final TenantContext tenantContext;

    public InsightsController(
            ReportingService reportingService,
            ForecastingService forecastingService,
            TenantContext tenantContext) {
        this.reportingService = reportingService;
        this.forecastingService = forecastingService;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/reports/sales-over-time")
    @PreAuthorize("hasAuthority('PERM_INSIGHTS_READ')")
    @Operation(summary = "Daily sales report from issued non-voided invoices")
    public ResponseEntity<ApiResponse<SalesOverTimeReport>> salesOverTime(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.of(
                reportingService.getSalesOverTime(tenantContext.requirePrincipal(), from, to)));
    }

    @GetMapping("/reports/orders-by-status")
    @PreAuthorize("hasAuthority('PERM_INSIGHTS_READ')")
    @Operation(summary = "Sales orders breakdown by status")
    public ResponseEntity<ApiResponse<OrdersByStatusReport>> ordersByStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.of(
                reportingService.getOrdersByStatus(tenantContext.requirePrincipal(), from, to)));
    }

    @GetMapping("/reports/receivables")
    @PreAuthorize("hasAuthority('PERM_INSIGHTS_READ')")
    @Operation(summary = "Outstanding invoices and recorded payments/refunds")
    public ResponseEntity<ApiResponse<ReceivablesReport>> receivables(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.of(
                reportingService.getReceivables(tenantContext.requirePrincipal(), from, to)));
    }

    @GetMapping("/reports/lead-pipeline")
    @PreAuthorize("hasAuthority('PERM_INSIGHTS_READ')")
    @Operation(summary = "CRM lead pipeline and conversion rate")
    public ResponseEntity<ApiResponse<LeadPipelineReport>> leadPipeline() {
        return ResponseEntity.ok(ApiResponse.of(
                reportingService.getLeadPipeline(tenantContext.requirePrincipal())));
    }

    @GetMapping("/reports/inventory-stock")
    @PreAuthorize("hasAuthority('PERM_INSIGHTS_READ')")
    @Operation(summary = "Current stock, low-stock items, and valuation")
    public ResponseEntity<ApiResponse<InventoryStockReport>> inventoryStock(
            @RequestParam(defaultValue = "false") boolean lowStockOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(ApiResponse.of(
                reportingService.getInventoryStock(
                        tenantContext.requirePrincipal(), lowStockOnly, page, pageSize)));
    }

    @GetMapping("/reports/stock-movements")
    @PreAuthorize("hasAuthority('PERM_INSIGHTS_READ')")
    @Operation(summary = "Paginated stock movement history report")
    public ResponseEntity<PagedResponse<StockMovementResponse>> stockMovements(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(reportingService.getStockMovementsReport(
                tenantContext.requirePrincipal(), from, to, page, pageSize));
    }

    @GetMapping("/reports/export/{reportType}")
    @PreAuthorize("hasAuthority('PERM_INSIGHTS_READ')")
    @Operation(summary = "Export an organization-scoped CSV report safely")
    public ResponseEntity<byte[]> exportCsv(
            @PathVariable String reportType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String csv = reportingService.exportReportCsv(
                tenantContext.requirePrincipal(), reportType, from, to);
        String filename = reportType.replaceAll("[^a-zA-Z0-9-]", "") + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/forecast/sales")
    @PreAuthorize("hasAuthority('PERM_INSIGHTS_READ')")
    @Operation(summary = "Sales revenue forecast (gracefully degrades if forecasting service is unavailable)")
    public ResponseEntity<ApiResponse<ForecastResponse>> forecastSales(
            @RequestParam(defaultValue = "14") int horizonDays) {
        return ResponseEntity.ok(ApiResponse.of(
                forecastingService.forecastSales(tenantContext.requirePrincipal(), horizonDays)));
    }

    @GetMapping("/forecast/inventory/{productId}")
    @PreAuthorize("hasAuthority('PERM_INSIGHTS_READ')")
    @Operation(summary = "Inventory unit demand forecast for a product")
    public ResponseEntity<ApiResponse<ForecastResponse>> forecastInventory(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "14") int horizonDays) {
        return ResponseEntity.ok(ApiResponse.of(
                forecastingService.forecastInventoryDemand(
                        tenantContext.requirePrincipal(), productId, horizonDays)));
    }
}

