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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ProviderHttpClient {
    private static final Logger log = LoggerFactory.getLogger(ProviderHttpClient.class);
    private final ObjectMapper objectMapper;
    private final ProviderErrorMapper errorMapper = new ProviderErrorMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public ProviderHttpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode post(AiProviderType providerType, String url, JsonNode body,
                         Map<String, String> headers) {
        return post(providerType, url, body, headers, Duration.ofMinutes(3));
    }

    public JsonNode post(AiProviderType providerType, String url, JsonNode body,
                         Map<String, String> headers, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(writeBody(providerType, body)));
        return send(providerType, builder, headers);
    }

    public JsonNode get(AiProviderType providerType, String url,
                        Map<String, String> headers, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .GET();
        return send(providerType, builder, headers);
    }

    private JsonNode send(AiProviderType providerType, HttpRequest.Builder builder,
                          Map<String, String> headers) {
        HttpResponse<String> response;
        try {
            headers.forEach(builder::header);
            response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (ProviderCallException exception) {
            logFailure(exception);
            throw exception;
        } catch (Exception exception) {
            ProviderCallException mapped = errorMapper.fromThrowable(providerType, exception);
            logFailure(mapped);
            throw mapped;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            ProviderCallException mapped = errorMapper.fromHttp(
                    providerType, response.statusCode(), response.body());
            Duration retryAfter = retryAfter(response.headers().firstValue("Retry-After").orElse(null));
            if (retryAfter != null) mapped = mapped.withRetryAfter(retryAfter);
            logFailure(mapped);
            throw mapped;
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (Exception exception) {
            ProviderCallException mapped = errorMapper.invalidResponse(providerType,
                    "HTTP " + response.statusCode() + " response was not valid JSON");
            logFailure(mapped);
            throw mapped;
        }
    }

    private String writeBody(AiProviderType providerType, JsonNode body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception exception) {
            ProviderCallException mapped = new ProviderCallException(providerType,
                    com.localflow.domain.provider.domain.ProviderErrorCode.REQUEST_REJECTED,
                    "AI 요청을 생성하지 못했습니다.", false,
                    "입력 내용과 요청 크기를 확인해 주세요.",
                    SensitiveDataMasker.mask(exception.getMessage()), exception);
            logFailure(mapped);
            throw mapped;
        }
    }

    private void logFailure(ProviderCallException exception) {
        log.warn("AI provider call failed: provider={}, code={}, retryable={}, diagnostic={}",
                exception.providerType(), exception.errorCode(), exception.retryable(),
                SensitiveDataMasker.mask(exception.diagnosticMessage()));
    }

    private Duration retryAfter(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value.strip())));
        } catch (NumberFormatException ignored) {
            try {
                Duration duration = Duration.between(ZonedDateTime.now(),
                        ZonedDateTime.parse(value.strip(), DateTimeFormatter.RFC_1123_DATE_TIME));
                return duration.isNegative() ? Duration.ZERO : duration;
            } catch (DateTimeParseException ignoredDate) {
                return null;
            }
        }
    }
}
