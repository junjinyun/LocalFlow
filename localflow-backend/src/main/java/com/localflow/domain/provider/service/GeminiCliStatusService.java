package com.localflow.domain.provider.service;

import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.domain.provider.domain.GeminiCliStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GeminiCliStatusService {
    private final AiProviderProperties.GeminiCli properties;
    private final GeminiCliProcessExecutor executor;
    private volatile CachedStatus cached;

    public GeminiCliStatusService(AiProviderProperties properties, GeminiCliProcessExecutor executor) {
        this.properties = properties.geminiCli();
        this.executor = executor;
    }

    public GeminiCliStatus status(boolean refresh) {
        if (properties == null || !properties.configured()) {
            return new GeminiCliStatus(false, false, false,
                    "GEMINI_CLI_ENABLED가 비활성화되어 있습니다.", null, models());
        }
        CachedStatus current = cached;
        if (!refresh && current != null && current.expiresAt().isAfter(Instant.now())) return current.status();
        GeminiCliStatus inspected = inspect();
        cached = new CachedStatus(inspected, Instant.now().plus(properties.statusCacheTtl()));
        return inspected;
    }

    private GeminiCliStatus inspect() {
        try {
            GeminiCliProcessExecutor.ProcessResult result = executor.execute(
                    List.of("--version"), null, properties.statusTimeout());
            if (result.exitCode() != 0) {
                return unavailable("Gemini CLI를 실행하지 못했습니다: " + output(result));
            }
            String version = result.stdout().strip();
            return new GeminiCliStatus(true, true, true,
                    "Gemini CLI가 설치되어 있습니다. 개인 Google 계정 로그인은 실제 호출 시 확인됩니다.",
                    version.isBlank() ? null : version, models());
        } catch (Exception exception) {
            return unavailable("Gemini CLI를 찾을 수 없습니다. 설치 상태와 GEMINI_CLI_COMMAND를 확인해 주세요.");
        }
    }

    private GeminiCliStatus unavailable(String message) {
        return new GeminiCliStatus(true, false, false, message, null, models());
    }

    private List<String> models() {
        return properties == null ? List.of() : properties.selectableModels();
    }

    private String output(GeminiCliProcessExecutor.ProcessResult result) {
        String value = result.stderr().isBlank() ? result.stdout() : result.stderr();
        value = value.replaceAll("\\s+", " ").strip();
        return value.length() <= 240 ? value : value.substring(0, 240) + "…";
    }

    private record CachedStatus(GeminiCliStatus status, Instant expiresAt) {
    }
}
