package com.localflow.domain.provider.service;

import com.localflow.domain.provider.config.ProviderRetryProperties;
import com.localflow.domain.provider.exception.ProviderCallException;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class ProviderRetryExecutor {
    private static final Duration CHECKPOINT_INTERVAL = Duration.ofMillis(200);
    private final ProviderRetryProperties properties;

    public ProviderRetryExecutor(ProviderRetryProperties properties) {
        this.properties = properties;
    }

    public <T> T execute(Supplier<T> operation, Runnable checkpoint,
                         Consumer<RetryNotice> listener) {
        Objects.requireNonNull(operation);
        Runnable safeCheckpoint = checkpoint == null ? () -> { } : checkpoint;
        Consumer<RetryNotice> safeListener = listener == null ? notice -> { } : listener;
        for (int attempt = 1; ; attempt++) {
            safeCheckpoint.run();
            try {
                return operation.get();
            } catch (ProviderCallException exception) {
                if (!exception.retryable() || attempt >= properties.maxAttempts()) throw exception;
                Duration delay = retryDelay(attempt, exception.retryAfter());
                safeListener.accept(new RetryNotice(attempt, properties.maxAttempts(), delay, exception));
                sleep(delay, safeCheckpoint, exception);
            }
        }
    }

    private Duration retryDelay(int failedAttempt, Duration retryAfter) {
        Duration exponential = properties.initialDelay().multipliedBy(1L << Math.min(failedAttempt - 1, 10));
        Duration selected = retryAfter != null && !retryAfter.isNegative() && !retryAfter.isZero()
                ? retryAfter : exponential;
        return selected.compareTo(properties.maxDelay()) > 0 ? properties.maxDelay() : selected;
    }

    private void sleep(Duration delay, Runnable checkpoint, ProviderCallException original) {
        long remaining = delay.toMillis();
        while (remaining > 0) {
            checkpoint.run();
            long chunk = Math.min(remaining, CHECKPOINT_INTERVAL.toMillis());
            try {
                Thread.sleep(chunk);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw ProviderCallException.timeout(original.providerType(),
                        "AI 제공자 재시도 대기가 중단되었습니다.", exception);
            }
            remaining -= chunk;
        }
        checkpoint.run();
    }

    public record RetryNotice(
            int failedAttempt,
            int maxAttempts,
            Duration delay,
            ProviderCallException exception
    ) {
    }
}
