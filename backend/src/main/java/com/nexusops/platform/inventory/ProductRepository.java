package com.nexusops.platform.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository
        extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.organizationId = :organizationId")
    Optional<Product> findForUpdateByIdAndOrganizationId(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId
    );

    boolean existsByOrganizationIdAndSkuIgnoreCase(UUID organizationId, String sku);

    boolean existsByOrganizationIdAndSkuIgnoreCaseAndIdNot(
            UUID organizationId, String sku, UUID id);

    @Query("""
            SELECT COUNT(p)
            FROM Product p
            WHERE p.organizationId = :organizationId
              AND p.active = true
              AND p.stockQuantity <= p.lowStockThreshold
            """)
    long countLowStockProducts(@Param("organizationId") UUID organizationId);
}

