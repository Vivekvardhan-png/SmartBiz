package com.nexusops.platform.inventory.dto;

import com.nexusops.platform.inventory.StockMovementType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    public record CreateSupplierRequest(
            @NotBlank(message = "Supplier name is required.")
            @Size(max = 255)
            String name,

            @Size(max = 255)
            String contactName,

            @Email(message = "Email must be valid.")
            @Size(max = 320)
            String email,

            @Size(max = 50)
            String phone,

            @Size(max = 2000)
            String address,

            @Size(max = 4000)
            String notes
    ) {}

    public record UpdateSupplierRequest(
            @Size(max = 255)
            String name,

            @Size(max = 255)
            String contactName,

            @Email(message = "Email must be valid.")
            @Size(max = 320)
            String email,

            @Size(max = 50)
            String phone,

            @Size(max = 2000)
            String address,

            @Size(max = 4000)
            String notes,

            Boolean active
    ) {}

    public record SupplierResponse(
            UUID id,
            String name,
            String contactName,
            String email,
            String phone,
            String address,
            String notes,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CreateProductRequest(
            @NotBlank(message = "SKU is required.")
            @Size(max = 100)
            String sku,

            @NotBlank(message = "Product name is required.")
            @Size(max = 255)
            String name,

            @Size(max = 2000)
            String description,

            @Size(max = 100)
            String category,

            @NotBlank(message = "Unit of measure is required.")
            @Size(max = 50)
            String unitOfMeasure,

            @NotNull(message = "Unit price is required.")
            @DecimalMin(value = "0.00", message = "Unit price must be zero or positive.")
            BigDecimal unitPrice,

            @DecimalMin(value = "0.00", message = "Cost price must be zero or positive.")
            BigDecimal costPrice,

            @DecimalMin(value = "0.000", message = "Initial stock must be zero or positive.")
            BigDecimal initialStock,

            @NotNull(message = "Low-stock threshold is required.")
            @DecimalMin(value = "0.000", message = "Low-stock threshold must be zero or positive.")
            BigDecimal lowStockThreshold
    ) {}

    public record UpdateProductRequest(
            @Size(max = 100)
            String sku,

            @Size(max = 255)
            String name,

            @Size(max = 2000)
            String description,

            @Size(max = 100)
            String category,

            @Size(max = 50)
            String unitOfMeasure,

            @DecimalMin(value = "0.00", message = "Unit price must be zero or positive.")
            BigDecimal unitPrice,

            @DecimalMin(value = "0.00", message = "Cost price must be zero or positive.")
            BigDecimal costPrice,

            @DecimalMin(value = "0.000", message = "Low-stock threshold must be zero or positive.")
            BigDecimal lowStockThreshold,

            Boolean active
    ) {}

    public record ProductResponse(
            UUID id,
            String sku,
            String name,
            String description,
            String category,
            String unitOfMeasure,
            BigDecimal unitPrice,
            BigDecimal costPrice,
            BigDecimal stockQuantity,
            BigDecimal lowStockThreshold,
            boolean lowStock,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record LinkSupplierProductRequest(
            @NotNull(message = "Supplier ID is required.")
            UUID supplierId,

            @Size(max = 100)
            String supplierSku,

            @DecimalMin(value = "0.00", message = "Unit cost must be zero or positive.")
            BigDecimal unitCost,

            @Min(value = 0, message = "Lead time days must be zero or positive.")
            Integer leadTimeDays
    ) {}

    public record SupplierProductResponse(
            UUID id,
            UUID supplierId,
            String supplierName,
            UUID productId,
            String productSku,
            String productName,
            String supplierSku,
            BigDecimal unitCost,
            Integer leadTimeDays,
            Instant createdAt
    ) {}

    public record RecordStockMovementRequest(
            @NotNull(message = "Movement type is required.")
            StockMovementType movementType,

            @NotNull(message = "Quantity is required.")
            @DecimalMin(value = "0.001", message = "Quantity must be greater than zero.")
            BigDecimal quantity,

            @NotBlank(message = "Reason is required.")
            @Size(max = 500)
            String reason,

            @Size(max = 255)
            String reference,

            UUID supplierId,

            @Size(max = 128)
            String idempotencyKey
    ) {}

    public record StockMovementResponse(
            UUID id,
            UUID productId,
            String productSku,
            String productName,
            UUID supplierId,
            String supplierName,
            StockMovementType movementType,
            BigDecimal quantityDelta,
            BigDecimal quantityBefore,
            BigDecimal quantityAfter,
            String reason,
            String reference,
            String actorName,
            Instant createdAt,
            boolean idempotentReplay
    ) {}
}

