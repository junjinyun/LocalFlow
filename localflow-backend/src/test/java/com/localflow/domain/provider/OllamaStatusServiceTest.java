package com.localflow.domain.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.domain.provider.domain.OllamaStatus;
import com.localflow.domain.provider.service.OllamaStatusService;
import com.localflow.domain.provider.service.ProviderHttpClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OllamaStatusServiceTest {
    private final AtomicReference<String> probeRequestBody = new AtomicReference<>();
    @Test
    void reportsAvailableAndCachesInstalledModels() throws Exception {
        AtomicInteger versionCalls = new AtomicInteger();
        AtomicInteger tagsCalls = new AtomicInteger();
        HttpServer server = server(
                versionCalls, tagsCalls,
                200, """
                        {"models":[
                          {"name":"qwen2.5-coder:3b","model":"qwen2.5-coder:3b"},
                          {"name":"qwen3:8b","model":"qwen3:8b"}
                        ]}
                        """);
        try {
            OllamaStatusService service = service(true, url(server), "qwen2.5-coder:3b");

            OllamaStatus first = service.status(false);
            OllamaStatus cached = service.status(false);

            assertThat(first.configured()).isTrue();
            assertThat(first.reachable()).isTrue();
            assertThat(first.modelInstalled()).isTrue();
            assertThat(first.available()).isTrue();
            assertThat(first.models()).containsExactly("qwen2.5-coder:3b", "qwen3:8b");
            assertThat(cached).isEqualTo(first);
            assertThat(versionCalls).hasValue(1);
            assertThat(tagsCalls).hasValue(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void distinguishesMissingModelFromUnreachableServer() throws Exception {
        HttpServer server = server(new AtomicInteger(), new AtomicInteger(),
                200, """
                        {"models":[{"name":"qwen3:8b"}]}
                        """);
        try {
            OllamaStatus missing = service(true, url(server), "qwen2.5-coder:3b").status(false);
            OllamaStatus unreachable = service(true, "http://127.0.0.1:1",
                    "qwen2.5-coder:3b").status(false);

            assertThat(missing.configured()).isTrue();
            assertThat(missing.reachable()).isTrue();
            assertThat(missing.modelInstalled()).isFalse();
            assertThat(missing.available()).isFalse();
            assertThat(missing.message()).contains("설치되지 않았습니다");

            assertThat(unreachable.configured()).isTrue();
            assertThat(unreachable.reachable()).isFalse();
            assertThat(unreachable.modelInstalled()).isFalse();
            assertThat(unreachable.available()).isFalse();
            assertThat(unreachable.message()).contains("연결할 수 없습니다");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void providerStatusFailureReturnsUnavailableInsteadOfThrowing() throws Exception {
        HttpServer server = server(new AtomicInteger(), new AtomicInteger(), 500, "{}");
        try {
            OllamaStatus status = service(true, url(server), "qwen2.5-coder:3b").status(false);

            assertThat(status.configured()).isTrue();
            assertThat(status.reachable()).isTrue();
            assertThat(status.modelInstalled()).isFalse();
            assertThat(status.available()).isFalse();
            assertThat(status.message()).contains("목록을 불러오지 못했습니다");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void explicitRefreshBypassesCache() throws Exception {
        AtomicInteger versionCalls = new AtomicInteger();
        AtomicInteger tagsCalls = new AtomicInteger();
        HttpServer server = server(versionCalls, tagsCalls,
                200, """
                        {"models":[{"name":"qwen2.5-coder:3b"}]}
                        """);
        try {
            OllamaStatusService service = service(true, url(server), "qwen2.5-coder:3b");

            service.status(false);
            service.status(true);

            assertThat(versionCalls).hasValue(2);
            assertThat(tagsCalls).hasValue(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void explicitRefreshUsesProbeModelWhileWorkModelRemainsSeparate() throws Exception {
        HttpServer server = server(new AtomicInteger(), new AtomicInteger(), 200, """
                {"models":[
                  {"name":"qwen2.5-coder:3b"},
                  {"name":"qwen3.5:4b-q4_K_M"}
                ]}
                """);
        try {
            OllamaStatus status = service(true, url(server),
                    "qwen3.5:4b-q4_K_M", "qwen2.5-coder:3b").status(true);

            JsonNode request = new ObjectMapper().readTree(probeRequestBody.get());
            assertThat(status.available()).isTrue();
            assertThat(status.message()).contains("qwen2.5-coder:3b", "qwen3.5:4b-q4_K_M");
            assertThat(request.path("model").asText()).isEqualTo("qwen2.5-coder:3b");
            assertThat(request.path("options").path("num_predict").asInt()).isEqualTo(8);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void disabledProviderDoesNotContactServer() {
        OllamaStatus status = service(false, "http://127.0.0.1:1",
                "qwen2.5-coder:3b").status(false);

        assertThat(status.configured()).isFalse();
        assertThat(status.reachable()).isFalse();
        assertThat(status.available()).isFalse();
        assertThat(status.message()).contains("OLLAMA_ENABLED");
    }

    private OllamaStatusService service(boolean enabled, String baseUrl, String model) {
        return service(enabled, baseUrl, model, model);
    }

    private OllamaStatusService service(boolean enabled, String baseUrl, String model,
                                        String probeModel) {
        AiProviderProperties properties = new AiProviderProperties(
                60_000, 20, null, null,
                new AiProviderProperties.Ollama(
                        enabled, baseUrl, model, probeModel,
                        Duration.ofSeconds(30), Duration.ofMillis(500)),
                null);
        return new OllamaStatusService(properties, new ProviderHttpClient(new ObjectMapper()));
    }

    private HttpServer server(AtomicInteger versionCalls, AtomicInteger tagsCalls,
                              int tagsStatus, String tagsBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/version", exchange -> {
            versionCalls.incrementAndGet();
            respond(exchange, 200, "{\"version\":\"0.40.2\"}");
        });
        server.createContext("/api/tags", exchange -> {
            tagsCalls.incrementAndGet();
            respond(exchange, tagsStatus, tagsBody);
        });
        server.createContext("/api/generate", exchange -> {
            probeRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"model\":\"qwen2.5-coder:3b\",\"response\":\"OK\",\"done\":true}");
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
