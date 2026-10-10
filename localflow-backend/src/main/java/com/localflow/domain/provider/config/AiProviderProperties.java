package com.localflow.domain.provider.config;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "localflow.ai")
public record AiProviderProperties(
        int contextMaxChars,
        int maxOperations,
        OpenAi openai,
        Jev jev,
        Ollama ollama,
        VertexAi vertexAi
) {
    public AiProviderProperties {
        contextMaxChars = contextMaxChars <= 0 ? 60_000 : contextMaxChars;
        maxOperations = maxOperations <= 0 ? 20 : maxOperations;
    }

    public record OpenAi(String apiKey, String baseUrl, String model, List<String> models, int maxOutputTokens) {
        public boolean configured() { return apiKey != null && !apiKey.isBlank(); }
        public List<String> selectableModels() { return mergeModels(model, models); }
    }

    public record Jev(String apiKey, String baseUrl, String model) {
        public boolean configured() { return apiKey != null && !apiKey.isBlank(); }
    }

    public record Ollama(boolean enabled, String baseUrl, String model,
                         Duration statusCacheTtl, Duration statusTimeout,
                         double temperature, int seed, boolean think,
                         int numPredict, String keepAlive, int structuredOutputRetries) {
        public Ollama(boolean enabled, String baseUrl, String model,
                      Duration statusCacheTtl, Duration statusTimeout) {
            this(enabled, baseUrl, model, statusCacheTtl, statusTimeout,
                    0.0, 42, false, 4_096, "10m", 1);
        }

        public Ollama {
            statusCacheTtl = statusCacheTtl == null || statusCacheTtl.isNegative()
                    ? Duration.ofSeconds(5) : statusCacheTtl;
            statusTimeout = statusTimeout == null || statusTimeout.isNegative() || statusTimeout.isZero()
                    ? Duration.ofSeconds(2) : statusTimeout;
            temperature = temperature < 0 ? 0.0 : temperature;
            numPredict = numPredict <= 0 ? 4_096 : numPredict;
            keepAlive = keepAlive == null || keepAlive.isBlank() ? "10m" : keepAlive.strip();
            structuredOutputRetries = Math.max(0, Math.min(structuredOutputRetries, 2));
        }

        public boolean configured() {
            return enabled && baseUrl != null && !baseUrl.isBlank()
                    && model != null && !model.isBlank();
        }
    }

    public record VertexAi(String credentialsBase64, String projectId, String location, String model,
                           List<String> models) {
        public boolean configured() {
            return credentialsBase64 != null && !credentialsBase64.isBlank()
                    && projectId != null && !projectId.isBlank();
        }
        public List<String> selectableModels() { return mergeModels(model, models); }
    }

    private static List<String> mergeModels(String defaultModel, List<String> configuredModels) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (defaultModel != null && !defaultModel.isBlank()) values.add(defaultModel.strip());
        if (configuredModels != null) configuredModels.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .forEach(values::add);
        return List.copyOf(values);
    }
}
