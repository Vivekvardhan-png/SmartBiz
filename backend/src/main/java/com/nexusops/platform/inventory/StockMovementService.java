package com.nexusops.platform.inventory;

import com.nexusops.platform.common.api.PagedResponse;
import com.nexusops.platform.common.exception.BusinessRuleException;
import com.nexusops.platform.common.exception.ResourceNotFoundException;
import com.nexusops.platform.inventory.dto.InventoryDtos.RecordStockMovementRequest;
import com.nexusops.platform.inventory.dto.InventoryDtos.StockMovementResponse;
import com.nexusops.platform.security.TenantPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

/**
 * Authoritative stock-movement service enforcing row-level locking,
 * non-negative stock invariants, and idempotency keys.
 */
@Service
public class StockMovementService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final StockMovementRepository movementRepository;

    public StockMovementService(
            ProductRepository productRepository,
            SupplierRepository supplierRepository,
            StockMovementRepository movementRepository) {
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.movementRepository = movementRepository;
    }

    @Transactional
    public StockMovementResponse recordManualMovement(
            TenantPrincipal principal,
            UUID productId,
            RecordStockMovementRequest request) {

        if (!request.movementType().isManualAllowed()) {
            throw new BusinessRuleException(
                    "Movement type " + request.movementType()
                            + " cannot be recorded manually via the adjustment endpoint.");
        }

        return applyMovement(
                principal.organizationId(),
                productId,
                request.supplierId(),
                request.movementType(),
                request.quantity(),
                request.reason(),
                request.reference(),
                request.idempotencyKey(),
                principal.userId(),
                resolveActorName(principal)
        );
    }

    /**
     * Applies a stock movement inside the caller's transaction while holding a
     * {@code PESSIMISTIC_WRITE} lock on the target product row.
     */
    @Transactional
    public StockMovementResponse applyMovement(
            UUID organizationId,
            UUID productId,
            UUID supplierId,
            StockMovementType movementType,
            BigDecimal positiveQuantity,
            String reason,
            String reference,
            String idempotencyKey,
            UUID actorId,
            String actorName) {

        if (positiveQuantity == null || positiveQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Movement quantity must be greater than zero.");
        }

        String normalizedKey = SupplierService.trimToNull(idempotencyKey);
        if (normalizedKey != null) {
            Optional<StockMovement> existing = movementRepository
                    .findByOrganizationIdAndIdempotencyKey(organizationId, normalizedKey);
            if (existing.isPresent()) {
                StockMovement replay = existing.get();
                if (!replay.getProduct().getId().equals(productId)) {
                    throw new BusinessRuleException(
                            "Idempotency key '" + normalizedKey + "' was already used for another product.");
                }
                return toResponse(replay, true);
            }
        }

        Product product = productRepository
                .findForUpdateByIdAndOrganizationId(productId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        Supplier supplier = null;
        if (supplierId != null) {
            supplier = supplierRepository
                    .findByIdAndOrganizationId(supplierId, organizationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier", supplierId));
        }

        BigDecimal scaledQuantity = positiveQuantity.setScale(3, RoundingMode.HALF_UP);
        BigDecimal delta = movementType.sign() > 0 ? scaledQuantity : scaledQuantity.negate();
        BigDecimal before = product.getStockQuantity().setScale(3, RoundingMode.HALF_UP);
        BigDecimal after = before.add(delta).setScale(3, RoundingMode.HALF_UP);

        if (after.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException(
                    "Insufficient stock for SKU '" + product.getSku()
                            + "': available " + before + " " + product.getUnitOfMeasure()
                            + ", attempted deduction " + scaledQuantity + ".");
        }

        product.setStockQuantity(after);
        productRepository.save(product);

        StockMovement saved = movementRepository.save(new StockMovement(
                organizationId,
                product,
                supplier,
                movementType,
                delta,
                before,
                after,
                reason.trim(),
                SupplierService.trimToNull(reference),
                normalizedKey,
                actorId,
                actorName
        ));

        return toResponse(saved, false);
    }

    @Transactional(readOnly = true)
    public PagedResponse<StockMovementResponse> listMovements(
            TenantPrincipal principal,
            UUID productId,
            int page,
            int pageSize) {

        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(safePage - 1, safeSize);

        Page<StockMovement> result;
        if (productId != null) {
            productRepository.findByIdAndOrganizationId(productId, principal.organizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
            result = movementRepository.findPageByOrganizationAndProduct(
                    principal.organizationId(), productId, pageable);
        } else {
            result = movementRepository.findPageByOrganization(
                    principal.organizationId(), pageable);
        }

        return PagedResponse.of(
                result.getContent().stream().map(m -> toResponse(m, false)).toList(),
                safePage,
                safeSize,
                result.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public BigDecimal reconcileProductStock(TenantPrincipal principal, UUID productId) {
        productRepository.findByIdAndOrganizationId(productId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        return movementRepository.sumQuantityDeltaByOrganizationIdAndProductId(
                principal.organizationId(), productId);
    }

    static String resolveActorName(TenantPrincipal principal) {
        return principal.displayName() != null && !principal.displayName().isBlank()
                ? principal.displayName()
                : principal.email();
    }

    public static StockMovementResponse toResponse(StockMovement movement, boolean idempotentReplay) {
        return new StockMovementResponse(
                movement.getId(),
                movement.getProduct().getId(),
                movement.getProduct().getSku(),
                movement.getProduct().getName(),
                movement.getSupplier() != null ? movement.getSupplier().getId() : null,
                movement.getSupplier() != null ? movement.getSupplier().getName() : null,
                movement.getMovementType(),
                movement.getQuantityDelta(),
                movement.getQuantityBefore(),
                movement.getQuantityAfter(),
                movement.getReason(),
                movement.getReference(),
                movement.getActorName(),
                movement.getCreatedAt(),
                idempotentReplay
        );
    }
}

