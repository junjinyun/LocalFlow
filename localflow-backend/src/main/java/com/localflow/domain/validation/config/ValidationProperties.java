package com.localflow.domain.validation.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "localflow.validation")
public record ValidationProperties(
        boolean enabled,
        Duration timeout,
        int maxOutputChars,
        int maxConcurrentProcesses
) {
    public ValidationProperties {
        timeout = timeout == null || timeout.isNegative() || timeout.isZero()
                ? Duration.ofMinutes(3) : timeout;
        maxOutputChars = maxOutputChars <= 0 ? 20_000 : maxOutputChars;
        maxConcurrentProcesses = maxConcurrentProcesses <= 0 ? 1 : maxConcurrentProcesses;
    }
}
