package com.nexusops.platform.insights.dto;

import com.nexusops.platform.crm.LeadStatus;
import com.nexusops.platform.finance.SalesOrderStatus;
import com.nexusops.platform.finance.dto.FinanceDtos.InvoiceSummaryResponse;
import com.nexusops.platform.finance.dto.FinanceDtos.PaymentResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class InsightsDtos {

    private InsightsDtos() {
    }

    public record SalesOverTimePoint(
            LocalDate date,
            BigDecimal revenue,
            long invoiceCount
    ) {}

    public record SalesOverTimeReport(
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal totalRevenue,
            long totalInvoices,
            String definition,
            List<SalesOverTimePoint> series
    ) {}

    public record OrderStatusReportRow(
            SalesOrderStatus status,
            long count,
            BigDecimal totalAmount
    ) {}

    public record OrdersByStatusReport(
            LocalDate periodStart,
            LocalDate periodEnd,
            long totalOrders,
            BigDecimal totalValue,
            List<OrderStatusReportRow> rows
    ) {}

    public record ReceivablesReport(
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal outstandingTotal,
            BigDecimal totalPaymentsInPeriod,
            BigDecimal totalRefundsInPeriod,
            List<InvoiceSummaryResponse> outstandingInvoices,
            List<PaymentResponse> paymentsInPeriod
    ) {}

    public record LeadPipelineRow(
            LeadStatus status,
            long count,
            BigDecimal estimatedValue
    ) {}

    public record LeadPipelineReport(
            long totalLeads,
            long openLeads,
            long convertedLeads,
            BigDecimal conversionRatePercent,
            List<LeadPipelineRow> stages
    ) {}

    public record InventoryStockRow(
            UUID id,
            String sku,
            String name,
            String category,
            String unitOfMeasure,
            BigDecimal stockQuantity,
            BigDecimal lowStockThreshold,
            boolean lowStock,
            BigDecimal unitPrice,
            BigDecimal stockValue
    ) {}

    public record InventoryStockReport(
            long totalSkus,
            long lowStockCount,
            BigDecimal totalStockValue,
            List<InventoryStockRow> items,
            int page,
            int pageSize,
            long totalItems,
            int totalPages
    ) {}

    public record ForecastEvaluationDto(
            int holdoutDays,
            BigDecimal baselineMae,
            BigDecimal baselineWape,
            BigDecimal modelMae,
            BigDecimal modelWape,
            String selectedModel
    ) {}

    public record ForecastPointDto(
            LocalDate date,
            BigDecimal predictedValue,
            BigDecimal baselineValue
    ) {}

    public record ForecastResponse(
            String status,
            boolean serviceAvailable,
            String predictionTarget,
            int horizonDays,
            int actualDays,
            int nonZeroDays,
            int minimumDaysRequired,
            String message,
            ForecastEvaluationDto evaluation,
            List<ForecastPointDto> predictions,
            BigDecimal projectedTotal,
            LocalDate estimatedStockoutDate,
            List<String> limitations
    ) {}
}

