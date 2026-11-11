package com.nexusops.platform.identity;

import com.nexusops.platform.common.api.ApiResponse;
import com.nexusops.platform.identity.dto.IdentityDtos.AddMemberRequest;
import com.nexusops.platform.identity.dto.IdentityDtos.CreateOrganizationRequest;
import com.nexusops.platform.identity.dto.IdentityDtos.OrganizationMemberResponse;
import com.nexusops.platform.identity.dto.IdentityDtos.OrganizationSummary;
import com.nexusops.platform.identity.dto.IdentityDtos.UpdateMemberRoleRequest;
import com.nexusops.platform.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organizations", description = "Tenant organization and membership management.")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final TenantContext tenantContext;

    public OrganizationController(
            OrganizationService organizationService,
            TenantContext tenantContext) {
        this.organizationService = organizationService;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/current")
    @Operation(summary = "Get the active authenticated organization")
    public ResponseEntity<ApiResponse<OrganizationSummary>> getCurrentOrganization() {
        return ResponseEntity.ok(ApiResponse.of(
                organizationService.getCurrentOrganization(tenantContext.requirePrincipal())));
    }

    @PostMapping
    @Operation(summary = "Create an additional organization owned by the current user")
    public ResponseEntity<ApiResponse<OrganizationSummary>> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(
                        organizationService.createOrganization(tenantContext.requirePrincipal(), request)));
    }

    @GetMapping("/current/members")
    @PreAuthorize("hasAuthority('PERM_MEMBERS_READ')")
    @Operation(summary = "List members of the active organization")
    public ResponseEntity<ApiResponse<List<OrganizationMemberResponse>>> listMembers() {
        return ResponseEntity.ok(ApiResponse.of(
                organizationService.listMembers(tenantContext.requirePrincipal())));
    }

    @PostMapping("/current/members")
    @PreAuthorize("hasAuthority('PERM_MEMBERS_MANAGE')")
    @Operation(summary = "Add or provision a member in the active organization")
    public ResponseEntity<ApiResponse<OrganizationMemberResponse>> addMember(
            @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(
                        organizationService.addMember(tenantContext.requirePrincipal(), request)));
    }

    @PatchMapping("/current/members/{memberId}")
    @PreAuthorize("hasAuthority('PERM_MEMBERS_MANAGE')")
    @Operation(summary = "Update a member role inside the active organization")
    public ResponseEntity<ApiResponse<OrganizationMemberResponse>> updateMemberRole(
            @PathVariable UUID memberId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                organizationService.updateMemberRole(
                        tenantContext.requirePrincipal(), memberId, request)));
    }

    @DeleteMapping("/current/members/{memberId}")
    @PreAuthorize("hasAuthority('PERM_MEMBERS_MANAGE')")
    @Operation(summary = "Remove a member from the active organization and revoke their sessions")
    public ResponseEntity<Void> removeMember(@PathVariable UUID memberId) {
        organizationService.removeMember(tenantContext.requirePrincipal(), memberId);
        return ResponseEntity.noContent().build();
    }
}

