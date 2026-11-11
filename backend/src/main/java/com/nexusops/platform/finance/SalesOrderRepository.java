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

public interface SalesOrderRepository
        extends JpaRepository<SalesOrder, UUID>, JpaSpecificationExecutor<SalesOrder> {

    @Query("""
            SELECT o FROM SalesOrder o
            LEFT JOIN FETCH o.items
            WHERE o.id = :id AND o.organizationId = :organizationId
            """)
    Optional<SalesOrder> findWithItemsByIdAndOrganizationId(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId
    );

    @Query("""
            SELECT o FROM SalesOrder o
            LEFT JOIN FETCH o.items
            WHERE UPPER(o.orderNumber) = UPPER(:orderNumber) AND o.organizationId = :organizationId
            """)
    Optional<SalesOrder> findWithItemsByOrganizationIdAndOrderNumberIgnoreCase(
            @Param("organizationId") UUID organizationId,
            @Param("orderNumber") String orderNumber
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM SalesOrder o WHERE o.id = :id AND o.organizationId = :organizationId")
    Optional<SalesOrder> findForUpdateByIdAndOrganizationId(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId
    );

    boolean existsByOrganizationIdAndOrderNumber(UUID organizationId, String orderNumber);

    long countByOrganizationId(UUID organizationId);

    @Query("""
            SELECT o.status AS status,
                   COUNT(o.id) AS total,
                   COALESCE(SUM(o.totalAmount), 0) AS totalAmount
            FROM SalesOrder o
            WHERE o.organizationId = :organizationId
              AND o.orderDate >= :fromDate
              AND o.orderDate <= :toDate
            GROUP BY o.status
            """)
    List<OrderStatusCount> countByStatusInDateRange(
            @Param("organizationId") UUID organizationId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    interface OrderStatusCount {
        SalesOrderStatus getStatus();
        long getTotal();
        BigDecimal getTotalAmount();
    }
}

