package com.localflow.domain.provider.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.GenerationRequest;
import com.localflow.domain.provider.domain.GenerationResult;
import com.localflow.domain.provider.domain.GeminiCliStatus;
import com.localflow.domain.provider.exception.ProviderCallException;
import com.localflow.domain.provider.port.GenerationProvider;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GeminiCliGenerationProvider implements GenerationProvider {
    private final AiProviderProperties.GeminiCli properties;
    private final GeminiCliProcessExecutor executor;
    private final GeminiCliStatusService statusService;
    private final GeminiCliExecutionCoordinator executionCoordinator;
    private final ObjectMapper objectMapper;

    public GeminiCliGenerationProvider(AiProviderProperties properties,
                                       GeminiCliProcessExecutor executor,
                                       GeminiCliStatusService statusService,
                                       GeminiCliExecutionCoordinator executionCoordinator,
                                       ObjectMapper objectMapper) {
        this.properties = properties.geminiCli();
        this.executor = executor;
        this.statusService = statusService;
        this.executionCoordinator = executionCoordinator;
        this.objectMapper = objectMapper;
    }

    @Override public AiProviderType providerType() { return AiProviderType.GEMINI_CLI; }
    @Override public boolean available() { return status(false).available(); }
    @Override public String model() { return properties == null ? null : properties.model(); }
    @Override public List<String> models() {
        return properties == null ? List.of() : properties.selectableModels();
    }
    @Override public int structuredOutputRetries() {
        return properties == null ? 0 : properties.structuredOutputRetries();
    }

    public GeminiCliStatus status(boolean refresh) {
        return statusService.status(refresh);
    }

    public GeminiCliExecutionCoordinator.ExecutionStatus executionStatus() {
        return executionCoordinator.status();
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        GeminiCliStatus status = status(false);
        if (!status.available()) throw new ProviderCallException(providerType(), status.message());
        String selectedModel = selectedModel(request.model());
        GeminiCliProcessExecutor.ProcessResult result = executionCoordinator.execute(() -> executor.execute(
                arguments(selectedModel), prompt(request), properties.requestTimeout()));
        if (result.exitCode() != 0) {
            throw new ProviderCallException(providerType(), failureMessage(result));
        }
        return parse(result.stdout(), selectedModel);
    }

    private List<String> arguments(String model) {
        List<String> arguments = new ArrayList<>(List.of(
                "--output-format", "json",
                "--approval-mode", "plan",
                "--skip-trust"));
        if (!"auto".equalsIgnoreCase(model)) {
            arguments.add("--model");
            arguments.add(model);
        }
        return arguments;
    }

    private String prompt(GenerationRequest request) {
        return """
                LocalFlow가 제공한 프로젝트 문맥만 사용하여 응답하세요.
                파일을 직접 생성·수정·삭제하거나 명령을 실행하지 마세요.
                최종 응답은 LocalFlow가 검증하고 사용자 승인 후 적용합니다.

                ## 시스템 지침
                %s

                ## 사용자 요청 및 프로젝트 문맥
                %s
                """.formatted(request.systemPrompt(), request.userPrompt());
    }

    private GenerationResult parse(String output, String fallbackModel) {
        try {
            JsonNode root = objectMapper.readTree(output);
            String response = root.path("response").asText();
            if (response.isBlank()) {
                throw new ProviderCallException(providerType(), "Gemini CLI 응답에 텍스트가 없습니다.");
            }
            JsonNode stats = root.path("stats");
            return new GenerationResult(response, detectedModel(stats, fallbackModel),
                    token(stats, "inputTokens", "promptTokens", "totalPromptTokens"),
                    token(stats, "outputTokens", "candidateTokens", "totalCandidateTokens"));
        } catch (ProviderCallException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ProviderCallException(providerType(),
                    "Gemini CLI JSON 응답을 해석하지 못했습니다.", exception);
        }
    }

    private String detectedModel(JsonNode stats, String fallback) {
        JsonNode models = stats.path("models");
        if (models.isObject() && models.fieldNames().hasNext()) return models.fieldNames().next();
        return fallback;
    }

    private Integer token(JsonNode stats, String... names) {
        for (String name : names) {
            JsonNode value = stats.get(name);
            if (value != null && value.canConvertToInt()) return value.asInt();
        }
        return null;
    }

    private String selectedModel(String requested) {
        return requested == null || requested.isBlank() ? properties.model() : requested.strip();
    }

    private String failureMessage(GeminiCliProcessExecutor.ProcessResult result) {
        String raw = result.stderr().isBlank() ? result.stdout() : result.stderr();
        String value = raw.replaceAll("\\s+", " ").strip();
        if (value.isBlank()) value = "종료 코드 " + result.exitCode();
        if (value.length() > 600) value = value.substring(0, 600) + "…";
        return "Gemini CLI 호출에 실패했습니다. 로그인, 사용량 한도와 모델 접근 권한을 확인해 주세요. (" + value + ")";
    }
}
