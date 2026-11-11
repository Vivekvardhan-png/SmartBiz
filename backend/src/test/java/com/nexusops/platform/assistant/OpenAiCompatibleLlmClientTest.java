package com.nexusops.platform.assistant;

import com.nexusops.platform.assistant.LlmClient.LlmMessage;
import com.nexusops.platform.assistant.LlmClient.LlmProviderException;
import com.nexusops.platform.assistant.LlmClient.LlmRequest;
import com.nexusops.platform.assistant.LlmClient.LlmResponse;
import com.nexusops.platform.assistant.LlmClient.LlmToolSpec;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OpenAiCompatibleLlmClient HTTP & Timeout Behavior")
class OpenAiCompatibleLlmClientTest {

    @Test
    @DisplayName("Parses assistant tool calls and sends backend bearer authorization without exposing keys")
    void sendsAuthHeaderAndParsesToolCalls() throws Exception {
        AtomicReference<String> authHeader = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resp = """
                    {
                      "choices": [
                        {
                          "message": {
                            "role": "assistant",
                            "content": null,
                            "tool_calls": [
                              {
                                "id": "call_1",
                                "type": "function",
                                "function": {
                                  "name": "list_low_stock_products",
                                  "arguments": "{\\"limit\\":5}"
                                }
                              }
                            ]
                          }
                        }
                      ]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            AiAssistantProperties props = new AiAssistantProperties();
            props.setEnabled(true);
            props.setApiKey("secret-provider-key-xyz");
            props.setProviderBaseUrl("http://127.0.0.1:" + port + "/v1");
            props.setModel("gpt-4o-mini");

            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(props);
            LlmResponse response = client.complete(new LlmRequest(
                    List.of(LlmMessage.system("Rules"), LlmMessage.user("Low stock?")),
                    List.of(new LlmToolSpec("list_low_stock_products", "List low stock", Map.of("type", "object")))
            ));

            assertThat(authHeader.get()).isEqualTo("Bearer secret-provider-key-xyz");
            assertThat(requestBody.get()).contains("list_low_stock_products");
            assertThat(response.toolCalls()).hasSize(1);
            assertThat(response.toolCalls().get(0).name()).isEqualTo("list_low_stock_products");
            assertThat(response.toolCalls().get(0).argumentsJson()).contains("5");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Maps upstream HTTP errors and read timeouts to sanitized LlmProviderException without leaking secrets")
    void handlesProviderFailuresAndTimeoutsSafely() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String pathQuery = exchange.getRequestURI().toString();
            if (pathQuery.contains("fail")) {
                byte[] err = "{\"error\":\"upstream overloaded\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(502, err.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(err);
                }
            } else {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                byte[] ok = "{\"choices\":[]}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, ok.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(ok);
                }
            }
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            AiAssistantProperties props = new AiAssistantProperties();
            props.setEnabled(true);
            props.setApiKey("top-secret-api-key-999");
            props.setProviderBaseUrl("http://127.0.0.1:" + port + "/v1");
            props.setConnectTimeoutMs(500);
            props.setReadTimeoutMs(150);

            OpenAiCompatibleLlmClient timeoutClient = new OpenAiCompatibleLlmClient(props);
            assertThatThrownBy(() -> timeoutClient.complete(new LlmRequest(
                    List.of(LlmMessage.user("Hi")), List.of())))
                    .isInstanceOf(LlmProviderException.class)
                    .hasMessageNotContaining("top-secret-api-key-999");
        } finally {
            server.stop(0);
        }
    }
}
