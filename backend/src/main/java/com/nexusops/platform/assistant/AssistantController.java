package com.nexusops.platform.assistant;

import com.nexusops.platform.assistant.dto.AssistantDtos.ActionProposalDto;
import com.nexusops.platform.assistant.dto.AssistantDtos.AssistantAuditLogDto;
import com.nexusops.platform.assistant.dto.AssistantDtos.AssistantChatRequest;
import com.nexusops.platform.assistant.dto.AssistantDtos.AssistantChatResponse;
import com.nexusops.platform.common.api.ApiResponse;
import com.nexusops.platform.common.api.PagedResponse;
import com.nexusops.platform.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/assistant")
@Tag(name = "AI Assistant", description = "Controlled conversational business assistant with typed tools and confirmed write actions.")
public class AssistantController {

    private final AssistantService assistantService;
    private final TenantContext tenantContext;

    public AssistantController(
            AssistantService assistantService,
            TenantContext tenantContext) {
        this.assistantService = assistantService;
        this.tenantContext = tenantContext;
    }

    @PostMapping("/chat")
    @PreAuthorize("hasAuthority('PERM_AI_USE')")
    @Operation(summary = "Send a message to the controlled AI business assistant")
    public ResponseEntity<ApiResponse<AssistantChatResponse>> chat(
            @Valid @RequestBody AssistantChatRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                assistantService.chat(tenantContext.requirePrincipal(), request)));
    }

    @GetMapping("/actions")
    @PreAuthorize("hasAuthority('PERM_AI_USE')")
    @Operation(summary = "List assistant action proposals created by the current user in the active organization")
    public ResponseEntity<PagedResponse<ActionProposalDto>> listMyActions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(
                assistantService.listMyProposals(tenantContext.requirePrincipal(), page, pageSize));
    }

    @PostMapping("/actions/{proposalId}/confirm")
    @PreAuthorize("hasAuthority('PERM_AI_USE')")
    @Operation(summary = "Confirm and execute a pending assistant write action after revalidating permissions")
    public ResponseEntity<ApiResponse<ActionProposalDto>> confirmAction(
            @PathVariable UUID proposalId) {
        return ResponseEntity.ok(ApiResponse.of(
                assistantService.confirmProposal(tenantContext.requirePrincipal(), proposalId)));
    }

    @PostMapping("/actions/{proposalId}/cancel")
    @PreAuthorize("hasAuthority('PERM_AI_USE')")
    @Operation(summary = "Cancel a pending assistant write action")
    public ResponseEntity<ApiResponse<ActionProposalDto>> cancelAction(
            @PathVariable UUID proposalId) {
        return ResponseEntity.ok(ApiResponse.of(
                assistantService.cancelProposal(tenantContext.requirePrincipal(), proposalId)));
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('PERM_AI_USE')")
    @Operation(summary = "List organization AI assistant audit logs")
    public ResponseEntity<PagedResponse<AssistantAuditLogDto>> listAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int pageSize) {
        return ResponseEntity.ok(
                assistantService.listAuditLogs(tenantContext.requirePrincipal(), page, pageSize));
    }
}
