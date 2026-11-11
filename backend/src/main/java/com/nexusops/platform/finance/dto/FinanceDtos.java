package com.nexusops.platform.finance.dto;

import com.nexusops.platform.finance.InvoiceStatus;
import com.nexusops.platform.finance.PaymentEntryType;
import com.nexusops.platform.finance.PaymentMethod;
import com.nexusops.platform.finance.SalesOrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class FinanceDtos {

    private FinanceDtos() {
    }

    public record OrderItemInput(
            @NotNull(message = "Product ID is required.")
            UUID productId,

            @NotNull(message = "Quantity is required.")
            @DecimalMin(value = "0.001", message = "Quantity must be greater than zero.")
            BigDecimal quantity
    ) {}

    public record CreateSalesOrderRequest(
            @NotNull(message = "Customer ID is required.")
            UUID customerId,

            LocalDate orderDate,

            @DecimalMin(value = "0.00", message = "Discount must be zero or positive.")
            BigDecimal discountAmount,

            @DecimalMin(value = "0.00", message = "Tax rate must be zero or positive.")
            @DecimalMax(value = "100.00", message = "Tax rate cannot exceed 100%.")
            BigDecimal taxRatePercent,

            @Size(max = 4000)
            String notes,

            Boolean confirmImmediately,

            @NotEmpty(message = "At least one order line item is required.")
            @Valid
            List<OrderItemInput> items
    ) {}

    public record OrderStatusNoteRequest(
            @Size(max = 500)
            String note
    ) {}

    public record SalesOrderItemResponse(
            UUID id,
            UUID productId,
            String productSku,
            String productName,
            String unitOfMeasure,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {}

    public record SalesOrderHistoryResponse(
            UUID id,
            SalesOrderStatus fromStatus,
            SalesOrderStatus toStatus,
            String note,
            String actorName,
            Instant createdAt
    ) {}

    public record SalesOrderSummaryResponse(
            UUID id,
            String orderNumber,
            UUID customerId,
            String customerName,
            SalesOrderStatus status,
            LocalDate orderDate,
            BigDecimal subtotalAmount,
            BigDecimal discountAmount,
            BigDecimal taxRatePercent,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            boolean stockDeducted,
            boolean hasInvoice,
            Instant createdAt
    ) {}

    public record SalesOrderDetailResponse(
            UUID id,
            String orderNumber,
            UUID customerId,
            String customerName,
            String customerEmail,
            SalesOrderStatus status,
            LocalDate orderDate,
            BigDecimal subtotalAmount,
            BigDecimal discountAmount,
            BigDecimal taxRatePercent,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            boolean stockDeducted,
            String notes,
            List<SalesOrderItemResponse> items,
            List<SalesOrderHistoryResponse> history,
            UUID invoiceId,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record GenerateInvoiceRequest(
            LocalDate dueDate,
            @Size(max = 4000)
            String notes
    ) {}

    public record InvoiceItemResponse(
            UUID id,
            UUID productId,
            String productSku,
            String description,
            String unitOfMeasure,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {}

    public record InvoiceSummaryResponse(
            UUID id,
            String invoiceNumber,
            UUID salesOrderId,
            UUID customerId,
            String customerName,
            InvoiceStatus status,
            LocalDate issueDate,
            LocalDate dueDate,
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingBalance,
            Instant createdAt
    ) {}

    public record InvoiceDetailResponse(
            UUID id,
            String invoiceNumber,
            UUID salesOrderId,
            UUID customerId,
            String customerName,
            String customerEmail,
            InvoiceStatus status,
            LocalDate issueDate,
            LocalDate dueDate,
            BigDecimal subtotalAmount,
            BigDecimal discountAmount,
            BigDecimal taxRatePercent,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingBalance,
            String notes,
            List<InvoiceItemResponse> items,
            List<PaymentResponse> payments,
            boolean alreadyExisted,
            Instant createdAt
    ) {}

    public record RecordPaymentRequest(
            @NotNull(message = "Entry type is required.")
            PaymentEntryType entryType,

            @NotNull(message = "Amount is required.")
            @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
            BigDecimal amount,

            LocalDate paymentDate,

            @NotNull(message = "Payment method is required.")
            PaymentMethod method,

            @Size(max = 255)
            String reference,

            @Size(max = 1000)
            String notes,

            @Size(max = 128)
            String idempotencyKey
    ) {}

    public record PaymentResponse(
            UUID id,
            UUID invoiceId,
            PaymentEntryType entryType,
            BigDecimal amount,
            LocalDate paymentDate,
            PaymentMethod method,
            String reference,
            String notes,
            String recordedByName,
            Instant createdAt,
            boolean idempotentReplay
    ) {}
}

