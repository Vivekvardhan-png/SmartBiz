package com.nexusops.platform.automation.dto;

import com.nexusops.platform.automation.AutomationAlertSeverity;
import com.nexusops.platform.automation.AutomationAlertStatus;
import com.nexusops.platform.automation.AutomationEntityType;
import com.nexusops.platform.automation.AutomationRunStatus;
import com.nexusops.platform.automation.AutomationTriggerSource;
import com.nexusops.platform.automation.AutomationWorkflowType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AutomationDtos {

    private AutomationDtos() {
    }

    public record RunWorkflowRequest(AutomationWorkflowType workflowType) {
    }

    public record AutomationRunResponse(
            UUID id,
            AutomationWorkflowType workflowType,
            AutomationTriggerSource triggerSource,
            AutomationRunStatus status,
            int alertsCreated,
            int alertsSkippedDuplicate,
            String errorMessage,
            Instant startedAt,
            Instant completedAt
    ) {
    }

    public record AutomationRunBatchResponse(
            List<AutomationRunResponse> runs
    ) {
    }

    public record AutomationAlertResponse(
            UUID id,
            AutomationWorkflowType workflowType,
            AutomationEntityType entityType,
            UUID entityId,
            AutomationAlertSeverity severity,
            String title,
            String message,
            AutomationAlertStatus status,
            UUID runId,
            Instant acknowledgedAt,
            Instant createdAt
    ) {
    }
}
