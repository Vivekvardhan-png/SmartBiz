package com.nexusops.platform.crm;

import com.nexusops.platform.common.api.ApiResponse;
import com.nexusops.platform.common.api.PagedResponse;
import com.nexusops.platform.crm.dto.CrmDtos.ConvertLeadRequest;
import com.nexusops.platform.crm.dto.CrmDtos.CreateCrmNoteRequest;
import com.nexusops.platform.crm.dto.CrmDtos.CreateLeadRequest;
import com.nexusops.platform.crm.dto.CrmDtos.CrmNoteResponse;
import com.nexusops.platform.crm.dto.CrmDtos.LeadConversionResponse;
import com.nexusops.platform.crm.dto.CrmDtos.LeadResponse;
import com.nexusops.platform.crm.dto.CrmDtos.UpdateLeadRequest;
import com.nexusops.platform.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crm/leads")
@Tag(name = "CRM Leads", description = "Organization-scoped lead pipeline, follow-up notes, and customer conversion.")
public class LeadController {

    private final LeadService leadService;
    private final TenantContext tenantContext;

    public LeadController(LeadService leadService, TenantContext tenantContext) {
        this.leadService = leadService;
        this.tenantContext = tenantContext;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_CRM_READ')")
    @Operation(summary = "List, search, filter, and paginate leads")
    public ResponseEntity<PagedResponse<LeadResponse>> listLeads(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResponseEntity.ok(leadService.listLeads(
                tenantContext.requirePrincipal(), search, status, page, pageSize));
    }

    @GetMapping("/{leadId}")
    @PreAuthorize("hasAuthority('PERM_CRM_READ')")
    @Operation(summary = "Get lead details")
    public ResponseEntity<ApiResponse<LeadResponse>> getLead(@PathVariable UUID leadId) {
        return ResponseEntity.ok(ApiResponse.of(
                leadService.getLead(tenantContext.requirePrincipal(), leadId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_CRM_WRITE')")
    @Operation(summary = "Create a new lead")
    public ResponseEntity<ApiResponse<LeadResponse>> createLead(
            @Valid @RequestBody CreateLeadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(
                        leadService.createLead(tenantContext.requirePrincipal(), request)));
    }

    @PatchMapping("/{leadId}")
    @PreAuthorize("hasAuthority('PERM_CRM_WRITE')")
    @Operation(summary = "Update lead details or transition status")
    public ResponseEntity<ApiResponse<LeadResponse>> updateLead(
            @PathVariable UUID leadId,
            @Valid @RequestBody UpdateLeadRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                leadService.updateLead(tenantContext.requirePrincipal(), leadId, request)));
    }

    @PostMapping("/{leadId}/convert")
    @PreAuthorize("hasAuthority('PERM_CRM_WRITE')")
    @Operation(summary = "Idempotently convert a lead into a customer")
    public ResponseEntity<ApiResponse<LeadConversionResponse>> convertLead(
            @PathVariable UUID leadId,
            @RequestBody(required = false) ConvertLeadRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                leadService.convertLead(tenantContext.requirePrincipal(), leadId, request)));
    }

    @GetMapping("/{leadId}/notes")
    @PreAuthorize("hasAuthority('PERM_CRM_READ')")
    @Operation(summary = "List follow-up notes for a lead")
    public ResponseEntity<ApiResponse<List<CrmNoteResponse>>> listNotes(@PathVariable UUID leadId) {
        return ResponseEntity.ok(ApiResponse.of(
                leadService.listNotes(tenantContext.requirePrincipal(), leadId)));
    }

    @PostMapping("/{leadId}/notes")
    @PreAuthorize("hasAuthority('PERM_CRM_WRITE')")
    @Operation(summary = "Add a follow-up note to a lead")
    public ResponseEntity<ApiResponse<CrmNoteResponse>> addNote(
            @PathVariable UUID leadId,
            @Valid @RequestBody CreateCrmNoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(
                        leadService.addNote(tenantContext.requirePrincipal(), leadId, request)));
    }
}

