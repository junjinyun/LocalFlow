package com.localflow.domain.provider.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "localflow.ai.retry")
public record ProviderRetryProperties(
        int maxAttempts,
        Duration initialDelay,
        Duration maxDelay
) {
    @ConstructorBinding
    public ProviderRetryProperties {
        maxAttempts = Math.max(1, Math.min(maxAttempts, 5));
        initialDelay = positive(initialDelay, Duration.ofSeconds(1));
        maxDelay = positive(maxDelay, Duration.ofSeconds(8));
        if (maxDelay.compareTo(initialDelay) < 0) maxDelay = initialDelay;
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }
}
