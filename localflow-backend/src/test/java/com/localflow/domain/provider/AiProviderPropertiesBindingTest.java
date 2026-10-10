package com.localflow.domain.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.localflow.domain.provider.config.AiProviderProperties;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AiProviderPropertiesBindingTest {
    @Test
    void bindsExtendedOllamaSettingsWithConvenienceConstructorsPresent() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.ofEntries(
                Map.entry("localflow.ai.context-max-chars", "32000"),
                Map.entry("localflow.ai.max-operations", "20"),
                Map.entry("localflow.ai.ollama.enabled", "true"),
                Map.entry("localflow.ai.ollama.base-url", "http://localhost:11434"),
                Map.entry("localflow.ai.ollama.model", "qwen3.5:4b-q4_K_M"),
                Map.entry("localflow.ai.ollama.probe-model", "qwen2.5-coder:3b"),
                Map.entry("localflow.ai.ollama.status-cache-ttl", "5s"),
                Map.entry("localflow.ai.ollama.status-timeout", "2s"),
                Map.entry("localflow.ai.ollama.temperature", "0"),
                Map.entry("localflow.ai.ollama.seed", "42"),
                Map.entry("localflow.ai.ollama.think", "false"),
                Map.entry("localflow.ai.ollama.num-predict", "4096"),
                Map.entry("localflow.ai.ollama.num-ctx", "16384"),
                Map.entry("localflow.ai.ollama.keep-alive", "10m"),
                Map.entry("localflow.ai.ollama.request-timeout", "10m"),
                Map.entry("localflow.ai.ollama.queue-timeout", "15m"),
                Map.entry("localflow.ai.ollama.max-concurrent-requests", "1"),
                Map.entry("localflow.ai.ollama.structured-output-retries", "1")
        )));

        AiProviderProperties properties = binder.bind(
                "localflow.ai", Bindable.of(AiProviderProperties.class)).get();

        assertThat(properties.ollama()).isNotNull();
        assertThat(properties.ollama().enabled()).isTrue();
        assertThat(properties.ollama().baseUrl()).isEqualTo("http://localhost:11434");
        assertThat(properties.ollama().model()).isEqualTo("qwen3.5:4b-q4_K_M");
        assertThat(properties.ollama().probeModel()).isEqualTo("qwen2.5-coder:3b");
        assertThat(properties.ollama().numCtx()).isEqualTo(16_384);
        assertThat(properties.ollama().requestTimeout()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.ollama().maxConcurrentRequests()).isEqualTo(1);
    }

    @Test
    void bindsGeminiCliSettings() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.ofEntries(
                Map.entry("localflow.ai.context-max-chars", "32000"),
                Map.entry("localflow.ai.max-operations", "20"),
                Map.entry("localflow.ai.gemini-cli.enabled", "true"),
                Map.entry("localflow.ai.gemini-cli.command", "gemini.cmd"),
                Map.entry("localflow.ai.gemini-cli.model", "auto"),
                Map.entry("localflow.ai.gemini-cli.models", "auto,gemini-2.5-flash"),
                Map.entry("localflow.ai.gemini-cli.status-cache-ttl", "10s"),
                Map.entry("localflow.ai.gemini-cli.status-timeout", "5s"),
                Map.entry("localflow.ai.gemini-cli.request-timeout", "10m"),
                Map.entry("localflow.ai.gemini-cli.queue-timeout", "15m"),
                Map.entry("localflow.ai.gemini-cli.max-concurrent-requests", "1"),
                Map.entry("localflow.ai.gemini-cli.structured-output-retries", "1")
        )));

        AiProviderProperties properties = binder.bind(
                "localflow.ai", Bindable.of(AiProviderProperties.class)).get();

        assertThat(properties.geminiCli()).isNotNull();
        assertThat(properties.geminiCli().configured()).isTrue();
        assertThat(properties.geminiCli().command()).isEqualTo("gemini.cmd");
        assertThat(properties.geminiCli().selectableModels())
                .containsExactly("auto", "gemini-2.5-flash");
        assertThat(properties.geminiCli().requestTimeout()).isEqualTo(Duration.ofMinutes(10));
    }
}
