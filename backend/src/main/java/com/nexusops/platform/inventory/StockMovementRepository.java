package com.nexusops.platform.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    @Query("""
            SELECT m FROM StockMovement m
            JOIN FETCH m.product
            LEFT JOIN FETCH m.supplier
            WHERE m.organizationId = :organizationId AND m.idempotencyKey = :idempotencyKey
            """)
    Optional<StockMovement> findByOrganizationIdAndIdempotencyKey(
            @Param("organizationId") UUID organizationId,
            @Param("idempotencyKey") String idempotencyKey
    );

    @Query(
            value = """
                    SELECT m FROM StockMovement m
                    JOIN FETCH m.product p
                    LEFT JOIN FETCH m.supplier s
                    WHERE m.organizationId = :organizationId
                    ORDER BY m.createdAt DESC
                    """,
            countQuery = """
                    SELECT COUNT(m) FROM StockMovement m
                    WHERE m.organizationId = :organizationId
                    """
    )
    Page<StockMovement> findPageByOrganization(
            @Param("organizationId") UUID organizationId,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT m FROM StockMovement m
                    JOIN FETCH m.product p
                    LEFT JOIN FETCH m.supplier s
                    WHERE m.organizationId = :organizationId
                      AND p.id = :productId
                    ORDER BY m.createdAt DESC
                    """,
            countQuery = """
                    SELECT COUNT(m) FROM StockMovement m
                    WHERE m.organizationId = :organizationId
                      AND m.product.id = :productId
                    """
    )
    Page<StockMovement> findPageByOrganizationAndProduct(
            @Param("organizationId") UUID organizationId,
            @Param("productId") UUID productId,
            Pageable pageable
    );

    @Query("""
            SELECT COALESCE(SUM(m.quantityDelta), 0)
            FROM StockMovement m
            WHERE m.organizationId = :organizationId AND m.product.id = :productId
            """)
    BigDecimal sumQuantityDeltaByOrganizationIdAndProductId(
            @Param("organizationId") UUID organizationId,
            @Param("productId") UUID productId
    );

    @Query(
            value = """
                    SELECT m FROM StockMovement m
                    JOIN FETCH m.product p
                    LEFT JOIN FETCH m.supplier s
                    WHERE m.organizationId = :organizationId
                      AND m.createdAt >= :fromInstant
                      AND m.createdAt < :toExclusive
                    ORDER BY m.createdAt DESC
                    """,
            countQuery = """
                    SELECT COUNT(m) FROM StockMovement m
                    WHERE m.organizationId = :organizationId
                      AND m.createdAt >= :fromInstant
                      AND m.createdAt < :toExclusive
                    """
    )
    Page<StockMovement> findPageByOrganizationAndCreatedRange(
            @Param("organizationId") UUID organizationId,
            @Param("fromInstant") Instant fromInstant,
            @Param("toExclusive") Instant toExclusive,
            Pageable pageable
    );

    @Query("""
            SELECT m FROM StockMovement m
            JOIN FETCH m.product p
            LEFT JOIN FETCH m.supplier s
            WHERE m.organizationId = :organizationId
              AND m.createdAt >= :fromInstant
              AND m.createdAt < :toExclusive
            ORDER BY m.createdAt DESC
            """)
    List<StockMovement> findAllByOrganizationAndCreatedRange(
            @Param("organizationId") UUID organizationId,
            @Param("fromInstant") Instant fromInstant,
            @Param("toExclusive") Instant toExclusive
    );

    @Query("""
            SELECT m FROM StockMovement m
            WHERE m.organizationId = :organizationId
              AND m.product.id = :productId
              AND m.movementType = 'ORDER_DEDUCTION'
              AND m.createdAt >= :fromInstant
            ORDER BY m.createdAt ASC
            """)
    List<StockMovement> findOrderDeductionsSince(
            @Param("organizationId") UUID organizationId,
            @Param("productId") UUID productId,
            @Param("fromInstant") Instant fromInstant
    );
}
