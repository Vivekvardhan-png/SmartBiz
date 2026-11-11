package com.nexusops.platform.finance;

import com.nexusops.platform.common.api.PagedResponse;
import com.nexusops.platform.common.exception.BusinessRuleException;
import com.nexusops.platform.common.exception.ResourceNotFoundException;
import com.nexusops.platform.crm.Customer;
import com.nexusops.platform.crm.CustomerRepository;
import com.nexusops.platform.finance.dto.FinanceDtos.GenerateInvoiceRequest;
import com.nexusops.platform.finance.dto.FinanceDtos.InvoiceDetailResponse;
import com.nexusops.platform.finance.dto.FinanceDtos.InvoiceItemResponse;
import com.nexusops.platform.finance.dto.FinanceDtos.InvoiceSummaryResponse;
import com.nexusops.platform.finance.dto.FinanceDtos.PaymentResponse;
import com.nexusops.platform.security.TenantPrincipal;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class InvoiceService {

    private static final int MAX_PAGE_SIZE = 100;

    private final InvoiceRepository invoiceRepository;
    private final InvoiceSequenceRepository sequenceRepository;
    private final SalesOrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            InvoiceSequenceRepository sequenceRepository,
            SalesOrderRepository orderRepository,
            CustomerRepository customerRepository,
            PaymentRepository paymentRepository) {
        this.invoiceRepository = invoiceRepository;
        this.sequenceRepository = sequenceRepository;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public PagedResponse<InvoiceSummaryResponse> listInvoices(
            TenantPrincipal principal,
            InvoiceStatus status,
            int page,
            int pageSize) {

        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);

        Specification<Invoice> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), principal.organizationId()));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<Invoice> result = invoiceRepository.findAll(
                spec,
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        return PagedResponse.of(
                result.getContent().stream().map(InvoiceService::toSummary).toList(),
                safePage,
                safeSize,
                result.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public InvoiceDetailResponse getInvoice(TenantPrincipal principal, UUID invoiceId) {
        Invoice invoice = invoiceRepository
                .findWithItemsByIdAndOrganizationId(invoiceId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));
        return buildDetail(invoice, false);
    }

    @Transactional
    public InvoiceDetailResponse generateFromOrder(
            TenantPrincipal principal,
            UUID orderId,
            GenerateInvoiceRequest request) {

        UUID orgId = principal.organizationId();
        SalesOrder order = orderRepository
                .findForUpdateByIdAndOrganizationId(orderId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("SalesOrder", orderId));

        Optional<Invoice> existingOpt = invoiceRepository
                .findWithItemsByOrganizationIdAndSalesOrderId(orgId, order.getId());
        if (existingOpt.isPresent()) {
            return buildDetail(existingOpt.get(), true);
        }

        if (order.getStatus() != SalesOrderStatus.CONFIRMED
                && order.getStatus() != SalesOrderStatus.FULFILLED) {
            throw new BusinessRuleException(
                    "Only CONFIRMED or FULFILLED orders can generate an invoice (current status: "
                            + order.getStatus() + ").");
        }

        Customer customer = customerRepository
                .findByIdAndOrganizationId(order.getCustomerId(), orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", order.getCustomerId()));

        LocalDate issueDate = LocalDate.now(ZoneOffset.UTC);
        LocalDate dueDate = request != null && request.dueDate() != null
                ? request.dueDate()
                : issueDate.plusDays(30);
        if (dueDate.isBefore(issueDate)) {
            throw new BusinessRuleException("Invoice due date cannot precede issue date.");
        }

        InvoiceSequence sequence = sequenceRepository
                .findForUpdateByOrganizationId(orgId)
                .orElseGet(() -> sequenceRepository.saveAndFlush(new InvoiceSequence(orgId, 1L)));

        long seqNumber = sequence.getAndIncrement();
        String invoiceNumber = "INV-" + String.format("%06d", seqNumber);

        Invoice invoice = new Invoice(
                orgId,
                order.getId(),
                customer.getId(),
                invoiceNumber,
                customer.getName(),
                customer.getEmail(),
                issueDate,
                dueDate,
                order.getSubtotalAmount(),
                order.getDiscountAmount(),
                order.getTaxRatePercent(),
                order.getTaxAmount(),
                order.getTotalAmount(),
                request != null ? SalesOrderService.trimToNull(request.notes()) : null,
                principal.userId()
        );

        for (SalesOrderItem orderItem : order.getItems()) {
            invoice.addItem(new InvoiceItem(
                    orgId,
                    orderItem.getProductId(),
                    orderItem.getProductSku(),
                    orderItem.getProductName(),
                    orderItem.getUnitOfMeasure(),
                    orderItem.getQuantity(),
                    orderItem.getUnitPrice(),
                    orderItem.getLineTotal()
            ));
        }

        Invoice saved = invoiceRepository.saveAndFlush(invoice);
        return buildDetail(saved, false);
    }

    InvoiceDetailResponse buildDetail(Invoice invoice, boolean alreadyExisted) {
        List<InvoiceItemResponse> items = invoice.getItems().stream()
                .map(item -> new InvoiceItemResponse(
                        item.getId(),
                        item.getProductId(),
                        item.getProductSku(),
                        item.getDescription(),
                        item.getUnitOfMeasure(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getLineTotal()
                ))
                .toList();

        List<PaymentResponse> payments = paymentRepository
                .findAllByOrganizationIdAndInvoiceIdOrderByCreatedAtDesc(
                        invoice.getOrganizationId(), invoice.getId())
                .stream()
                .map(p -> PaymentService.toResponse(p, false))
                .toList();

        return new InvoiceDetailResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getSalesOrderId(),
                invoice.getCustomerId(),
                invoice.getCustomerName(),
                invoice.getCustomerEmail(),
                invoice.getStatus(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getSubtotalAmount(),
                invoice.getDiscountAmount(),
                invoice.getTaxRatePercent(),
                invoice.getTaxAmount(),
                invoice.getTotalAmount(),
                invoice.getPaidAmount(),
                invoice.getOutstandingBalance(),
                invoice.getNotes(),
                items,
                payments,
                alreadyExisted,
                invoice.getCreatedAt()
        );
    }

    static InvoiceSummaryResponse toSummary(Invoice invoice) {
        return new InvoiceSummaryResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getSalesOrderId(),
                invoice.getCustomerId(),
                invoice.getCustomerName(),
                invoice.getStatus(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getTotalAmount(),
                invoice.getPaidAmount(),
                invoice.getOutstandingBalance(),
                invoice.getCreatedAt()
        );
    }
}

