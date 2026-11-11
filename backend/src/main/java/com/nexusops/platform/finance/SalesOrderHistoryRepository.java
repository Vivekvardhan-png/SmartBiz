package com.nexusops.platform.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SalesOrderHistoryRepository extends JpaRepository<SalesOrderHistory, UUID> {

    List<SalesOrderHistory> findAllByOrganizationIdAndSalesOrderIdOrderByCreatedAtDesc(
            UUID organizationId,
            UUID salesOrderId
    );
}

