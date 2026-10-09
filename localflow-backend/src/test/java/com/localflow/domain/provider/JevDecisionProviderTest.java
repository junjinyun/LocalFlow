package com.localflow.domain.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.domain.provider.domain.DecisionRequest;
import com.localflow.domain.provider.service.JevDecisionProvider;
import com.localflow.domain.provider.service.ProviderHttpClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JevDecisionProviderTest {
    @Test
    void sendsOfficialQuestionShapeAndMapsTargetLabelBackToPath() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/systemone/", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = """
                    {"model":"typesafe/jev-1.13-20260917","provider":"TypeSafe","answers":{
                      "action":{"type":"choice","choice":"MODIFY_FILES"},
                      "target":{"type":"choice","choice":"f0"},
                      "risk":{"type":"score","score":1.8},
                      "needs_human":{"type":"noul","noul":0.91},
                      "task_0_valid":{"type":"noul","noul":0.96},
                      "task_0_tag_0":{"type":"choice","choice":"t0"},
                      "task_0_tag_1":{"type":"choice","choice":"t1"}
                    }}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            ObjectMapper mapper = new ObjectMapper();
            String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/api/v1";
            AiProviderProperties properties = new AiProviderProperties(60_000, 20, null,
                    new AiProviderProperties.Jev("sk-or-test-key", baseUrl, "typesafe/jev-1.13"), null, null);
            JevDecisionProvider provider = new JevDecisionProvider(properties,
                    new ProviderHttpClient(mapper), mapper);

            var result = provider.decide(new DecisionRequest(
                    "로그인 코드를 수정", List.of("src/Auth.java"),
                    List.of("authentication", "login"), List.of("로그인 코드를 수정")));
            JsonNode request = mapper.readTree(captured.get());

            assertThat(request.path("questions").isObject()).isTrue();
            assertThat(request.path("questions").path("action").path("criteria").isObject()).isTrue();
            assertThat(request.path("questions").path("risk").path("criteria").isArray()).isTrue();
            assertThat(request.path("questions").path("task_0_tag_0").path("criteria").isObject()).isTrue();
            assertThat(request.path("model").asText()).isEqualTo("typesafe/jev-1.13");
            assertThat(authorization.get()).isEqualTo("Bearer sk-or-test-key");
            assertThat(result.actionType()).isEqualTo("MODIFY_FILES");
            assertThat(result.targetPath()).isEqualTo("src/Auth.java");
            assertThat(result.riskLevel()).isEqualTo("HIGH");
            assertThat(result.needsHumanApproval()).isTrue();
            assertThat(result.taskScopes()).hasSize(1);
            assertThat(result.taskScopes().get(0).tags()).containsExactly("authentication", "login");
        } finally {
            server.stop(0);
        }
    }
}
