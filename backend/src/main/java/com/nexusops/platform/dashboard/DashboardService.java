package com.nexusops.platform.dashboard;

import com.nexusops.platform.common.exception.BusinessRuleException;
import com.nexusops.platform.crm.CustomerRepository;
import com.nexusops.platform.crm.LeadRepository;
import com.nexusops.platform.crm.LeadStatus;
import com.nexusops.platform.dashboard.dto.DashboardSummaryResponse;
import com.nexusops.platform.dashboard.dto.DashboardSummaryResponse.DeferredMetric;
import com.nexusops.platform.dashboard.dto.DashboardSummaryResponse.MetricDefinition;
import com.nexusops.platform.finance.InvoiceRepository;
import com.nexusops.platform.finance.SalesOrderRepository;
import com.nexusops.platform.finance.SalesOrderStatus;
import com.nexusops.platform.identity.Permission;
import com.nexusops.platform.inventory.ProductRepository;
import com.nexusops.platform.security.TenantPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DashboardService {

    private final CustomerRepository customerRepository;
    private final LeadRepository leadRepository;
    private final ProductRepository productRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final InvoiceRepository invoiceRepository;
    private final Clock clock;

    public DashboardService(
            CustomerRepository customerRepository,
            LeadRepository leadRepository,
            ProductRepository productRepository,
            SalesOrderRepository salesOrderRepository,
            InvoiceRepository invoiceRepository) {
        this.customerRepository = customerRepository;
        this.leadRepository = leadRepository;
        this.productRepository = productRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.invoiceRepository = invoiceRepository;
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

        boolean canViewInventory = principal.hasPermission(Permission.INVENTORY_READ);
        long lowStockProductsCount = canViewInventory ? productRepository.countLowStockProducts(orgId) : 0L;

        Map<LeadStatus, Long> leadsByStage = new EnumMap<>(LeadStatus.class);
        for (LeadStatus status : LeadStatus.values()) {
            leadsByStage.put(status, 0L);
        }
        for (LeadRepository.LeadStageCount row : leadRepository.countByStageForOrganization(orgId)) {
            leadsByStage.put(row.getStatus(), row.getTotal());
        }

        boolean canViewFinance = principal.hasPermission(Permission.FINANCE_READ);
        Map<String, Long> ordersByStatus = new LinkedHashMap<>();
        BigDecimal periodRevenue = null;
        BigDecimal outstandingBalance = null;
        if (canViewFinance) {
            for (SalesOrderStatus status : SalesOrderStatus.values()) {
                ordersByStatus.put(status.name(), 0L);
            }
            for (SalesOrderRepository.OrderStatusCount row
                    : salesOrderRepository.countByStatusInDateRange(orgId, periodStart, periodEnd)) {
                ordersByStatus.put(row.getStatus().name(), row.getTotal());
            }

            periodRevenue = invoiceRepository
                    .sumRevenueInDateRange(orgId, periodStart, periodEnd)
                    .setScale(2, RoundingMode.HALF_UP);
            outstandingBalance = invoiceRepository
                    .sumOutstandingBalance(orgId)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return new DashboardSummaryResponse(
                periodStart,
                periodEnd,
                totalCustomers,
                openLeads,
                leadsByStage,
                new DeferredMetric<>(
                        ordersByStatus,
                        canViewFinance,
                        canViewFinance
                                ? "Sales orders grouped by status within selected UTC period."
                                : "Requires FINANCE_READ permission."
                ),
                new DeferredMetric<>(
                        periodRevenue,
                        canViewFinance,
                        canViewFinance
                                ? "Sum of non-voided, non-refunded invoice totals issued in selected UTC period."
                                : "Requires FINANCE_READ permission."
                ),
                new DeferredMetric<>(
                        outstandingBalance,
                        canViewFinance,
                        canViewFinance
                                ? "Total remaining balance across ISSUED and PARTIALLY_PAID invoices."
                                : "Requires FINANCE_READ permission."
                ),
                new DeferredMetric<>(
                        lowStockProductsCount,
                        canViewInventory,
                        canViewInventory
                                ? "Active products with stock quantity at or below threshold."
                                : "Requires INVENTORY_READ permission."
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
                        true
                ),
                new MetricDefinition(
                        "periodRevenue",
                        "SUM(invoices.total_amount) for ISSUED/PARTIALLY_PAID/PAID invoices",
                        "Selected UTC date range",
                        true
                ),
                new MetricDefinition(
                        "outstandingInvoiceBalance",
                        "SUM(invoices.total_amount - invoices.paid_amount) for ISSUED/PARTIALLY_PAID invoices",
                        "Current snapshot",
                        true
                ),
                new MetricDefinition(
                        "lowStockProductsCount",
                        "COUNT(products.id) where active = true AND stock_quantity <= low_stock_threshold",
                        "Current snapshot",
                        true
                )
        );
    }
}
