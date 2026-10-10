package com.localflow.domain.provider.config;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

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
        contextMaxChars = contextMaxChars <= 0 ? 32_000 : contextMaxChars;
        maxOperations = maxOperations <= 0 ? 20 : maxOperations;
    }

    public record OpenAi(String apiKey, String baseUrl, String model, List<String> models, int maxOutputTokens) {
        public boolean configured() { return apiKey != null && !apiKey.isBlank(); }
        public List<String> selectableModels() { return mergeModels(model, models); }
    }

    public record Jev(String apiKey, String baseUrl, String model) {
        public boolean configured() { return apiKey != null && !apiKey.isBlank(); }
    }

    public record Ollama(boolean enabled, String baseUrl, String model, String probeModel,
                         Duration statusCacheTtl, Duration statusTimeout,
                         double temperature, int seed, boolean think,
                         int numPredict, int numCtx, String keepAlive,
                         Duration requestTimeout, Duration queueTimeout,
                         int maxConcurrentRequests, int structuredOutputRetries) {
        public Ollama(boolean enabled, String baseUrl, String model,
                      Duration statusCacheTtl, Duration statusTimeout) {
            this(enabled, baseUrl, model, model, statusCacheTtl, statusTimeout,
                    0.0, 42, false, 4_096, 16_384, "10m",
                    Duration.ofMinutes(10), Duration.ofMinutes(15), 1, 1);
        }

        public Ollama(boolean enabled, String baseUrl, String model, String probeModel,
                      Duration statusCacheTtl, Duration statusTimeout) {
            this(enabled, baseUrl, model, probeModel, statusCacheTtl, statusTimeout,
                    0.0, 42, false, 4_096, 16_384, "10m",
                    Duration.ofMinutes(10), Duration.ofMinutes(15), 1, 1);
        }

        public Ollama(boolean enabled, String baseUrl, String model,
                      Duration statusCacheTtl, Duration statusTimeout,
                      double temperature, int seed, boolean think,
                      int numPredict, String keepAlive, int structuredOutputRetries) {
            this(enabled, baseUrl, model, model, statusCacheTtl, statusTimeout,
                    temperature, seed, think, numPredict, 16_384, keepAlive,
                    Duration.ofMinutes(10), Duration.ofMinutes(15), 1,
                    structuredOutputRetries);
        }

        public Ollama(boolean enabled, String baseUrl, String model,
                      Duration statusCacheTtl, Duration statusTimeout,
                      double temperature, int seed, boolean think,
                      int numPredict, int numCtx, String keepAlive,
                      Duration requestTimeout, Duration queueTimeout,
                      int maxConcurrentRequests, int structuredOutputRetries) {
            this(enabled, baseUrl, model, model, statusCacheTtl, statusTimeout,
                    temperature, seed, think, numPredict, numCtx, keepAlive,
                    requestTimeout, queueTimeout, maxConcurrentRequests,
                    structuredOutputRetries);
        }

        @ConstructorBinding
        public Ollama {
            probeModel = probeModel == null || probeModel.isBlank() ? model : probeModel.strip();
            statusCacheTtl = statusCacheTtl == null || statusCacheTtl.isNegative()
                    ? Duration.ofSeconds(5) : statusCacheTtl;
            statusTimeout = statusTimeout == null || statusTimeout.isNegative() || statusTimeout.isZero()
                    ? Duration.ofSeconds(2) : statusTimeout;
            temperature = temperature < 0 ? 0.0 : temperature;
            numPredict = numPredict <= 0 ? 4_096 : numPredict;
            numCtx = numCtx <= 0 ? 16_384 : numCtx;
            keepAlive = keepAlive == null || keepAlive.isBlank() ? "10m" : keepAlive.strip();
            requestTimeout = positive(requestTimeout, Duration.ofMinutes(10));
            queueTimeout = positive(queueTimeout, Duration.ofMinutes(15));
            maxConcurrentRequests = Math.max(1, Math.min(maxConcurrentRequests, 4));
            structuredOutputRetries = Math.max(0, Math.min(structuredOutputRetries, 2));
        }

        public boolean configured() {
            return enabled && baseUrl != null && !baseUrl.isBlank()
                    && model != null && !model.isBlank()
                    && probeModel != null && !probeModel.isBlank();
        }

        private static Duration positive(Duration value, Duration fallback) {
            return value == null || value.isNegative() || value.isZero() ? fallback : value;
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
