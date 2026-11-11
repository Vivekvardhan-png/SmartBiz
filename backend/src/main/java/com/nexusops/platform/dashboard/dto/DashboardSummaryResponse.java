package com.nexusops.platform.dashboard.dto;

import com.nexusops.platform.crm.LeadStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DashboardSummaryResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        long totalCustomers,
        long openLeads,
        Map<LeadStatus, Long> leadsByStage,
        DeferredMetric<Map<String, Long>> salesOrdersByStatus,
        DeferredMetric<BigDecimal> periodRevenue,
        DeferredMetric<BigDecimal> outstandingInvoiceBalance,
        DeferredMetric<Long> lowStockProductsCount,
        List<MetricDefinition> metricDefinitions
) {
    public record DeferredMetric<T>(
            T value,
            boolean moduleAvailable,
            String note
    ) {}

    public record MetricDefinition(
            String metric,
            String formula,
            String dateScope,
            boolean active
    ) {}
}

