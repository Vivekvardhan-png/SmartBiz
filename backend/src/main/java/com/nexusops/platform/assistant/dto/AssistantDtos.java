package com.nexusops.platform.assistant.dto;

import com.nexusops.platform.assistant.ProposalStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AssistantDtos {

    private AssistantDtos() {
    }

    public record ChatTurnDto(
            @NotBlank @Size(max = 20) String role,
            @NotBlank @Size(max = 2000) String content
    ) {
    }

    public record AssistantChatRequest(
            @NotBlank(message = "Message is required.")
            @Size(max = 2000, message = "Message cannot exceed 2000 characters.")
            String message,

            @Valid
            @Size(max = 10, message = "Conversation history cannot exceed 10 turns.")
            List<ChatTurnDto> history
    ) {
    }

    public record SourceCitationDto(
            String toolName,
            String entityType,
            String label,
            String summary
    ) {
    }

    public record ActionProposalDto(
            UUID id,
            String toolName,
            String actionSummary,
            ProposalStatus status,
            String resultSummary,
            Instant createdAt,
            Instant expiresAt,
            Instant resolvedAt
    ) {
    }

    public record AssistantChatResponse(
            String status,
            String reply,
            String errorMessage,
            List<String> toolsUsed,
            List<SourceCitationDto> citations,
            List<ActionProposalDto> pendingActions,
            String disclaimer
    ) {
    }

    public record AssistantAuditLogDto(
            UUID id,
            UUID userId,
            String eventType,
            String toolName,
            String outcome,
            String details,
            Instant createdAt
    ) {
    }
}
