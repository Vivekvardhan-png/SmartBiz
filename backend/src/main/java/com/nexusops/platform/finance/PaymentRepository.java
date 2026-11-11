package com.nexusops.platform.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findAllByOrganizationIdAndInvoiceIdOrderByCreatedAtDesc(
            UUID organizationId,
            UUID invoiceId
    );

    Optional<Payment> findByOrganizationIdAndIdempotencyKey(
            UUID organizationId,
            String idempotencyKey
    );

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM Payment p
            WHERE p.organizationId = :organizationId
              AND p.invoiceId = :invoiceId
              AND p.entryType = :entryType
            """)
    BigDecimal sumAmountByOrganizationAndInvoiceAndType(
            @Param("organizationId") UUID organizationId,
            @Param("invoiceId") UUID invoiceId,
            @Param("entryType") PaymentEntryType entryType
    );

    boolean existsByOrganizationIdAndInvoiceIdAndEntryType(
            UUID organizationId,
            UUID invoiceId,
            PaymentEntryType entryType
    );
}

