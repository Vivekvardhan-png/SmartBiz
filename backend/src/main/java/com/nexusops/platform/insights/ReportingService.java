package com.nexusops.platform.insights;

import com.nexusops.platform.common.api.PagedResponse;
import com.nexusops.platform.common.exception.BusinessRuleException;
import com.nexusops.platform.crm.LeadRepository;
import com.nexusops.platform.crm.LeadStatus;
import com.nexusops.platform.finance.Invoice;
import com.nexusops.platform.finance.InvoiceRepository;
import com.nexusops.platform.finance.Payment;
import com.nexusops.platform.finance.PaymentEntryType;
import com.nexusops.platform.finance.PaymentRepository;
import com.nexusops.platform.finance.SalesOrderRepository;
import com.nexusops.platform.finance.SalesOrderStatus;
import com.nexusops.platform.finance.dto.FinanceDtos.InvoiceSummaryResponse;
import com.nexusops.platform.finance.dto.FinanceDtos.PaymentResponse;
import com.nexusops.platform.insights.dto.InsightsDtos.InventoryStockReport;
import com.nexusops.platform.insights.dto.InsightsDtos.InventoryStockRow;
import com.nexusops.platform.insights.dto.InsightsDtos.LeadPipelineReport;
import com.nexusops.platform.insights.dto.InsightsDtos.LeadPipelineRow;
import com.nexusops.platform.insights.dto.InsightsDtos.OrderStatusReportRow;
import com.nexusops.platform.insights.dto.InsightsDtos.OrdersByStatusReport;
import com.nexusops.platform.insights.dto.InsightsDtos.ReceivablesReport;
import com.nexusops.platform.insights.dto.InsightsDtos.SalesOverTimePoint;
import com.nexusops.platform.insights.dto.InsightsDtos.SalesOverTimeReport;
import com.nexusops.platform.inventory.Product;
import com.nexusops.platform.inventory.ProductRepository;
import com.nexusops.platform.inventory.StockMovement;
import com.nexusops.platform.inventory.StockMovementRepository;
import com.nexusops.platform.inventory.StockMovementService;
import com.nexusops.platform.inventory.dto.InventoryDtos.StockMovementResponse;
import com.nexusops.platform.security.TenantPrincipal;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportingService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final InvoiceRepository invoiceRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final PaymentRepository paymentRepository;
    private final LeadRepository leadRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;

    public ReportingService(
            InvoiceRepository invoiceRepository,
            SalesOrderRepository salesOrderRepository,
            PaymentRepository paymentRepository,
            LeadRepository leadRepository,
            ProductRepository productRepository,
            StockMovementRepository movementRepository) {
        this.invoiceRepository = invoiceRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.paymentRepository = paymentRepository;
        this.leadRepository = leadRepository;
        this.productRepository = productRepository;
        this.movementRepository = movementRepository;
    }

    @Transactional(readOnly = true)
    public SalesOverTimeReport getSalesOverTime(
            TenantPrincipal principal,
            LocalDate from,
            LocalDate to) {
        DateRange range = resolveRange(from, to);
        List<InvoiceRepository.DailyRevenueRow> rows = invoiceRepository
                .findDailyRevenueInDateRange(principal.organizationId(), range.start(), range.end());

        BigDecimal totalRevenue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        long totalInvoices = 0L;
        List<SalesOverTimePoint> points = new ArrayList<>(rows.size());

        for (InvoiceRepository.DailyRevenueRow row : rows) {
            BigDecimal rev = row.getRevenue().setScale(2, RoundingMode.HALF_UP);
            totalRevenue = totalRevenue.add(rev);
            totalInvoices += row.getInvoiceCount();
            points.add(new SalesOverTimePoint(row.getIssueDate(), rev, row.getInvoiceCount()));
        }

        return new SalesOverTimeReport(
                range.start(),
                range.end(),
                totalRevenue,
                totalInvoices,
                "Daily sum of issued, partially paid, and paid invoice totals (UTC issue date; excludes VOID and REFUNDED).",
                points
        );
    }

    @Transactional(readOnly = true)
    public OrdersByStatusReport getOrdersByStatus(
            TenantPrincipal principal,
            LocalDate from,
            LocalDate to) {
        DateRange range = resolveRange(from, to);
        Map<SalesOrderStatus, OrderStatusReportRow> map = new EnumMap<>(SalesOrderStatus.class);
        for (SalesOrderStatus status : SalesOrderStatus.values()) {
            map.put(status, new OrderStatusReportRow(status, 0L, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)));
        }

        long totalOrders = 0L;
        BigDecimal totalValue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (SalesOrderRepository.OrderStatusCount row
                : salesOrderRepository.countByStatusInDateRange(principal.organizationId(), range.start(), range.end())) {
            BigDecimal val = row.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
            map.put(row.getStatus(), new OrderStatusReportRow(row.getStatus(), row.getTotal(), val));
            totalOrders += row.getTotal();
            totalValue = totalValue.add(val);
        }

        return new OrdersByStatusReport(
                range.start(),
                range.end(),
                totalOrders,
                totalValue,
                List.copyOf(map.values())
        );
    }

    @Transactional(readOnly = true)
    public ReceivablesReport getReceivables(
            TenantPrincipal principal,
            LocalDate from,
            LocalDate to) {
        DateRange range = resolveRange(from, to);
        UUID orgId = principal.organizationId();

        List<Invoice> outstanding = invoiceRepository.findOutstandingInvoices(orgId);
        BigDecimal outstandingTotal = outstanding.stream()
                .map(Invoice::getOutstandingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        List<InvoiceSummaryResponse> invoiceDtos = outstanding.stream()
                .map(inv -> new InvoiceSummaryResponse(
                        inv.getId(),
                        inv.getInvoiceNumber(),
                        inv.getSalesOrderId(),
                        inv.getCustomerId(),
                        inv.getCustomerName(),
                        inv.getStatus(),
                        inv.getIssueDate(),
                        inv.getDueDate(),
                        inv.getTotalAmount(),
                        inv.getPaidAmount(),
                        inv.getOutstandingBalance(),
                        inv.getCreatedAt()
                ))
                .toList();

        List<Payment> payments = paymentRepository
                .findAllByOrganizationIdAndPaymentDateBetweenOrderByPaymentDateDescCreatedAtDesc(
                        orgId, range.start(), range.end());

        BigDecimal totalPayments = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalRefunds = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<PaymentResponse> paymentDtos = new ArrayList<>(payments.size());

        for (Payment p : payments) {
            if (p.getEntryType() == PaymentEntryType.PAYMENT) {
                totalPayments = totalPayments.add(p.getAmount());
            } else {
                totalRefunds = totalRefunds.add(p.getAmount());
            }
            paymentDtos.add(new PaymentResponse(
                    p.getId(),
                    p.getInvoiceId(),
                    p.getEntryType(),
                    p.getAmount(),
                    p.getPaymentDate(),
                    p.getMethod(),
                    p.getReference(),
                    p.getNotes(),
                    p.getRecordedByName(),
                    p.getCreatedAt(),
                    false
            ));
        }

        return new ReceivablesReport(
                range.start(),
                range.end(),
                outstandingTotal,
                totalPayments.setScale(2, RoundingMode.HALF_UP),
                totalRefunds.setScale(2, RoundingMode.HALF_UP),
                invoiceDtos,
                paymentDtos
        );
    }

    @Transactional(readOnly = true)
    public LeadPipelineReport getLeadPipeline(TenantPrincipal principal) {
        Map<LeadStatus, LeadPipelineRow> map = new EnumMap<>(LeadStatus.class);
        for (LeadStatus status : LeadStatus.values()) {
            map.put(status, new LeadPipelineRow(status, 0L, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)));
        }

        long totalLeads = 0L;
        long openLeads = 0L;
        long convertedLeads = 0L;

        for (LeadRepository.LeadStageCount row
                : leadRepository.countByStageForOrganization(principal.organizationId())) {
            BigDecimal val = row.getTotalValue().setScale(2, RoundingMode.HALF_UP);
            map.put(row.getStatus(), new LeadPipelineRow(row.getStatus(), row.getTotal(), val));
            totalLeads += row.getTotal();
            if (row.getStatus().isOpen()) {
                openLeads += row.getTotal();
            }
            if (row.getStatus() == LeadStatus.CONVERTED) {
                convertedLeads += row.getTotal();
            }
        }

        BigDecimal conversionRate = totalLeads == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(convertedLeads)
                        .multiply(ONE_HUNDRED)
                        .divide(BigDecimal.valueOf(totalLeads), 2, RoundingMode.HALF_UP);

        return new LeadPipelineReport(
                totalLeads,
                openLeads,
                convertedLeads,
                conversionRate,
                List.copyOf(map.values())
        );
    }

    @Transactional(readOnly = true)
    public InventoryStockReport getInventoryStock(
            TenantPrincipal principal,
            boolean lowStockOnly,
            int page,
            int pageSize) {

        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        UUID orgId = principal.organizationId();

        Specification<Product> allActiveSpec = (root, query, cb) -> cb.and(
                cb.equal(root.get("organizationId"), orgId),
                cb.isTrue(root.get("active"))
        );
        List<Product> allActive = productRepository.findAll(allActiveSpec);
        long lowStockCount = allActive.stream().filter(Product::isLowStock).count();
        BigDecimal totalStockValue = allActive.stream()
                .map(p -> p.getStockQuantity().multiply(p.getUnitPrice()).setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        Specification<Product> filteredSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), orgId));
            predicates.add(cb.isTrue(root.get("active")));
            if (lowStockOnly) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("stockQuantity"),
                        root.get("lowStockThreshold")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<Product> pageResult = productRepository.findAll(
                filteredSpec,
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.ASC, "name"))
        );

        List<InventoryStockRow> rows = pageResult.getContent().stream()
                .map(p -> new InventoryStockRow(
                        p.getId(),
                        p.getSku(),
                        p.getName(),
                        p.getCategory(),
                        p.getUnitOfMeasure(),
                        p.getStockQuantity(),
                        p.getLowStockThreshold(),
                        p.isLowStock(),
                        p.getUnitPrice(),
                        p.getStockQuantity().multiply(p.getUnitPrice()).setScale(2, RoundingMode.HALF_UP)
                ))
                .toList();

        return new InventoryStockReport(
                allActive.size(),
                lowStockCount,
                totalStockValue,
                rows,
                safePage,
                safeSize,
                pageResult.getTotalElements(),
                pageResult.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<StockMovementResponse> getStockMovementsReport(
            TenantPrincipal principal,
            LocalDate from,
            LocalDate to,
            int page,
            int pageSize) {

        DateRange range = resolveRange(from, to);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);

        Instant fromInstant = range.start().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = range.end().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Page<StockMovement> result = movementRepository.findPageByOrganizationAndCreatedRange(
                principal.organizationId(),
                fromInstant,
                toExclusive,
                PageRequest.of(safePage - 1, safeSize)
        );

        return PagedResponse.of(
                result.getContent().stream()
                        .map(m -> StockMovementService.toResponse(m, false))
                        .toList(),
                safePage,
                safeSize,
                result.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public String exportReportCsv(
            TenantPrincipal principal,
            String reportType,
            LocalDate from,
            LocalDate to) {

        return switch (reportType) {
            case "sales-over-time" -> {
                SalesOverTimeReport r = getSalesOverTime(principal, from, to);
                yield CsvSanitizer.toCsv(
                        List.of("Date", "Invoice Count", "Revenue"),
                        r.series().stream()
                                .map(p -> List.of(
                                        p.date().toString(),
                                        String.valueOf(p.invoiceCount()),
                                        p.revenue().toPlainString()))
                                .toList());
            }
            case "outstanding-invoices" -> {
                ReceivablesReport r = getReceivables(principal, from, to);
                yield CsvSanitizer.toCsv(
                        List.of("Invoice Number", "Customer", "Status", "Issue Date", "Total", "Paid", "Outstanding"),
                        r.outstandingInvoices().stream()
                                .map(inv -> List.of(
                                        CsvSanitizer.sanitizeTextCell(inv.invoiceNumber()),
                                        CsvSanitizer.sanitizeTextCell(inv.customerName()),
                                        inv.status().name(),
                                        inv.issueDate().toString(),
                                        inv.totalAmount().toPlainString(),
                                        inv.paidAmount().toPlainString(),
                                        inv.outstandingBalance().toPlainString()))
                                .toList());
            }
            case "payments" -> {
                ReceivablesReport r = getReceivables(principal, from, to);
                yield CsvSanitizer.toCsv(
                        List.of("Payment Date", "Type", "Method", "Amount", "Reference", "Recorded By"),
                        r.paymentsInPeriod().stream()
                                .map(p -> List.of(
                                        p.paymentDate().toString(),
                                        p.entryType().name(),
                                        p.method().name(),
                                        p.amount().toPlainString(),
                                        CsvSanitizer.sanitizeTextCell(p.reference()),
                                        CsvSanitizer.sanitizeTextCell(p.recordedByName())))
                                .toList());
            }
            case "lead-pipeline" -> {
                LeadPipelineReport r = getLeadPipeline(principal);
                yield CsvSanitizer.toCsv(
                        List.of("Stage", "Lead Count", "Estimated Value"),
                        r.stages().stream()
                                .map(s -> List.of(
                                        s.status().name(),
                                        String.valueOf(s.count()),
                                        s.estimatedValue().toPlainString()))
                                .toList());
            }
            case "inventory-stock" -> {
                InventoryStockReport r = getInventoryStock(principal, false, 1, MAX_PAGE_SIZE);
                yield CsvSanitizer.toCsv(
                        List.of("SKU", "Product Name", "Category", "UOM", "Stock Qty", "Threshold", "Low Stock", "Unit Price", "Stock Value"),
                        r.items().stream()
                                .map(item -> List.of(
                                        CsvSanitizer.sanitizeTextCell(item.sku()),
                                        CsvSanitizer.sanitizeTextCell(item.name()),
                                        CsvSanitizer.sanitizeTextCell(item.category()),
                                        CsvSanitizer.sanitizeTextCell(item.unitOfMeasure()),
                                        item.stockQuantity().toPlainString(),
                                        item.lowStockThreshold().toPlainString(),
                                        String.valueOf(item.lowStock()),
                                        item.unitPrice().toPlainString(),
                                        item.stockValue().toPlainString()))
                                .toList());
            }
            case "stock-movements" -> {
                DateRange range = resolveRange(from, to);
                List<StockMovement> all = movementRepository.findAllByOrganizationAndCreatedRange(
                        principal.organizationId(),
                        range.start().atStartOfDay(ZoneOffset.UTC).toInstant(),
                        range.end().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
                yield CsvSanitizer.toCsv(
                        List.of("Timestamp", "SKU", "Product", "Type", "Delta", "Before", "After", "Actor", "Reason", "Reference"),
                        all.stream()
                                .map(m -> List.of(
                                        m.getCreatedAt().toString(),
                                        CsvSanitizer.sanitizeTextCell(m.getProduct().getSku()),
                                        CsvSanitizer.sanitizeTextCell(m.getProduct().getName()),
                                        m.getMovementType().name(),
                                        m.getQuantityDelta().toPlainString(),
                                        m.getQuantityBefore().toPlainString(),
                                        m.getQuantityAfter().toPlainString(),
                                        CsvSanitizer.sanitizeTextCell(m.getActorName()),
                                        CsvSanitizer.sanitizeTextCell(m.getReason()),
                                        CsvSanitizer.sanitizeTextCell(m.getReference())))
                                .toList());
            }
            default -> throw new BusinessRuleException(
                    "Unsupported CSV export reportType: " + reportType);
        };
    }

    static DateRange resolveRange(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.minusDays(29);
        if (start.isAfter(end)) {
            throw new BusinessRuleException("Report 'from' date must not be after 'to' date.");
        }
        return new DateRange(start, end);
    }

    record DateRange(LocalDate start, LocalDate end) {}
}

