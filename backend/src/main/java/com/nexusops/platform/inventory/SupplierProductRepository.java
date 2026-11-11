package com.nexusops.platform.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierProductRepository extends JpaRepository<SupplierProduct, UUID> {

    @Query("""
            SELECT sp FROM SupplierProduct sp
            JOIN FETCH sp.supplier
            JOIN FETCH sp.product
            WHERE sp.organizationId = :organizationId AND sp.product.id = :productId
            ORDER BY sp.createdAt ASC
            """)
    List<SupplierProduct> findAllWithDetailsByOrganizationIdAndProductId(
            @Param("organizationId") UUID organizationId,
            @Param("productId") UUID productId
    );

    @Query("""
            SELECT sp FROM SupplierProduct sp
            JOIN FETCH sp.supplier
            JOIN FETCH sp.product
            WHERE sp.organizationId = :organizationId AND sp.supplier.id = :supplierId
            ORDER BY sp.createdAt ASC
            """)
    List<SupplierProduct> findAllWithDetailsByOrganizationIdAndSupplierId(
            @Param("organizationId") UUID organizationId,
            @Param("supplierId") UUID supplierId
    );

    Optional<SupplierProduct> findByOrganizationIdAndSupplierIdAndProductId(
            UUID organizationId, UUID supplierId, UUID productId);

    void deleteByOrganizationIdAndSupplierIdAndProductId(
            UUID organizationId, UUID supplierId, UUID productId);
}

