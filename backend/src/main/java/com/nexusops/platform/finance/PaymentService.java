package com.nexusops.platform.finance;

import com.nexusops.platform.common.exception.BusinessRuleException;
import com.nexusops.platform.common.exception.ResourceNotFoundException;
import com.nexusops.platform.finance.dto.FinanceDtos.PaymentResponse;
import com.nexusops.platform.finance.dto.FinanceDtos.RecordPaymentRequest;
import com.nexusops.platform.security.TenantPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;

    public PaymentService(
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listPayments(TenantPrincipal principal, UUID invoiceId) {
        invoiceRepository.findWithItemsByIdAndOrganizationId(invoiceId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

        return paymentRepository.findAllByOrganizationIdAndInvoiceIdOrderByCreatedAtDesc(
                        principal.organizationId(), invoiceId)
                .stream()
                .map(p -> toResponse(p, false))
                .toList();
    }

    @Transactional
    public PaymentResponse recordEntry(
            TenantPrincipal principal,
            UUID invoiceId,
            RecordPaymentRequest request) {

        UUID orgId = principal.organizationId();
        String normalizedKey = SalesOrderService.trimToNull(request.idempotencyKey());
        if (normalizedKey != null) {
            Optional<Payment> existing = paymentRepository
                    .findByOrganizationIdAndIdempotencyKey(orgId, normalizedKey);
            if (existing.isPresent()) {
                Payment replay = existing.get();
                if (!replay.getInvoiceId().equals(invoiceId)) {
                    throw new BusinessRuleException(
                            "Idempotency key '" + normalizedKey + "' was already used on another invoice.");
                }
                return toResponse(replay, true);
            }
        }

        Invoice invoice = invoiceRepository
                .findForUpdateByIdAndOrganizationId(invoiceId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

        if (invoice.getStatus() == InvoiceStatus.VOID) {
            throw new BusinessRuleException("Cannot record payments or refunds on a VOID invoice.");
        }

        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Payment or refund amount must be greater than zero.");
        }

        BigDecimal totalPayments = paymentRepository.sumAmountByOrganizationAndInvoiceAndType(
                orgId, invoice.getId(), PaymentEntryType.PAYMENT);
        BigDecimal totalRefunds = paymentRepository.sumAmountByOrganizationAndInvoiceAndType(
                orgId, invoice.getId(), PaymentEntryType.REFUND);
        BigDecimal currentNetPaid = totalPayments.subtract(totalRefunds).setScale(2, RoundingMode.HALF_UP);

        if (request.entryType() == PaymentEntryType.PAYMENT) {
            BigDecimal nextNetPaid = currentNetPaid.add(amount);
            if (nextNetPaid.compareTo(invoice.getTotalAmount()) > 0) {
                BigDecimal remaining = invoice.getTotalAmount().subtract(currentNetPaid);
                throw new BusinessRuleException(
                        "Payment of " + amount + " exceeds remaining balance of " + remaining + ".");
            }
        } else {
            if (amount.compareTo(currentNetPaid) > 0) {
                throw new BusinessRuleException(
                        "Refund of " + amount + " exceeds net paid amount of " + currentNetPaid + ".");
            }
        }

        LocalDate paymentDate = request.paymentDate() != null
                ? request.paymentDate()
                : LocalDate.now(ZoneOffset.UTC);

        Payment saved = paymentRepository.saveAndFlush(new Payment(
                orgId,
                invoice.getId(),
                request.entryType(),
                amount,
                paymentDate,
                request.method(),
                SalesOrderService.trimToNull(request.reference()),
                SalesOrderService.trimToNull(request.notes()),
                normalizedKey,
                principal.userId(),
                SalesOrderService.resolveActorName(principal)
        ));

        // Reconcile invoice paidAmount and status strictly from ledger totals
        BigDecimal reconciledPayments = paymentRepository.sumAmountByOrganizationAndInvoiceAndType(
                orgId, invoice.getId(), PaymentEntryType.PAYMENT);
        BigDecimal reconciledRefunds = paymentRepository.sumAmountByOrganizationAndInvoiceAndType(
                orgId, invoice.getId(), PaymentEntryType.REFUND);
        BigDecimal reconciledNetPaid = reconciledPayments.subtract(reconciledRefunds)
                .setScale(2, RoundingMode.HALF_UP);

        invoice.setPaidAmount(reconciledNetPaid);
        if (reconciledNetPaid.compareTo(invoice.getTotalAmount()) == 0
                && invoice.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else if (reconciledNetPaid.compareTo(BigDecimal.ZERO) > 0) {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        } else if (reconciledRefunds.compareTo(BigDecimal.ZERO) > 0) {
            invoice.setStatus(InvoiceStatus.REFUNDED);
        } else {
            invoice.setStatus(InvoiceStatus.ISSUED);
        }

        return toResponse(saved, false);
    }

    static PaymentResponse toResponse(Payment payment, boolean idempotentReplay) {
        return new PaymentResponse(
                payment.getId(),
                payment.getInvoiceId(),
                payment.getEntryType(),
                payment.getAmount(),
                payment.getPaymentDate(),
                payment.getMethod(),
                payment.getReference(),
                payment.getNotes(),
                payment.getRecordedByName(),
                payment.getCreatedAt(),
                idempotentReplay
        );
    }
}

