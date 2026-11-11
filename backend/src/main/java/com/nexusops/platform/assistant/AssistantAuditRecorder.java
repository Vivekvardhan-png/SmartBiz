package com.nexusops.platform.assistant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Persists assistant audit entries independently of caller transactions so
 * denied, malformed, or failed attempts are still durably recorded.
 */
@Component
public class AssistantAuditRecorder {

    private final AssistantAuditLogRepository repository;

    public AssistantAuditRecorder(AssistantAuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            UUID organizationId,
            UUID userId,
            String eventType,
            String toolName,
            String outcome,
            String details) {
        repository.save(new AssistantAuditLog(
                organizationId,
                userId,
                eventType,
                toolName,
                outcome,
                details
        ));
    }
}
