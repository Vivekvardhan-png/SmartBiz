package com.nexusops.platform.automation;

import com.nexusops.platform.automation.dto.AutomationDtos.AutomationAlertResponse;
import com.nexusops.platform.automation.dto.AutomationDtos.AutomationRunBatchResponse;
import com.nexusops.platform.automation.dto.AutomationDtos.AutomationRunResponse;
import com.nexusops.platform.automation.dto.AutomationDtos.RunWorkflowRequest;
import com.nexusops.platform.common.api.ApiResponse;
import com.nexusops.platform.common.api.PagedResponse;
import com.nexusops.platform.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/automation")
@Tag(name = "Automation", description = "Deterministic business automation workflows, alerts, and audit history.")
public class AutomationController {

    private final AutomationService automationService;
    private final TenantContext tenantContext;

    public AutomationController(
            AutomationService automationService,
            TenantContext tenantContext) {
        this.automationService = automationService;
        this.tenantContext = tenantContext;
    }

    @PostMapping("/workflows/run")
    @PreAuthorize("hasAuthority('PERM_AI_USE')")
    @Operation(summary = "Run one or all deterministic automation workflows for the current organization")
    public ResponseEntity<ApiResponse<AutomationRunBatchResponse>> runWorkflows(
            @RequestBody(required = false) RunWorkflowRequest request) {
        AutomationWorkflowType type = request != null ? request.workflowType() : null;
        return ResponseEntity.ok(ApiResponse.of(
                automationService.runManualWorkflows(tenantContext.requirePrincipal(), type)));
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('PERM_AI_USE')")
    @Operation(summary = "List organization automation alerts")
    public ResponseEntity<PagedResponse<AutomationAlertResponse>> listAlerts(
            @RequestParam(required = false) AutomationAlertStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(
                automationService.listAlerts(tenantContext.requirePrincipal(), status, page, pageSize));
    }

    @PostMapping("/alerts/{alertId}/acknowledge")
    @PreAuthorize("hasAuthority('PERM_AI_USE')")
    @Operation(summary = "Acknowledge an open automation alert")
    public ResponseEntity<ApiResponse<AutomationAlertResponse>> acknowledgeAlert(
            @PathVariable UUID alertId) {
        return ResponseEntity.ok(ApiResponse.of(
                automationService.acknowledgeAlert(tenantContext.requirePrincipal(), alertId)));
    }

    @GetMapping("/runs")
    @PreAuthorize("hasAuthority('PERM_AI_USE')")
    @Operation(summary = "List automation execution audit runs")
    public ResponseEntity<PagedResponse<AutomationRunResponse>> listRuns(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(
                automationService.listRuns(tenantContext.requirePrincipal(), page, pageSize));
    }
}
