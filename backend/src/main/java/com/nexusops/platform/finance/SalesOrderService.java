package com.nexusops.platform.finance;

import com.nexusops.platform.common.api.PagedResponse;
import com.nexusops.platform.common.exception.BusinessRuleException;
import com.nexusops.platform.common.exception.ResourceNotFoundException;
import com.nexusops.platform.crm.Customer;
import com.nexusops.platform.crm.CustomerRepository;
import com.nexusops.platform.finance.dto.FinanceDtos.CreateSalesOrderRequest;
import com.nexusops.platform.finance.dto.FinanceDtos.OrderItemInput;
import com.nexusops.platform.finance.dto.FinanceDtos.SalesOrderDetailResponse;
import com.nexusops.platform.finance.dto.FinanceDtos.SalesOrderHistoryResponse;
import com.nexusops.platform.finance.dto.FinanceDtos.SalesOrderItemResponse;
import com.nexusops.platform.finance.dto.FinanceDtos.SalesOrderSummaryResponse;
import com.nexusops.platform.inventory.Product;
import com.nexusops.platform.inventory.ProductRepository;
import com.nexusops.platform.inventory.StockMovementService;
import com.nexusops.platform.inventory.StockMovementType;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SalesOrderService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final SalesOrderRepository orderRepository;
    private final SalesOrderHistoryRepository historyRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final InvoiceRepository invoiceRepository;
    private final StockMovementService stockMovementService;

    public SalesOrderService(
            SalesOrderRepository orderRepository,
            SalesOrderHistoryRepository historyRepository,
            CustomerRepository customerRepository,
            ProductRepository productRepository,
            InvoiceRepository invoiceRepository,
            StockMovementService stockMovementService) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.invoiceRepository = invoiceRepository;
        this.stockMovementService = stockMovementService;
    }

    @Transactional(readOnly = true)
    public PagedResponse<SalesOrderSummaryResponse> listOrders(
            TenantPrincipal principal,
            SalesOrderStatus status,
            UUID customerId,
            int page,
            int pageSize) {

        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);

        Specification<SalesOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), principal.organizationId()));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<SalesOrder> result = orderRepository.findAll(
                spec,
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        List<SalesOrderSummaryResponse> summaries = result.getContent().stream()
                .map(order -> {
                    String customerName = customerRepository
                            .findByIdAndOrganizationId(order.getCustomerId(), principal.organizationId())
                            .map(Customer::getName)
                            .orElse("Customer");
                    boolean hasInvoice = invoiceRepository.existsByOrganizationIdAndSalesOrderId(
                            principal.organizationId(), order.getId());
                    return toSummary(order, customerName, hasInvoice);
                })
                .toList();

        return PagedResponse.of(summaries, safePage, safeSize, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public SalesOrderDetailResponse getOrder(TenantPrincipal principal, UUID orderId) {
        SalesOrder order = orderRepository
                .findWithItemsByIdAndOrganizationId(orderId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("SalesOrder", orderId));
        return buildDetailResponse(principal, order);
    }

    @Transactional
    public SalesOrderDetailResponse createOrder(
            TenantPrincipal principal,
            CreateSalesOrderRequest request) {

        UUID orgId = principal.organizationId();
        Customer customer = customerRepository
                .findByIdAndOrganizationId(request.customerId(), orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));

        LocalDate orderDate = request.orderDate() != null
                ? request.orderDate()
                : LocalDate.now(ZoneOffset.UTC);

        String orderNumber = generateUniqueOrderNumber(orgId, orderDate);
        SalesOrder order = new SalesOrder(
                orgId,
                orderNumber,
                customer.getId(),
                orderDate,
                principal.userId());
        order.setNotes(trimToNull(request.notes()));

        // Consolidate duplicate product lines cleanly
        Map<UUID, BigDecimal> consolidated = new LinkedHashMap<>();
        for (OrderItemInput item : request.items()) {
            consolidated.merge(
                    item.productId(),
                    item.quantity().setScale(3, RoundingMode.HALF_UP),
                    BigDecimal::add);
        }

        BigDecimal subtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (Map.Entry<UUID, BigDecimal> entry : consolidated.entrySet()) {
            Product product = productRepository
                    .findByIdAndOrganizationId(entry.getKey(), orgId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product", entry.getKey()));

            if (!product.isActive()) {
                throw new BusinessRuleException(
                        "Inactive product '" + product.getSku() + "' cannot be added to an order.");
            }

            BigDecimal qty = entry.getValue();
            if (product.getStockQuantity().compareTo(qty) < 0) {
                throw new BusinessRuleException(
                        "Insufficient available stock for SKU '" + product.getSku()
                                + "': available " + product.getStockQuantity()
                                + ", requested " + qty + ".");
            }

            BigDecimal unitPrice = product.getUnitPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);

            order.addItem(new SalesOrderItem(
                    orgId,
                    product.getId(),
                    product.getSku(),
                    product.getName(),
                    product.getUnitOfMeasure(),
                    qty,
                    unitPrice,
                    lineTotal
            ));
        }

        BigDecimal discount = request.discountAmount() != null
                ? request.discountAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (discount.compareTo(subtotal) > 0) {
            throw new BusinessRuleException(
                    "Order discount (" + discount + ") cannot exceed subtotal (" + subtotal + ").");
        }

        BigDecimal taxRate = request.taxRatePercent() != null
                ? request.taxRatePercent().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal taxableBase = subtotal.subtract(discount);
        BigDecimal taxAmount = taxableBase
                .multiply(taxRate)
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal total = taxableBase.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

        order.setSubtotalAmount(subtotal);
        order.setDiscountAmount(discount);
        order.setTaxRatePercent(taxRate);
        order.setTaxAmount(taxAmount);
        order.setTotalAmount(total);

        SalesOrder saved = orderRepository.saveAndFlush(order);
        recordHistory(orgId, saved.getId(), null, SalesOrderStatus.DRAFT, "Order created", principal);

        if (Boolean.TRUE.equals(request.confirmImmediately())) {
            confirmOrderInternal(principal, saved, "Order confirmed upon creation");
        }

        return buildDetailResponse(principal, saved);
    }

    @Transactional
    public SalesOrderDetailResponse confirmOrder(
            TenantPrincipal principal,
            UUID orderId,
            String note) {

        SalesOrder order = orderRepository
                .findForUpdateByIdAndOrganizationId(orderId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("SalesOrder", orderId));

        if (order.getStatus() == SalesOrderStatus.CONFIRMED && order.isStockDeducted()) {
            return buildDetailResponse(principal, order);
        }

        confirmOrderInternal(principal, order, trimToNull(note) != null ? note.trim() : "Order confirmed");
        return buildDetailResponse(principal, order);
    }

    @Transactional
    public SalesOrderDetailResponse fulfillOrder(
            TenantPrincipal principal,
            UUID orderId,
            String note) {

        SalesOrder order = orderRepository
                .findForUpdateByIdAndOrganizationId(orderId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("SalesOrder", orderId));

        if (order.getStatus() == SalesOrderStatus.FULFILLED) {
            return buildDetailResponse(principal, order);
        }
        if (order.getStatus() != SalesOrderStatus.CONFIRMED) {
            throw new BusinessRuleException(
                    "Only CONFIRMED orders can be fulfilled (current status: " + order.getStatus() + ").");
        }

        order.setStatus(SalesOrderStatus.FULFILLED);
        recordHistory(
                principal.organizationId(),
                order.getId(),
                SalesOrderStatus.CONFIRMED,
                SalesOrderStatus.FULFILLED,
                trimToNull(note) != null ? note.trim() : "Order fulfilled",
                principal);

        return buildDetailResponse(principal, order);
    }

    @Transactional
    public SalesOrderDetailResponse cancelOrder(
            TenantPrincipal principal,
            UUID orderId,
            String note) {

        UUID orgId = principal.organizationId();
        SalesOrder order = orderRepository
                .findForUpdateByIdAndOrganizationId(orderId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("SalesOrder", orderId));

        if (order.getStatus() == SalesOrderStatus.CANCELLED) {
            return buildDetailResponse(principal, order);
        }
        if (order.getStatus() == SalesOrderStatus.FULFILLED) {
            throw new BusinessRuleException("Fulfilled orders cannot be cancelled.");
        }
        if (invoiceRepository.existsByOrganizationIdAndSalesOrderId(orgId, order.getId())) {
            throw new BusinessRuleException(
                    "Cannot cancel an order that already has an invoice.");
        }

        SalesOrderStatus previousStatus = order.getStatus();
        if (order.isStockDeducted()) {
            String actorName = resolveActorName(principal);
            for (SalesOrderItem item : order.getItems()) {
                stockMovementService.applyMovement(
                        orgId,
                        item.getProductId(),
                        null,
                        StockMovementType.RETURN_IN,
                        item.getQuantity(),
                        "Restock from cancelled sales order " + order.getOrderNumber(),
                        order.getOrderNumber(),
                        "order-cancel:" + order.getId() + ":" + item.getId(),
                        principal.userId(),
                        actorName
                );
            }
            order.setStockDeducted(false);
        }

        order.setStatus(SalesOrderStatus.CANCELLED);
        recordHistory(
                orgId,
                order.getId(),
                previousStatus,
                SalesOrderStatus.CANCELLED,
                trimToNull(note) != null ? note.trim() : "Order cancelled",
                principal);

        return buildDetailResponse(principal, order);
    }

    private void confirmOrderInternal(TenantPrincipal principal, SalesOrder order, String note) {
        if (order.getStatus() != SalesOrderStatus.DRAFT) {
            throw new BusinessRuleException(
                    "Only DRAFT orders can be confirmed (current status: " + order.getStatus() + ").");
        }

        UUID orgId = principal.organizationId();
        String actorName = resolveActorName(principal);
        for (SalesOrderItem item : order.getItems()) {
            stockMovementService.applyMovement(
                    orgId,
                    item.getProductId(),
                    null,
                    StockMovementType.ORDER_DEDUCTION,
                    item.getQuantity(),
                    "Sales order deduction " + order.getOrderNumber(),
                    order.getOrderNumber(),
                    "order-confirm:" + order.getId() + ":" + item.getId(),
                    principal.userId(),
                    actorName
            );
        }

        order.setStockDeducted(true);
        order.setStatus(SalesOrderStatus.CONFIRMED);
        recordHistory(orgId, order.getId(), SalesOrderStatus.DRAFT, SalesOrderStatus.CONFIRMED, note, principal);
    }

    private SalesOrderDetailResponse buildDetailResponse(TenantPrincipal principal, SalesOrder order) {
        UUID orgId = principal.organizationId();
        Customer customer = customerRepository
                .findByIdAndOrganizationId(order.getCustomerId(), orgId)
                .orElse(null);

        List<SalesOrderItemResponse> itemResponses = order.getItems().stream()
                .map(i -> new SalesOrderItemResponse(
                        i.getId(),
                        i.getProductId(),
                        i.getProductSku(),
                        i.getProductName(),
                        i.getUnitOfMeasure(),
                        i.getQuantity(),
                        i.getUnitPrice(),
                        i.getLineTotal()
                ))
                .toList();

        List<SalesOrderHistoryResponse> history = historyRepository
                .findAllByOrganizationIdAndSalesOrderIdOrderByCreatedAtDesc(orgId, order.getId())
                .stream()
                .map(h -> new SalesOrderHistoryResponse(
                        h.getId(),
                        h.getFromStatus(),
                        h.getToStatus(),
                        h.getNote(),
                        h.getActorName(),
                        h.getCreatedAt()
                ))
                .toList();

        UUID invoiceId = invoiceRepository
                .findWithItemsByOrganizationIdAndSalesOrderId(orgId, order.getId())
                .map(Invoice::getId)
                .orElse(null);

        return new SalesOrderDetailResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerId(),
                customer != null ? customer.getName() : "Customer",
                customer != null ? customer.getEmail() : null,
                order.getStatus(),
                order.getOrderDate(),
                order.getSubtotalAmount(),
                order.getDiscountAmount(),
                order.getTaxRatePercent(),
                order.getTaxAmount(),
                order.getTotalAmount(),
                order.isStockDeducted(),
                order.getNotes(),
                itemResponses,
                history,
                invoiceId,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private void recordHistory(
            UUID orgId,
            UUID orderId,
            SalesOrderStatus fromStatus,
            SalesOrderStatus toStatus,
            String note,
            TenantPrincipal principal) {
        historyRepository.save(new SalesOrderHistory(
                orgId,
                orderId,
                fromStatus,
                toStatus,
                note,
                principal.userId(),
                resolveActorName(principal)
        ));
    }

    private String generateUniqueOrderNumber(UUID orgId, LocalDate orderDate) {
        long base = orderRepository.countByOrganizationId(orgId) + 1;
        String datePart = orderDate.toString().replace("-", "");
        String candidate = "SO-" + datePart + "-" + String.format("%04d", base);
        while (orderRepository.existsByOrganizationIdAndOrderNumber(orgId, candidate)) {
            candidate = "SO-" + datePart + "-" + String.format("%04d", ++base);
        }
        return candidate;
    }

    private static SalesOrderSummaryResponse toSummary(
            SalesOrder order,
            String customerName,
            boolean hasInvoice) {
        return new SalesOrderSummaryResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerId(),
                customerName,
                order.getStatus(),
                order.getOrderDate(),
                order.getSubtotalAmount(),
                order.getDiscountAmount(),
                order.getTaxRatePercent(),
                order.getTaxAmount(),
                order.getTotalAmount(),
                order.isStockDeducted(),
                hasInvoice,
                order.getCreatedAt()
        );
    }

    static String resolveActorName(TenantPrincipal principal) {
        return principal.displayName() != null && !principal.displayName().isBlank()
                ? principal.displayName()
                : principal.email();
    }

    static String trimToNull(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

