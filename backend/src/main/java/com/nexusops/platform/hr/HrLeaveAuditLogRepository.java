package com.nexusops.platform.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HrLeaveAuditLogRepository extends JpaRepository<HrLeaveAuditLog, UUID> {

    List<HrLeaveAuditLog> findAllByOrganizationIdAndLeaveRequestIdOrderByCreatedAtAsc(
            UUID organizationId, UUID leaveRequestId);
}
