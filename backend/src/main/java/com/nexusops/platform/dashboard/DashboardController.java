package com.nexusops.platform.dashboard;

import com.nexusops.platform.common.api.ApiResponse;
import com.nexusops.platform.dashboard.dto.DashboardSummaryResponse;
import com.nexusops.platform.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Organization-scoped executive metrics and KPIs.")
public class DashboardController {

    private final DashboardService dashboardService;
    private final TenantContext tenantContext;

    public DashboardController(DashboardService dashboardService, TenantContext tenantContext) {
        this.dashboardService = dashboardService;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('PERM_CRM_READ')")
    @Operation(summary = "Get authorized dashboard summary metrics")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getSummary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.of(
                dashboardService.getSummary(tenantContext.requirePrincipal(), from, to)));
    }
}

