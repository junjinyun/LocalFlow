package com.localflow.domain.provider.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.GenerationRequest;
import com.localflow.domain.provider.domain.GenerationResult;
import com.localflow.domain.provider.exception.ProviderCallException;
import com.localflow.domain.provider.port.GenerationProvider;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OllamaGenerationProvider implements GenerationProvider {
    private final AiProviderProperties.Ollama properties;
    private final ProviderHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaGenerationProvider(AiProviderProperties properties,
                                    ProviderHttpClient httpClient,
                                    ObjectMapper objectMapper) {
        this.properties = properties.ollama();
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override public AiProviderType providerType() { return AiProviderType.OLLAMA; }
    @Override public boolean available() { return properties != null && properties.configured(); }
    @Override public String model() { return properties == null ? null : properties.model(); }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        if (!available()) throw new ProviderCallException(providerType(), "OLLAMA_BASE_URL 또는 OLLAMA_MODEL이 없습니다.");
        ObjectNode body = objectMapper.createObjectNode();
        String model = request.model() == null || request.model().isBlank() ? properties.model() : request.model();
        body.put("model", model);
        body.put("stream", false);
        ArrayNode messages = body.putArray("messages");
        messages.add(message("system", request.systemPrompt()));
        messages.add(message("user", request.userPrompt()));
        JsonNode response = httpClient.post(providerType(),
                properties.baseUrl().replaceAll("/+$", "") + "/api/chat", body, Map.of());
        String text = response.path("message").path("content").asText();
        if (text.isBlank()) throw new ProviderCallException(providerType(), "Ollama 응답에 텍스트가 없습니다.");
        return new GenerationResult(text, response.path("model").asText(model),
                integerOrNull(response.get("prompt_eval_count")), integerOrNull(response.get("eval_count")));
    }

    private ObjectNode message(String role, String content) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private Integer integerOrNull(JsonNode node) { return node == null || node.isNull() ? null : node.asInt(); }
}
