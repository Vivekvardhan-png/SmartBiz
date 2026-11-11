package com.nexusops.platform.dashboard;

import com.nexusops.platform.common.exception.BusinessRuleException;
import com.nexusops.platform.crm.CustomerRepository;
import com.nexusops.platform.crm.LeadRepository;
import com.nexusops.platform.crm.LeadStatus;
import com.nexusops.platform.dashboard.dto.DashboardSummaryResponse;
import com.nexusops.platform.dashboard.dto.DashboardSummaryResponse.DeferredMetric;
import com.nexusops.platform.dashboard.dto.DashboardSummaryResponse.MetricDefinition;
import com.nexusops.platform.security.TenantPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DashboardService {

    private final CustomerRepository customerRepository;
    private final LeadRepository leadRepository;
    private final Clock clock;

    public DashboardService(
            CustomerRepository customerRepository,
            LeadRepository leadRepository) {
        this.customerRepository = customerRepository;
        this.leadRepository = leadRepository;
        this.clock = Clock.systemUTC();
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(
            TenantPrincipal principal,
            LocalDate from,
            LocalDate to) {

        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate periodEnd = to != null ? to : today;
        LocalDate periodStart = from != null ? from : periodEnd.minusDays(29);

        if (periodStart.isAfter(periodEnd)) {
            throw new BusinessRuleException(
                    "Dashboard 'from' date must not be after 'to' date.");
        }

        UUID orgId = principal.organizationId();

        long totalCustomers = customerRepository.countByOrganizationId(orgId);
        long openLeads = leadRepository.countByOrganizationIdAndStatusIn(
                orgId, LeadStatus.OPEN_STATUSES);

        Map<LeadStatus, Long> leadsByStage = new EnumMap<>(LeadStatus.class);
        for (LeadStatus status : LeadStatus.values()) {
            leadsByStage.put(status, 0L);
        }
        for (LeadRepository.LeadStageCount row : leadRepository.countByStageForOrganization(orgId)) {
            leadsByStage.put(row.getStatus(), row.getTotal());
        }

        return new DashboardSummaryResponse(
                periodStart,
                periodEnd,
                totalCustomers,
                openLeads,
                leadsByStage,
                new DeferredMetric<>(
                        Map.of(),
                        false,
                        "Sales orders module is scheduled for Prompt 05."
                ),
                new DeferredMetric<>(
                        new BigDecimal("0.00"),
                        false,
                        "Revenue reporting from issued non-voided invoices is scheduled for Prompt 05."
                ),
                new DeferredMetric<>(
                        new BigDecimal("0.00"),
                        false,
                        "Outstanding invoice balances are scheduled for Prompt 05."
                ),
                new DeferredMetric<>(
                        0L,
                        false,
                        "Inventory stock thresholds are scheduled for Prompt 04."
                ),
                buildDefinitions()
        );
    }

    private static List<MetricDefinition> buildDefinitions() {
        return List.of(
                new MetricDefinition(
                        "totalCustomers",
                        "COUNT(customers.id) scoped to active organization",
                        "All-time",
                        true
                ),
                new MetricDefinition(
                        "openLeads",
                        "COUNT(leads.id) in statuses NEW, CONTACTED, QUALIFIED, PROPOSAL",
                        "All-time",
                        true
                ),
                new MetricDefinition(
                        "leadsByStage",
                        "COUNT(leads.id) grouped by pipeline status",
                        "All-time",
                        true
                ),
                new MetricDefinition(
                        "salesOrdersByStatus",
                        "COUNT(sales_orders.id) grouped by status",
                        "Selected UTC date range",
                        false
                ),
                new MetricDefinition(
                        "periodRevenue",
                        "SUM(invoices.total_amount) for issued non-voided invoices excluding CANCELLED orders",
                        "Selected UTC date range",
                        false
                ),
                new MetricDefinition(
                        "outstandingInvoiceBalance",
                        "SUM(invoices.total_amount - COALESCE(SUM(payments.amount), 0)) for non-voided invoices",
                        "Current snapshot",
                        false
                ),
                new MetricDefinition(
                        "lowStockProductsCount",
                        "COUNT(products.id) where active = true AND stock_quantity <= low_stock_threshold",
                        "Current snapshot",
                        false
                )
        );
    }
}

