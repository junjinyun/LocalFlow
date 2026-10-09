package com.localflow.domain.provider.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.GenerationRequest;
import com.localflow.domain.provider.domain.GenerationResult;
import com.localflow.domain.provider.exception.ProviderCallException;
import com.localflow.domain.provider.port.GenerationProvider;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OpenAiGenerationProvider implements GenerationProvider {
    private final AiProviderProperties.OpenAi properties;
    private final ProviderHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiGenerationProvider(AiProviderProperties properties,
                                    ProviderHttpClient httpClient,
                                    ObjectMapper objectMapper) {
        this.properties = properties.openai();
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override public AiProviderType providerType() { return AiProviderType.OPENAI; }
    @Override public boolean available() { return properties != null && properties.configured(); }
    @Override public String model() { return properties == null ? null : properties.model(); }
    @Override public List<String> models() {
        return properties == null ? List.of() : properties.selectableModels();
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        if (!available()) throw new ProviderCallException(providerType(), "OPENAI_API_KEY가 설정되지 않았습니다.");
        ObjectNode body = objectMapper.createObjectNode();
        String model = request.model() == null || request.model().isBlank() ? properties.model() : request.model();
        body.put("model", model);
        body.put("instructions", request.systemPrompt());
        body.put("input", request.userPrompt());
        body.put("store", false);
        if (properties.maxOutputTokens() > 0) body.put("max_output_tokens", properties.maxOutputTokens());
        JsonNode response = httpClient.post(providerType(), endpoint(properties.baseUrl(), "/responses"), body,
                Map.of("Authorization", "Bearer " + properties.apiKey()));
        StringBuilder text = new StringBuilder();
        for (JsonNode output : response.path("output")) {
            if (!"message".equals(output.path("type").asText())) continue;
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    if (!text.isEmpty()) text.append('\n');
                    text.append(content.path("text").asText());
                }
            }
        }
        if (text.isEmpty()) throw new ProviderCallException(providerType(), "OpenAI 응답에 텍스트가 없습니다.");
        JsonNode usage = response.path("usage");
        return new GenerationResult(text.toString(), response.path("model").asText(model),
                integerOrNull(usage.get("input_tokens")), integerOrNull(usage.get("output_tokens")));
    }

    private Integer integerOrNull(JsonNode node) { return node == null || node.isNull() ? null : node.asInt(); }
    private String endpoint(String baseUrl, String path) { return baseUrl.replaceAll("/+$", "") + path; }
}
