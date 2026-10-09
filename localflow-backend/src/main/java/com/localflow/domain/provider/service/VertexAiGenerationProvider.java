package com.localflow.domain.provider.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.GenerationRequest;
import com.localflow.domain.provider.domain.GenerationResult;
import com.localflow.domain.provider.exception.ProviderCallException;
import com.localflow.domain.provider.port.GenerationProvider;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class VertexAiGenerationProvider implements GenerationProvider {
    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    private final AiProviderProperties.VertexAi properties;
    private final ProviderHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public VertexAiGenerationProvider(AiProviderProperties properties,
                                      ProviderHttpClient httpClient,
                                      ObjectMapper objectMapper) {
        this.properties = properties.vertexAi();
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override public AiProviderType providerType() { return AiProviderType.VERTEX_AI; }

    @Override
    public boolean available() {
        if (properties == null || !properties.configured()) return false;
        try {
            serviceAccountCredentials();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override public String model() { return properties == null ? null : properties.model(); }
    @Override public List<String> models() {
        return properties == null ? List.of() : properties.selectableModels();
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        if (!available()) {
            throw new ProviderCallException(providerType(),
                    "VERTEX_AI_SERVICE_ACCOUNT_BASE64와 VERTEX_AI_PROJECT 설정을 확인해 주세요.");
        }
        String model = request.model() == null || request.model().isBlank() ? properties.model() : request.model();
        String token = accessToken();
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode systemInstruction = body.putObject("systemInstruction");
        systemInstruction.put("role", "system");
        systemInstruction.putArray("parts").addObject().put("text", request.systemPrompt());
        ArrayNode contents = body.putArray("contents");
        ObjectNode user = contents.addObject();
        user.put("role", "user");
        user.putArray("parts").addObject().put("text", request.userPrompt());

        JsonNode response = httpClient.post(providerType(), endpoint(model), body,
                Map.of("Authorization", "Bearer " + token));
        StringBuilder text = new StringBuilder();
        for (JsonNode candidate : response.path("candidates")) {
            for (JsonNode part : candidate.path("content").path("parts")) {
                String value = part.path("text").asText();
                if (!value.isBlank()) {
                    if (!text.isEmpty()) text.append('\n');
                    text.append(value);
                }
            }
            if (!text.isEmpty()) break;
        }
        if (text.isEmpty()) throw new ProviderCallException(providerType(), "Vertex AI 응답에 텍스트가 없습니다.");
        JsonNode usage = response.path("usageMetadata");
        return new GenerationResult(text.toString(), model,
                integerOrNull(usage.get("promptTokenCount")),
                integerOrNull(usage.get("candidatesTokenCount")));
    }

    private String accessToken() {
        try {
            GoogleCredentials credentials = serviceAccountCredentials()
                    .createScoped(List.of(CLOUD_PLATFORM_SCOPE));
            credentials.refreshIfExpired();
            AccessToken token = credentials.getAccessToken();
            if (token == null || token.getTokenValue() == null) {
                credentials.refresh();
                token = credentials.getAccessToken();
            }
            return token.getTokenValue();
        } catch (Exception exception) {
            throw new ProviderCallException(providerType(),
                    "Vertex AI 서비스 계정 인증에 실패했습니다. Base64 값과 서비스 계정 권한을 확인해 주세요.",
                    exception);
        }
    }

    private ServiceAccountCredentials serviceAccountCredentials() {
        try {
            byte[] json = Base64.getMimeDecoder().decode(properties.credentialsBase64().strip());
            if (json.length == 0 || json.length > 65_536) {
                throw new IllegalArgumentException("서비스 계정 JSON 크기가 올바르지 않습니다.");
            }
            try (ByteArrayInputStream input = new ByteArrayInputStream(json)) {
                return ServiceAccountCredentials.fromStream(input);
            }
        } catch (Exception exception) {
            throw new ProviderCallException(providerType(),
                    "VERTEX_AI_SERVICE_ACCOUNT_BASE64가 올바른 서비스 계정 JSON의 Base64 값이 아닙니다.",
                    exception);
        }
    }

    private String endpoint(String model) {
        String location = properties.location();
        return "https://" + location + "-aiplatform.googleapis.com/v1/projects/"
                + properties.projectId() + "/locations/" + location
                + "/publishers/google/models/" + model + ":generateContent";
    }

    private Integer integerOrNull(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() ? null : node.asInt();
    }
}
