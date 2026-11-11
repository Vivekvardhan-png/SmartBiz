package com.nexusops.platform.finance;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository
        extends JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {

    @Query("""
            SELECT i FROM Invoice i
            LEFT JOIN FETCH i.items
            WHERE i.id = :id AND i.organizationId = :organizationId
            """)
    Optional<Invoice> findWithItemsByIdAndOrganizationId(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.id = :id AND i.organizationId = :organizationId")
    Optional<Invoice> findForUpdateByIdAndOrganizationId(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId
    );

    @Query("""
            SELECT i FROM Invoice i
            LEFT JOIN FETCH i.items
            WHERE i.organizationId = :organizationId AND i.salesOrderId = :salesOrderId
            """)
    Optional<Invoice> findWithItemsByOrganizationIdAndSalesOrderId(
            @Param("organizationId") UUID organizationId,
            @Param("salesOrderId") UUID salesOrderId
    );

    boolean existsByOrganizationIdAndSalesOrderId(UUID organizationId, UUID salesOrderId);

    @Query("""
            SELECT COALESCE(SUM(i.totalAmount), 0)
            FROM Invoice i
            WHERE i.organizationId = :organizationId
              AND i.status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID')
              AND i.issueDate >= :fromDate
              AND i.issueDate <= :toDate
            """)
    BigDecimal sumRevenueInDateRange(
            @Param("organizationId") UUID organizationId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("""
            SELECT COALESCE(SUM(i.totalAmount - i.paidAmount), 0)
            FROM Invoice i
            WHERE i.organizationId = :organizationId
              AND i.status IN ('ISSUED', 'PARTIALLY_PAID')
            """)
    BigDecimal sumOutstandingBalance(@Param("organizationId") UUID organizationId);

    @Query("""
            SELECT i.issueDate AS issueDate,
                   COALESCE(SUM(i.totalAmount), 0) AS revenue,
                   COUNT(i.id) AS invoiceCount
            FROM Invoice i
            WHERE i.organizationId = :organizationId
              AND i.status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID')
              AND i.issueDate >= :fromDate
              AND i.issueDate <= :toDate
            GROUP BY i.issueDate
            ORDER BY i.issueDate ASC
            """)
    List<DailyRevenueRow> findDailyRevenueInDateRange(
            @Param("organizationId") UUID organizationId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("""
            SELECT i FROM Invoice i
            WHERE i.organizationId = :organizationId
              AND i.status IN ('ISSUED', 'PARTIALLY_PAID')
            ORDER BY i.issueDate DESC, i.createdAt DESC
            """)
    List<Invoice> findOutstandingInvoices(@Param("organizationId") UUID organizationId);

    interface DailyRevenueRow {
        LocalDate getIssueDate();
        BigDecimal getRevenue();
        long getInvoiceCount();
    }
}

