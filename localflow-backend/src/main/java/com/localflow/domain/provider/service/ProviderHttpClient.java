package com.localflow.domain.provider.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.exception.ProviderCallException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProviderHttpClient {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public ProviderHttpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode post(AiProviderType providerType, String url, JsonNode body,
                         Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            headers.forEach(builder::header);
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ProviderCallException(providerType,
                        "HTTP " + response.statusCode() + ": " + abbreviate(response.body()));
            }
            return objectMapper.readTree(response.body());
        } catch (ProviderCallException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ProviderCallException(providerType,
                    "AI 제공자 요청에 실패했습니다: " + exception.getMessage(), exception);
        }
    }

    private String abbreviate(String value) {
        if (value == null) return "응답 본문 없음";
        return value.length() <= 800 ? value : value.substring(0, 800) + "…";
    }
}
