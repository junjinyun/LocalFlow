package com.localflow.domain.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.ProviderErrorCode;
import com.localflow.domain.provider.exception.ProviderCallException;
import com.localflow.domain.provider.service.ProviderErrorMapper;
import com.localflow.domain.provider.service.SensitiveDataMasker;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.Test;

class ProviderErrorMapperTest {
    private final ProviderErrorMapper mapper = new ProviderErrorMapper();

    @Test
    void mapsAuthenticationPermissionQuotaRateLimitModelAndServiceErrors() {
        assertError(mapper.fromHttp(AiProviderType.OPENAI, 401,
                        "{\"error\":\"invalid sk-secret-value\"}"),
                ProviderErrorCode.AUTHENTICATION_FAILED, false);
        assertError(mapper.fromHttp(AiProviderType.VERTEX_AI, 403, "permission denied"),
                ProviderErrorCode.PERMISSION_DENIED, false);
        assertError(mapper.fromHttp(AiProviderType.JEV, 429, "quota exhausted"),
                ProviderErrorCode.QUOTA_EXCEEDED, false);
        assertError(mapper.fromHttp(AiProviderType.OPENAI, 429, "too many requests"),
                ProviderErrorCode.RATE_LIMITED, true);
        assertError(mapper.fromHttp(AiProviderType.OLLAMA, 404, "model not found"),
                ProviderErrorCode.MODEL_NOT_SUPPORTED, false);
        assertError(mapper.fromHttp(AiProviderType.JEV, 503, "unavailable"),
                ProviderErrorCode.SERVICE_UNAVAILABLE, true);
    }

    @Test
    void mapsTimeoutAndInvalidCliOutputWithoutExposingRawDetails() {
        ProviderCallException timeout = mapper.fromThrowable(AiProviderType.OLLAMA,
                new HttpTimeoutException("Bearer secret-token timed out"));
        assertError(timeout, ProviderErrorCode.TIMEOUT, true);
        assertThat(timeout.userMessage()).doesNotContain("secret-token");

        ProviderCallException cli = mapper.fromCli(AiProviderType.GEMINI_CLI, 1,
                "RESOURCE_EXHAUSTED api_key=secret-value");
        assertError(cli, ProviderErrorCode.QUOTA_EXCEEDED, false);
        assertThat(cli.userMessage()).doesNotContain("RESOURCE_EXHAUSTED", "secret-value");
        assertThat(cli.diagnosticMessage()).isEqualTo("CLI exited with code 1");
    }

    @Test
    void masksCredentialsBeforeTheyCanBeWrittenToDiagnostics() {
        String raw = "Authorization: Bearer abc.def.ghi api_key=secret-value "
                + "sk-test-secret-value -----BEGIN PRIVATE KEY-----hidden-----END PRIVATE KEY-----";

        String masked = SensitiveDataMasker.mask(raw);

        assertThat(masked).contains("Bearer [MASKED]", "[MASKED_API_KEY]", "[MASKED_PRIVATE_KEY]");
        assertThat(masked).doesNotContain("abc.def.ghi", "secret-value", "hidden");
    }

    private void assertError(ProviderCallException exception, ProviderErrorCode code,
                             boolean retryable) {
        assertThat(exception.errorCode()).isEqualTo(code);
        assertThat(exception.retryable()).isEqualTo(retryable);
        assertThat(exception.userMessage()).isNotBlank().doesNotContain("null");
        assertThat(exception.recommendedAction()).isNotBlank();
    }
}
