package com.nexusops.platform.assistant;

import java.util.List;
import java.util.Map;

/**
 * Abstraction over the backend-managed LLM provider API.
 */
public interface LlmClient {

    LlmResponse complete(LlmRequest request);

    record LlmMessage(
            String role,
            String content,
            String toolCallId,
            List<LlmToolCall> toolCalls
    ) {
        public static LlmMessage system(String content) {
            return new LlmMessage("system", content, null, null);
        }

        public static LlmMessage user(String content) {
            return new LlmMessage("user", content, null, null);
        }

        public static LlmMessage assistant(String content, List<LlmToolCall> toolCalls) {
            return new LlmMessage("assistant", content, null, toolCalls);
        }

        public static LlmMessage tool(String toolCallId, String content) {
            return new LlmMessage("tool", content, toolCallId, null);
        }
    }

    record LlmToolSpec(
            String name,
            String description,
            Map<String, Object> parametersSchema
    ) {
    }

    record LlmToolCall(
            String id,
            String name,
            String argumentsJson
    ) {
    }

    record LlmRequest(
            List<LlmMessage> messages,
            List<LlmToolSpec> tools
    ) {
    }

    record LlmResponse(
            String content,
            List<LlmToolCall> toolCalls
    ) {
    }

    class LlmProviderException extends RuntimeException {
        private final boolean unavailable;

        public LlmProviderException(String message, boolean unavailable) {
            super(message);
            this.unavailable = unavailable;
        }

        public LlmProviderException(String message, boolean unavailable, Throwable cause) {
            super(message, cause);
            this.unavailable = unavailable;
        }

        public boolean isUnavailable() {
            return unavailable;
        }
    }
}
