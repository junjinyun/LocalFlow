package com.localflow.domain.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.domain.provider.domain.GenerationRequest;
import com.localflow.domain.provider.domain.GeminiCliStatus;
import com.localflow.domain.provider.exception.ProviderCallException;
import com.localflow.domain.provider.service.GeminiCliExecutionCoordinator;
import com.localflow.domain.provider.service.GeminiCliGenerationProvider;
import com.localflow.domain.provider.service.GeminiCliProcessExecutor;
import com.localflow.domain.provider.service.GeminiCliStatusService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeminiCliGenerationProviderTest {
    @Test
    void parsesHeadlessJsonResponseWithoutExposingOauthCredentials() throws Exception {
        Fixture fixture = fixture();
        when(fixture.statusService.status(false)).thenReturn(new GeminiCliStatus(
                true, true, true, "사용 가능", "0.20.0", List.of("auto")));
        when(fixture.executor.execute(any(), any(), eq(Duration.ofMinutes(10))))
                .thenReturn(new GeminiCliProcessExecutor.ProcessResult(0, """
                        {"response":"{\\"summary\\":\\"완료\\",\\"response\\":\\"처리됨\\",\\"operations\\":[]}",
                         "stats":{"models":{"gemini-2.5-flash":{}}}}
                        """, ""));

        var result = fixture.provider.generate(new GenerationRequest(
                "JSON으로 응답", "로그인 기능을 분석", "auto"));

        assertThat(result.text()).contains("\"summary\":\"완료\"");
        assertThat(result.model()).isEqualTo("gemini-2.5-flash");
    }

    @Test
    void reportsAuthenticationOrQuotaFailureFromCli() throws Exception {
        Fixture fixture = fixture();
        when(fixture.statusService.status(false)).thenReturn(new GeminiCliStatus(
                true, true, true, "사용 가능", "0.20.0", List.of("auto")));
        when(fixture.executor.execute(any(), any(), eq(Duration.ofMinutes(10))))
                .thenReturn(new GeminiCliProcessExecutor.ProcessResult(1, "", "RESOURCE_EXHAUSTED"));

        assertThatThrownBy(() -> fixture.provider.generate(new GenerationRequest(
                "JSON으로 응답", "작업", "auto")))
                .isInstanceOf(ProviderCallException.class)
                .hasMessageContaining("로그인, 사용량 한도")
                .hasMessageContaining("RESOURCE_EXHAUSTED");
    }

    private Fixture fixture() {
        AiProviderProperties.GeminiCli cli = new AiProviderProperties.GeminiCli(
                true, "gemini", "auto", List.of("auto"),
                Duration.ofSeconds(10), Duration.ofSeconds(5),
                Duration.ofMinutes(10), Duration.ofMinutes(15), 1, 1);
        AiProviderProperties properties = new AiProviderProperties(
                32_000, 20, null, null, null, null, cli);
        GeminiCliProcessExecutor executor = mock(GeminiCliProcessExecutor.class);
        GeminiCliStatusService statusService = mock(GeminiCliStatusService.class);
        GeminiCliExecutionCoordinator coordinator = new GeminiCliExecutionCoordinator(properties);
        GeminiCliGenerationProvider provider = new GeminiCliGenerationProvider(
                properties, executor, statusService, coordinator, new ObjectMapper());
        return new Fixture(provider, executor, statusService);
    }

    private record Fixture(GeminiCliGenerationProvider provider,
                           GeminiCliProcessExecutor executor,
                           GeminiCliStatusService statusService) {
    }
}
