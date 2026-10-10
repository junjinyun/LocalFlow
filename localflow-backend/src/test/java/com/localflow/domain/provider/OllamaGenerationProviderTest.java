package com.localflow.domain.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.domain.provider.domain.GenerationRequest;
import com.localflow.domain.provider.exception.ProviderCallException;
import com.localflow.domain.provider.service.OllamaExecutionCoordinator;
import com.localflow.domain.provider.service.OllamaGenerationProvider;
import com.localflow.domain.provider.service.OllamaStatusService;
import com.localflow.domain.provider.service.ProviderHttpClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OllamaGenerationProviderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendsJsonSchemaAndDeterministicOptionsToChatApi() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = server(requestBody);
        try {
            AiProviderProperties properties = properties(url(server));
            ProviderHttpClient httpClient = new ProviderHttpClient(objectMapper);
            OllamaGenerationProvider provider = new OllamaGenerationProvider(
                    properties, httpClient, objectMapper,
                    new OllamaStatusService(properties, httpClient),
                    new OllamaExecutionCoordinator(properties));
            JsonNode schema = objectMapper.readTree("""
                    {"type":"object","required":["summary"],
                     "properties":{"summary":{"type":"string"}}}
                    """);

            var result = provider.generate(new GenerationRequest(
                    "system prompt", "user prompt", null, schema));

            JsonNode sent = objectMapper.readTree(requestBody.get());
            assertThat(sent.path("model").asText()).isEqualTo("qwen2.5-coder:3b");
            assertThat(sent.path("stream").asBoolean()).isFalse();
            assertThat(sent.path("think").asBoolean()).isFalse();
            assertThat(sent.path("keep_alive").asText()).isEqualTo("10m");
            assertThat(sent.path("format")).isEqualTo(schema);
            assertThat(sent.path("options").path("temperature").asDouble()).isZero();
            assertThat(sent.path("options").path("seed").asInt()).isEqualTo(42);
            assertThat(sent.path("options").path("num_predict").asInt()).isEqualTo(4096);
            assertThat(sent.path("options").path("num_ctx").asInt()).isEqualTo(16384);
            assertThat(sent.path("messages").get(0).path("role").asText()).isEqualTo("system");
            assertThat(sent.path("messages").get(1).path("content").asText()).isEqualTo("user prompt");
            assertThat(result.text()).isEqualTo("{\"summary\":\"완료\"}");
            assertThat(result.inputTokens()).isEqualTo(11);
            assertThat(result.outputTokens()).isEqualTo(7);
            assertThat(provider.structuredOutputRetries()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void usesOllamaSpecificRequestTimeout() throws Exception {
        HttpServer server = slowServer();
        try {
            AiProviderProperties properties = properties(url(server), Duration.ofMillis(80));
            ProviderHttpClient httpClient = new ProviderHttpClient(objectMapper);
            OllamaGenerationProvider provider = new OllamaGenerationProvider(
                    properties, httpClient, objectMapper,
                    new OllamaStatusService(properties, httpClient),
                    new OllamaExecutionCoordinator(properties));

            assertThatThrownBy(() -> provider.generate(
                    new GenerationRequest("system", "user", "qwen2.5-coder:3b")))
                    .isInstanceOf(ProviderCallException.class)
                    .hasMessageContaining("AI 제공자 요청에 실패했습니다");
        } finally {
            server.stop(0);
        }
    }

    private AiProviderProperties properties(String baseUrl) {
        return properties(baseUrl, Duration.ofMinutes(10));
    }

    private AiProviderProperties properties(String baseUrl, Duration requestTimeout) {
        return new AiProviderProperties(60_000, 20, null, null,
                new AiProviderProperties.Ollama(
                        true, baseUrl, "qwen2.5-coder:3b",
                        Duration.ofSeconds(30), Duration.ofSeconds(1),
                        0.0, 42, false, 4_096, 16_384, "10m",
                        requestTimeout, Duration.ofSeconds(1), 1, 1),
                null);
    }

    private HttpServer server(AtomicReference<String> requestBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/version", exchange ->
                respond(exchange, 200, "{\"version\":\"0.40.2\"}"));
        server.createContext("/api/tags", exchange ->
                respond(exchange, 200, "{\"models\":[{\"name\":\"qwen2.5-coder:3b\"}]}"));
        server.createContext("/api/chat", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {"model":"qwen2.5-coder:3b","message":{"role":"assistant",
                     "content":"{\\\"summary\\\":\\\"완료\\\"}"},
                     "prompt_eval_count":11,"eval_count":7}
                    """);
        });
        server.start();
        return server;
    }

    private HttpServer slowServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/version", exchange ->
                respond(exchange, 200, "{\"version\":\"0.40.2\"}"));
        server.createContext("/api/tags", exchange ->
                respond(exchange, 200, "{\"models\":[{\"name\":\"qwen2.5-coder:3b\"}]}"));
        server.createContext("/api/chat", exchange -> {
            try {
                Thread.sleep(400);
                respond(exchange, 200, "{\"message\":{\"content\":\"{}\"}}");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        return server;
    }

    private String url(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
