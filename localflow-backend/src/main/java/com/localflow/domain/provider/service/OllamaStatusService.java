package com.localflow.domain.provider.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.OllamaStatus;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class OllamaStatusService {
    private final AiProviderProperties.Ollama properties;
    private final ProviderHttpClient httpClient;
    private volatile CachedStatus cachedStatus;

    public OllamaStatusService(AiProviderProperties properties, ProviderHttpClient httpClient) {
        this.properties = properties.ollama();
        this.httpClient = httpClient;
    }

    public OllamaStatus status(boolean refresh) {
        long now = System.nanoTime();
        CachedStatus cached = cachedStatus;
        if (!refresh && cached != null && now < cached.expiresAtNanos()) return cached.status();

        synchronized (this) {
            now = System.nanoTime();
            cached = cachedStatus;
            if (!refresh && cached != null && now < cached.expiresAtNanos()) return cached.status();
            OllamaStatus status = inspect();
            cachedStatus = new CachedStatus(status, now + cacheTtl().toNanos());
            return status;
        }
    }

    private OllamaStatus inspect() {
        if (properties == null || !properties.enabled()) {
            return unavailable("OLLAMA_ENABLED가 비활성화되어 있습니다.");
        }
        if (!properties.configured()) {
            return unavailable("OLLAMA_BASE_URL 또는 OLLAMA_MODEL 설정을 확인해 주세요.");
        }

        String baseUrl = properties.baseUrl().replaceAll("/+$", "");
        try {
            JsonNode version = httpClient.get(AiProviderType.OLLAMA, baseUrl + "/api/version",
                    Map.of(), properties.statusTimeout());
            if (version.path("version").asText().isBlank()) {
                return new OllamaStatus(true, false, false, false,
                        "Ollama 서버 버전을 확인할 수 없습니다.", List.of());
            }
        } catch (RuntimeException exception) {
            return new OllamaStatus(true, false, false, false,
                    "Ollama 서버에 연결할 수 없습니다. 실행 상태와 OLLAMA_BASE_URL을 확인해 주세요.",
                    List.of());
        }

        try {
            JsonNode response = httpClient.get(AiProviderType.OLLAMA, baseUrl + "/api/tags",
                    Map.of(), properties.statusTimeout());
            List<String> models = installedModels(response);
            boolean installed = models.stream().anyMatch(this::isConfiguredModel);
            String message = installed
                    ? "Ollama 서버와 모델을 사용할 수 있습니다."
                    : "설정 모델 '" + properties.model() + "'이 설치되지 않았습니다.";
            return new OllamaStatus(true, true, installed, installed, message, models);
        } catch (RuntimeException exception) {
            return new OllamaStatus(true, true, false, false,
                    "Ollama 설치 모델 목록을 불러오지 못했습니다.", List.of());
        }
    }

    private List<String> installedModels(JsonNode response) {
        Set<String> names = new LinkedHashSet<>();
        JsonNode models = response.path("models");
        if (!models.isArray()) return List.of();
        for (JsonNode model : models) {
            String name = model.path("name").asText();
            if (name.isBlank()) name = model.path("model").asText();
            if (!name.isBlank()) names.add(name);
        }
        return List.copyOf(names);
    }

    private boolean isConfiguredModel(String installedModel) {
        return normalizedModel(installedModel).equals(normalizedModel(properties.model()));
    }

    private String normalizedModel(String model) {
        String value = model == null ? "" : model.strip();
        return value.endsWith(":latest") ? value.substring(0, value.length() - 7) : value;
    }

    private OllamaStatus unavailable(String message) {
        return new OllamaStatus(false, false, false, false, message, List.of());
    }

    private Duration cacheTtl() {
        return properties == null ? Duration.ofSeconds(5) : properties.statusCacheTtl();
    }

    private record CachedStatus(OllamaStatus status, long expiresAtNanos) {
    }
}
