package com.nexusops.platform.automation;

import com.nexusops.platform.automation.dto.AutomationDtos.AutomationAlertResponse;
import com.nexusops.platform.automation.dto.AutomationDtos.AutomationRunBatchResponse;
import com.nexusops.platform.automation.dto.AutomationDtos.AutomationRunResponse;
import com.nexusops.platform.common.api.PagedResponse;
import com.nexusops.platform.common.exception.AccessDeniedException;
import com.nexusops.platform.common.exception.ResourceNotFoundException;
import com.nexusops.platform.identity.Permission;
import com.nexusops.platform.security.TenantPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AutomationService {

    private static final Logger log = LoggerFactory.getLogger(AutomationService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final AutomationWorkflowExecutor executor;
    private final AutomationRunRepository runRepository;
    private final AutomationAlertRepository alertRepository;

    public AutomationService(
            AutomationWorkflowExecutor executor,
            AutomationRunRepository runRepository,
            AutomationAlertRepository alertRepository) {
        this.executor = executor;
        this.runRepository = runRepository;
        this.alertRepository = alertRepository;
    }

    public AutomationRunBatchResponse runManualWorkflows(
            TenantPrincipal principal,
            AutomationWorkflowType requestedType) {

        List<AutomationWorkflowType> toRun = requestedType != null
                ? List.of(requestedType)
                : List.of(AutomationWorkflowType.LOW_STOCK_ALERT, AutomationWorkflowType.FOLLOW_UP_REMINDER);

        for (AutomationWorkflowType wf : toRun) {
            Permission required = requiredPermission(wf);
            if (!principal.hasPermission(required)) {
                throw new AccessDeniedException(
                        "Missing permission " + required.name() + " to run workflow " + wf.name() + ".");
            }
        }

        List<AutomationRunResponse> results = new ArrayList<>();
        for (AutomationWorkflowType wf : toRun) {
            results.add(runSafely(principal.organizationId(), wf, AutomationTriggerSource.MANUAL, principal.userId()));
        }
        return new AutomationRunBatchResponse(results);
    }

    public List<AutomationRunResponse> runScheduledForOrganization(UUID organizationId) {
        List<AutomationRunResponse> runs = new ArrayList<>();
        for (AutomationWorkflowType wf : List.of(
                AutomationWorkflowType.LOW_STOCK_ALERT,
                AutomationWorkflowType.FOLLOW_UP_REMINDER)) {
            runs.add(runSafely(organizationId, wf, AutomationTriggerSource.SCHEDULED, null));
        }
        return runs;
    }

    private AutomationRunResponse runSafely(
            UUID organizationId,
            AutomationWorkflowType workflowType,
            AutomationTriggerSource triggerSource,
            UUID actorUserId) {
        try {
            AutomationRun run = executor.executeWorkflow(
                    organizationId, workflowType, triggerSource, actorUserId);
            return toRunResponse(run);
        } catch (RuntimeException ex) {
            log.warn("Automation workflow {} failed for org {}: {}", workflowType, organizationId, ex.getMessage());
            AutomationRun failed = executor.recordFailedRun(
                    organizationId,
                    workflowType,
                    triggerSource,
                    actorUserId,
                    ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            return toRunResponse(failed);
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<AutomationAlertResponse> listAlerts(
            TenantPrincipal principal,
            AutomationAlertStatus status,
            int page,
            int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(safePage - 1, safeSize);

        Page<AutomationAlert> result = status != null
                ? alertRepository.findAllByOrganizationIdAndStatusOrderByCreatedAtDesc(
                        principal.organizationId(), status, pageable)
                : alertRepository.findAllByOrganizationIdOrderByCreatedAtDesc(
                        principal.organizationId(), pageable);

        return PagedResponse.of(
                result.getContent().stream().map(AutomationService::toAlertResponse).toList(),
                safePage,
                safeSize,
                result.getTotalElements()
        );
    }

    @Transactional
    public AutomationAlertResponse acknowledgeAlert(TenantPrincipal principal, UUID alertId) {
        AutomationAlert alert = alertRepository
                .findByIdAndOrganizationId(alertId, principal.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("AutomationAlert", alertId));

        if (alert.getStatus() == AutomationAlertStatus.OPEN) {
            alert.acknowledge(principal.userId());
            alert = alertRepository.save(alert);
        }
        return toAlertResponse(alert);
    }

    @Transactional(readOnly = true)
    public PagedResponse<AutomationRunResponse> listRuns(
            TenantPrincipal principal,
            int page,
            int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        Page<AutomationRun> result = runRepository.findAllByOrganizationIdOrderByStartedAtDesc(
                principal.organizationId(),
                PageRequest.of(safePage - 1, safeSize)
        );
        return PagedResponse.of(
                result.getContent().stream().map(AutomationService::toRunResponse).toList(),
                safePage,
                safeSize,
                result.getTotalElements()
        );
    }

    private static Permission requiredPermission(AutomationWorkflowType workflowType) {
        return switch (workflowType) {
            case LOW_STOCK_ALERT -> Permission.INVENTORY_READ;
            case FOLLOW_UP_REMINDER -> Permission.CRM_READ;
        };
    }

    static AutomationRunResponse toRunResponse(AutomationRun run) {
        return new AutomationRunResponse(
                run.getId(),
                run.getWorkflowType(),
                run.getTriggerSource(),
                run.getStatus(),
                run.getAlertsCreated(),
                run.getAlertsSkippedDuplicate(),
                run.getErrorMessage(),
                run.getStartedAt(),
                run.getCompletedAt()
        );
    }

    static AutomationAlertResponse toAlertResponse(AutomationAlert alert) {
        return new AutomationAlertResponse(
                alert.getId(),
                alert.getWorkflowType(),
                alert.getEntityType(),
                alert.getEntityId(),
                alert.getSeverity(),
                alert.getTitle(),
                alert.getMessage(),
                alert.getStatus(),
                alert.getRunId(),
                alert.getAcknowledgedAt(),
                alert.getCreatedAt()
        );
    }
}
