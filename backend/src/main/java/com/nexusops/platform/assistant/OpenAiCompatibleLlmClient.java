package com.nexusops.platform.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible Chat Completions client that keeps API credentials strictly
 * on the backend and enforces explicit HTTP connect/read timeouts.
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);

    private final AiAssistantProperties properties;
    private final RestClient restClient;

    public OpenAiCompatibleLlmClient(AiAssistantProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        if (!properties.isEnabled()) {
            throw new LlmProviderException(
                    "AI assistant provider is disabled (AI_ASSISTANT_ENABLED=false).", true);
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new LlmProviderException(
                    "AI assistant provider API key is not configured on the backend.", true);
        }

        String baseUrl = properties.getProviderBaseUrl() != null
                ? properties.getProviderBaseUrl().replaceAll("/+$", "")
                : "";
        if (baseUrl.isBlank()) {
            throw new LlmProviderException(
                    "AI assistant provider base URL is not configured.", true);
        }

        Map<String, Object> body = buildRequestPayload(request);

        try {
            JsonNode response = restClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey().trim())
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            return parseResponse(response);
        } catch (ResourceAccessException ex) {
            log.warn("AI provider request timed out or failed to connect: {}", ex.getMessage());
            throw new LlmProviderException(
                    "AI provider timed out or could not be reached. Please try again.", true, ex);
        } catch (RestClientResponseException ex) {
            int code = ex.getStatusCode().value();
            log.warn("AI provider returned HTTP {}", code);
            throw new LlmProviderException(
                    "AI provider returned an upstream error (HTTP " + code + ").", false, ex);
        } catch (LlmProviderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Unexpected AI provider response error: {}", ex.getMessage());
            throw new LlmProviderException(
                    "AI provider returned an unexpected response.", false, ex);
        }
    }

    private Map<String, Object> buildRequestPayload(LlmRequest request) {
        List<Map<String, Object>> serializedMessages = new ArrayList<>();
        for (LlmMessage msg : request.messages()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", msg.role());
            item.put("content", msg.content() != null ? msg.content() : "");
            if (msg.toolCallId() != null) {
                item.put("tool_call_id", msg.toolCallId());
            }
            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                List<Map<String, Object>> calls = new ArrayList<>();
                for (LlmToolCall tc : msg.toolCalls()) {
                    Map<String, Object> fn = new LinkedHashMap<>();
                    fn.put("name", tc.name());
                    fn.put("arguments", tc.argumentsJson() != null ? tc.argumentsJson() : "{}");
                    Map<String, Object> tcMap = new LinkedHashMap<>();
                    tcMap.put("id", tc.id());
                    tcMap.put("type", "function");
                    tcMap.put("function", fn);
                    calls.add(tcMap);
                }
                item.put("tool_calls", calls);
            }
            serializedMessages.add(item);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("temperature", 0.1);
        body.put("messages", serializedMessages);

        if (request.tools() != null && !request.tools().isEmpty()) {
            List<Map<String, Object>> serializedTools = new ArrayList<>();
            for (LlmToolSpec spec : request.tools()) {
                Map<String, Object> fn = new LinkedHashMap<>();
                fn.put("name", spec.name());
                fn.put("description", spec.description());
                fn.put("parameters", spec.parametersSchema());

                Map<String, Object> toolMap = new LinkedHashMap<>();
                toolMap.put("type", "function");
                toolMap.put("function", fn);
                serializedTools.add(toolMap);
            }
            body.put("tools", serializedTools);
            body.put("tool_choice", "auto");
        }
        return body;
    }

    private LlmResponse parseResponse(JsonNode root) {
        if (root == null || !root.has("choices") || !root.get("choices").isArray()
                || root.get("choices").isEmpty()) {
            throw new LlmProviderException("AI provider returned no completion choices.", false);
        }
        JsonNode message = root.get("choices").get(0).path("message");
        String content = message.hasNonNull("content") ? message.get("content").asText() : null;

        List<LlmToolCall> toolCalls = new ArrayList<>();
        JsonNode rawCalls = message.get("tool_calls");
        if (rawCalls != null && rawCalls.isArray()) {
            for (JsonNode tc : rawCalls) {
                String id = tc.path("id").asText("call_" + toolCalls.size());
                JsonNode fn = tc.path("function");
                String name = fn.path("name").asText("");
                String args = fn.path("arguments").asText("{}");
                if (!name.isBlank()) {
                    toolCalls.add(new LlmToolCall(id, name, args));
                }
            }
        }
        return new LlmResponse(content, List.copyOf(toolCalls));
    }
}
