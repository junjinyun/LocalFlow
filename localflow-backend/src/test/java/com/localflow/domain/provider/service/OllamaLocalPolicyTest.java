package com.localflow.domain.provider.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OllamaLocalPolicyTest {
    @Test
    void allowsLoopbackIpv4Ipv6AndLocalModel() {
        assertThatCode(() -> policy("http://localhost:11434").validate("qwen2.5-coder:3b"))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy("http://127.8.9.10:11434").validate("qwen3:8b"))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy("http://[::1]:11434").validate("local/model:latest"))
                .doesNotThrowAnyException();
    }

    @Test
    void blocksRemoteOllamaBeforeSendingProjectData() {
        assertDenied(() -> policy("https://ollama.example.com").validate("qwen2.5-coder:3b"));
        assertDenied(() -> policy("http://192.168.0.20:11434").validate("qwen2.5-coder:3b"));
    }

    @Test
    void blocksCloudModelEvenOnLoopbackEndpoint() {
        assertDenied(() -> policy("http://localhost:11434").validate("gpt-oss:120b-cloud"));
        assertDenied(() -> policy("http://localhost:11434").validate("model:cloud"));
    }

    private OllamaLocalPolicy policy(String baseUrl) {
        AiProviderProperties properties = new AiProviderProperties(32_000, 20, null, null,
                new AiProviderProperties.Ollama(
                        true, baseUrl, "qwen2.5-coder:3b",
                        Duration.ofSeconds(5), Duration.ofSeconds(2)),
                null);
        return new OllamaLocalPolicy(properties);
    }

    private void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(CustomException.class)
                .satisfies(value -> org.assertj.core.api.Assertions.assertThat(
                        ((CustomException) value).errorCode()).isEqualTo(ErrorCode.REMOTE_OLLAMA_DENIED));
    }
}
