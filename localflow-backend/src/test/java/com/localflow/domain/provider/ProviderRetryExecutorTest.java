package com.localflow.domain.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.localflow.domain.provider.config.ProviderRetryProperties;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.ProviderErrorCode;
import com.localflow.domain.provider.exception.ProviderCallException;
import com.localflow.domain.provider.service.ProviderRetryExecutor;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProviderRetryExecutorTest {
    private final ProviderRetryExecutor executor = new ProviderRetryExecutor(
            new ProviderRetryProperties(3, Duration.ofMillis(1), Duration.ofMillis(2)));

    @Test
    void retriesTransientFailureAndReturnsSuccessfulValue() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger notices = new AtomicInteger();

        String result = executor.execute(() -> {
            if (calls.incrementAndGet() < 3) throw retryable();
            return "ok";
        }, () -> { }, notice -> notices.incrementAndGet());

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(3);
        assertThat(notices).hasValue(2);
    }

    @Test
    void doesNotRetryPermanentFailure() {
        AtomicInteger calls = new AtomicInteger();
        ProviderCallException failure = new ProviderCallException(AiProviderType.OPENAI,
                ProviderErrorCode.AUTHENTICATION_FAILED, "인증 실패", false,
                "키 확인", "HTTP 401");

        assertThatThrownBy(() -> executor.execute(() -> {
            calls.incrementAndGet();
            throw failure;
        }, () -> { }, notice -> { })).isSameAs(failure);
        assertThat(calls).hasValue(1);
    }

    @Test
    void checkpointCanCancelDuringRetryWait() {
        AtomicInteger checkpoints = new AtomicInteger();

        assertThatThrownBy(() -> executor.execute(() -> { throw retryable(); }, () -> {
            if (checkpoints.incrementAndGet() >= 2) throw new IllegalStateException("cancelled");
        }, notice -> { })).isInstanceOf(IllegalStateException.class)
                .hasMessage("cancelled");
    }

    private ProviderCallException retryable() {
        return new ProviderCallException(AiProviderType.OPENAI, ProviderErrorCode.RATE_LIMITED,
                "요청 제한", true, "대기", "HTTP 429");
    }
}
