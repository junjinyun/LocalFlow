package com.localflow.domain.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.GenerationRequest;
import com.localflow.domain.provider.domain.GenerationResult;
import com.localflow.domain.provider.exception.StructuredOutputException;
import com.localflow.domain.provider.port.GenerationProvider;
import com.localflow.domain.provider.service.StructuredGenerationService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructuredGenerationServiceTest {
    private final StructuredGenerationService service = new StructuredGenerationService();

    @Test
    void retriesMalformedResponseAndAccumulatesTokenUsage() {
        FakeProvider provider = new FakeProvider(1,
                new GenerationResult("not-json", "qwen", 10, 3),
                new GenerationResult("{\"result\":\"ok\"}", "qwen", 8, 4));
        GenerationRequest request = new GenerationRequest("system", "user", "qwen",
                new ObjectMapper().createObjectNode().put("type", "object"));

        var result = service.generate(provider, request,
                text -> {
                    if (!text.startsWith("{")) throw new IllegalArgumentException("invalid json");
                    return text;
                }, "파일 변경 계획");

        assertThat(result.attempts()).isEqualTo(2);
        assertThat(result.value()).isEqualTo("{\"result\":\"ok\"}");
        assertThat(result.generation().inputTokens()).isEqualTo(18);
        assertThat(result.generation().outputTokens()).isEqualTo(7);
        assertThat(provider.requests).hasSize(2);
        assertThat(provider.requests.get(0).userPrompt())
                .contains("반환할 파일 변경 계획 JSON Schema", "\"type\":\"object\"");
        assertThat(provider.requests.get(1).userPrompt())
                .contains("이전 응답", "파일 변경 계획", "JSON 객체 하나만");
    }

    @Test
    void exposesReadableErrorAfterRetryLimit() {
        FakeProvider provider = new FakeProvider(1,
                new GenerationResult("invalid-1", "qwen", null, null),
                new GenerationResult("invalid-2", "qwen", null, null));

        assertThatThrownBy(() -> service.generate(provider,
                new GenerationRequest("system", "user", "qwen"),
                text -> { throw new IllegalArgumentException("invalid json"); },
                "작업 분해"))
                .isInstanceOf(StructuredOutputException.class)
                .hasMessageContaining("OLLAMA")
                .hasMessageContaining("작업 분해")
                .hasMessageContaining("2회")
                .hasMessageContaining("JSON Schema");
    }

    private static class FakeProvider implements GenerationProvider {
        private final int retries;
        private final Deque<GenerationResult> results;
        private final List<GenerationRequest> requests = new ArrayList<>();

        private FakeProvider(int retries, GenerationResult... results) {
            this.retries = retries;
            this.results = new ArrayDeque<>(List.of(results));
        }

        @Override public AiProviderType providerType() { return AiProviderType.OLLAMA; }
        @Override public boolean available() { return true; }
        @Override public String model() { return "qwen"; }
        @Override public List<String> models() { return List.of("qwen"); }
        @Override public int structuredOutputRetries() { return retries; }

        @Override
        public GenerationResult generate(GenerationRequest request) {
            requests.add(request);
            return results.removeFirst();
        }
    }
}
